package com.linkpocket.link;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LinkProcessingWorkerImpl implements LinkProcessingWorker {

    private static final int CLAIM_BATCH_SIZE = 20;
    private static final int MAX_PROCESSING_ATTEMPTS = 2;
    private static final long LEASE_SECONDS = 30;
    private static final String SUMMARY_MODEL_VERSION = OpenAiSummaryGenerator.MODEL_VERSION;
    private static final String EMBEDDING_MODEL_VERSION = OpenAiEmbeddingGenerator.MODEL_VERSION;
    private static final String CHUNK_MODEL_VERSION = "structure-v1";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final LinkFetchService linkFetchService;
    private final SummaryGenerator summaryGenerator;
    private final EmbeddingGenerator embeddingGenerator;
    private final ContentChunker contentChunker = new ContentChunker();

    public LinkProcessingWorkerImpl(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            LinkFetchService linkFetchService,
            SummaryGenerator summaryGenerator,
            EmbeddingGenerator embeddingGenerator
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.linkFetchService = linkFetchService;
        this.summaryGenerator = summaryGenerator;
        this.embeddingGenerator = embeddingGenerator;
    }

    @Override
    @Scheduled(
            fixedDelayString = "${linkpocket.processing.poll-delay-ms:10000}",
            initialDelayString = "${linkpocket.processing.initial-delay-ms:60000}"
    )
    public void pollAndProcessOnce() {
        for (ClaimedLink claim : claimBatch()) {
            processOutsideTransaction(claim);
        }
    }

    private List<ClaimedLink> claimBatch() {
        List<ClaimedLink> claims = transactionTemplate.execute(status -> {
            List<ClaimedLink> candidates = jdbcTemplate.query(
                    """
                            select id, status
                            from link
                            where status in ('PENDING', 'FETCHED', 'SUMMARIZED', 'CHUNKED')
                               or (status in ('FETCHING', 'SUMMARIZING', 'CHUNKING', 'EMBEDDING')
                                   and processing_lease_expires_at < now())
                            order by created_at
                            for update skip locked
                            limit ?
                            """,
                    (resultSet, rowNum) -> new ClaimedLink(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getString("status")
                    ),
                    CLAIM_BATCH_SIZE
            );
            List<ClaimedLink> claimed = new ArrayList<>();
            for (ClaimedLink candidate : candidates) {
                String processingStatus = nextProcessingStatus(candidate.status());
                boolean aiStage = isAiProcessingStage(processingStatus);
                jdbcTemplate.update(
                        """
                                update link
                                set status = ?,
                                    processing_lease_expires_at = now() + interval '30 seconds',
                                    processing_attempt_count = processing_attempt_count + ?
                                where id = ?
                                """,
                        processingStatus, aiStage ? 1 : 0, candidate.linkId()
                );
                claimed.add(new ClaimedLink(candidate.linkId(), processingStatus));
            }
            return claimed;
        });
        return claims == null ? List.of() : claims;
    }

    private void processOutsideTransaction(ClaimedLink claim) {
        switch (claim.status()) {
            case "FETCHING" -> linkFetchService.fetchAndExtract(claim.linkId());
            case "SUMMARIZING" -> summarize(claim);
            case "CHUNKING" -> chunk(claim);
            case "EMBEDDING" -> embed(claim);
            default -> throw new IllegalStateException("지원하지 않는 processing 상태: " + claim.status());
        }
    }

    private void summarize(ClaimedLink claim) {
        try {
            String body = extractedBody(claim.linkId());
            String inputHash = hash(body);
            String summary = existingSummary(claim.linkId(), inputHash);
            if (summary == null) {
                // 외부 LLM 호출은 claim transaction이 끝난 뒤에만 실행한다.
                summary = summaryGenerator.summarize(body);
                saveSummary(claim.linkId(), inputHash, summary);
            }
            jdbcTemplate.update(
                    """
                            update link set status = 'SUMMARIZED', processing_lease_expires_at = null,
                                failure_reason = null, ai_summary = ?
                            where id = ?
                            """,
                    summary, claim.linkId()
            );
        } catch (AiProcessingException exception) {
            handleAiFailure(claim, exception);
        } catch (RuntimeException exception) {
            handleAiFailure(claim, new AiProcessingException("CHUNKING_FAILED", false));
        }
    }

    private void chunk(ClaimedLink claim) {
        try {
            String body = extractedBody(claim.linkId());
            String inputHash = hash(body);
            List<String> chunks = contentChunker.split(body);
            for (int index = 0; index < chunks.size(); index++) {
                jdbcTemplate.update(
                        """
                                insert into link_chunk (id, link_id, input_hash, model_version, chunk_index, content, created_at)
                                values (?, ?, ?, ?, ?, ?, now())
                                on conflict (link_id, input_hash, model_version, chunk_index) do nothing
                                """,
                        UUID.randomUUID(), claim.linkId(), inputHash, CHUNK_MODEL_VERSION, index, chunks.get(index)
                );
            }
            jdbcTemplate.update(
                    "update link set status = 'CHUNKED', processing_lease_expires_at = null, failure_reason = null where id = ?",
                    claim.linkId()
            );
        } catch (RuntimeException exception) {
            handleAiFailure(claim, new AiProcessingException("CHUNKING_FAILED", false));
        }
    }

    private void embed(ClaimedLink claim) {
        try {
            List<Chunk> chunks = jdbcTemplate.query(
                    "select input_hash, chunk_index, content from link_chunk where link_id = ? order by chunk_index",
                    (resultSet, rowNum) -> new Chunk(
                            resultSet.getString("input_hash"),
                            resultSet.getInt("chunk_index"),
                            resultSet.getString("content")
                    ),
                    claim.linkId()
            );
            if (chunks.isEmpty()) {
                throw new AiProcessingException("CHUNKING_FAILED", false);
            }
            for (Chunk chunk : chunks) {
                if (!embeddingExists(claim.linkId(), chunk.inputHash(), chunk.index())) {
                    // 외부 임베딩 호출도 claim transaction 밖에서만 실행한다.
                    saveEmbedding(claim.linkId(), chunk, embeddingGenerator.embed(chunk.content()));
                }
            }
            jdbcTemplate.update(
                    "update link set status = 'INDEXED', processing_lease_expires_at = null, failure_reason = null where id = ?",
                    claim.linkId()
            );
        } catch (AiProcessingException exception) {
            handleAiFailure(claim, exception);
        } catch (RuntimeException exception) {
            handleAiFailure(claim, new AiProcessingException("INDEX_WRITE_FAILED", true));
        }
    }

    private void handleAiFailure(ClaimedLink claim, AiProcessingException exception) {
        Integer attempts = jdbcTemplate.queryForObject(
                "select processing_attempt_count from link where id = ?", Integer.class, claim.linkId());
        if (!exception.retryable() || attempts == null || attempts >= MAX_PROCESSING_ATTEMPTS) {
            jdbcTemplate.update(
                    """
                            update link set status = 'READY_WITHOUT_INDEX', processing_lease_expires_at = null,
                                failure_reason = ?, ai_summary = coalesce(ai_summary, '요약 생성 실패')
                            where id = ?
                            """,
                    exception.reason(), claim.linkId()
            );
            return;
        }
        jdbcTemplate.update(
                "update link set failure_reason = ? where id = ?",
                exception.reason(), claim.linkId()
        );
    }

    private String extractedBody(UUID linkId) {
        String body = jdbcTemplate.queryForObject(
                "select extracted_body from link where id = ?", String.class, linkId);
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("추출 본문이 없습니다.");
        }
        return body;
    }

    private String existingSummary(UUID linkId, String inputHash) {
        List<String> summaries = jdbcTemplate.query(
                "select summary from link_summary where link_id = ? and input_hash = ? and model_version = ?",
                (resultSet, rowNum) -> resultSet.getString("summary"),
                linkId, inputHash, SUMMARY_MODEL_VERSION
        );
        return summaries.isEmpty() ? null : summaries.getFirst();
    }

    private void saveSummary(UUID linkId, String inputHash, String summary) {
        jdbcTemplate.update(
                """
                        insert into link_summary (id, link_id, input_hash, model_version, summary, created_at)
                        values (?, ?, ?, ?, ?, now())
                        on conflict (link_id, input_hash, model_version) do nothing
                        """,
                UUID.randomUUID(), linkId, inputHash, SUMMARY_MODEL_VERSION, summary
        );
    }

    private boolean embeddingExists(UUID linkId, String inputHash, int chunkIndex) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                        select exists(select 1 from link_embedding
                        where link_id = ? and input_hash = ? and model_version = ? and chunk_index = ?)
                        """,
                Boolean.class, linkId, inputHash, EMBEDDING_MODEL_VERSION, chunkIndex
        );
        return Boolean.TRUE.equals(exists);
    }

    private void saveEmbedding(UUID linkId, Chunk chunk, float[] embedding) {
        if (pgVectorAvailable()) {
            jdbcTemplate.update(
                    """
                            insert into link_embedding (id, link_id, input_hash, model_version, chunk_index, embedding, created_at)
                            values (?, ?, ?, ?, ?, ?::vector, now())
                            on conflict (link_id, input_hash, model_version, chunk_index) do nothing
                            """,
                    UUID.randomUUID(), linkId, chunk.inputHash(), EMBEDDING_MODEL_VERSION, chunk.index(), vectorLiteral(embedding)
            );
            return;
        }
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            insert into link_embedding (id, link_id, input_hash, model_version, chunk_index, embedding, created_at)
                            values (?, ?, ?, ?, ?, ?, now())
                            on conflict (link_id, input_hash, model_version, chunk_index) do nothing
                            """
            );
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, linkId);
            statement.setString(3, chunk.inputHash());
            statement.setString(4, EMBEDDING_MODEL_VERSION);
            statement.setInt(5, chunk.index());
            Array values = connection.createArrayOf("real", boxed(embedding));
            statement.setArray(6, values);
            return statement;
        });
    }

    private boolean pgVectorAvailable() {
        Boolean available = jdbcTemplate.queryForObject(
                "select exists(select 1 from pg_extension where extname = 'vector')", Boolean.class);
        return Boolean.TRUE.equals(available);
    }

    private Object[] boxed(float[] embedding) {
        Object[] values = new Object[embedding.length];
        for (int index = 0; index < embedding.length; index++) {
            values[index] = embedding[index];
        }
        return values;
    }

    private String vectorLiteral(float[] embedding) {
        StringBuilder value = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append(embedding[index]);
        }
        return value.append(']').toString();
    }

    private String nextProcessingStatus(String status) {
        return switch (status) {
            case "PENDING", "FETCHING" -> "FETCHING";
            case "FETCHED", "SUMMARIZING" -> "SUMMARIZING";
            case "SUMMARIZED", "CHUNKING" -> "CHUNKING";
            case "CHUNKED", "EMBEDDING" -> "EMBEDDING";
            default -> throw new IllegalStateException("claim 대상이 아닌 상태: " + status);
        };
    }

    private boolean isAiProcessingStage(String status) {
        return "SUMMARIZING".equals(status) || "CHUNKING".equals(status) || "EMBEDDING".equals(status);
    }

    private String hash(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private record ClaimedLink(UUID linkId, String status) {
    }

    private record Chunk(String inputHash, int index, String content) {
    }
}
