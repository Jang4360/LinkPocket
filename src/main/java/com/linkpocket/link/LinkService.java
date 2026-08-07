package com.linkpocket.link;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

@Service
public class LinkService {

    private final JdbcTemplate jdbcTemplate;
    private final LinkCategoryService linkCategoryService;
    private final CanonicalUrlNormalizer canonicalUrlNormalizer = new CanonicalUrlNormalizer();

    public LinkService(JdbcTemplate jdbcTemplate, LinkCategoryService linkCategoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.linkCategoryService = linkCategoryService;
    }

    /**
     * PostgreSQL의 unique constraint와 ON CONFLICT가 같은 URL의 동시 저장을 한 row로 수렴시킨다.
     * 충돌 시에도 no-op UPDATE와 RETURNING으로 신규/기존을 같은 경로로 반환한다.
     * xmax는 INSERT된 row에서만 0이므로 별도 조회 없이 신규 여부도 함께 판정한다.
     */
    @Transactional
    public LinkSaveResponse save(UUID userId, String rawUrl) {
        String canonicalUrl = canonicalUrlNormalizer.normalize(rawUrl);
        UUID uncategorizedId = linkCategoryService.ensureUncategorized(userId);
        LinkSaveResponse response = jdbcTemplate.queryForObject(
                """
                        insert into link (id, user_id, url, canonical_url, status, created_at)
                        values (gen_random_uuid(), ?, ?, ?, 'PENDING', now())
                        on conflict (user_id, canonical_url)
                        do update set url = link.url
                        returning id, canonical_url, status, (xmax = 0) as inserted
                        """,
                (resultSet, rowNum) -> new LinkSaveResponse(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("canonical_url"),
                        resultSet.getString("status"),
                        !resultSet.getBoolean("inserted")
                ),
                userId, rawUrl, canonicalUrl
        );
        linkCategoryService.ensureDefaultAssignment(response.linkId(), uncategorizedId);
        return response;
    }

    @Transactional(readOnly = true)
    public LinkLookupResponse lookup(UUID userId, String rawUrl) {
        String canonicalUrl = canonicalUrlNormalizer.normalize(rawUrl);
        Optional<UUID> linkId = jdbcTemplate.query(
                        "select id from link where user_id = ? and canonical_url = ?",
                        (resultSet, rowNum) -> resultSet.getObject("id", UUID.class),
                        userId, canonicalUrl
                )
                .stream()
                .findFirst();
        return new LinkLookupResponse(linkId.isPresent(), linkId.orElse(null));
    }

    @Transactional
    public void delete(UUID userId, UUID linkId) {
        int deleted = jdbcTemplate.update(
                "delete from link where id = ? and user_id = ?",
                linkId, userId
        );
        if (deleted == 0) {
            throw new com.linkpocket.common.error.DomainException(LinkErrorCode.LINK_NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public LinkStatusResponse status(UUID userId, UUID linkId) {
        return jdbcTemplate.query(
                        "select status, created_at from link where id = ? and user_id = ?",
                        (resultSet, rowNum) -> new LinkStatusResponse(
                                linkId,
                                externalStatus(resultSet.getString("status")),
                                resultSet.getObject("created_at", java.time.OffsetDateTime.class).toInstant()
                        ),
                        linkId, userId
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new com.linkpocket.common.error.DomainException(LinkErrorCode.LINK_NOT_FOUND));
    }

    @Transactional
    public LinkEditResponse edit(UUID userId, UUID linkId, LinkEditRequest request) {
        if (request.title() == null && request.summary() == null) {
            return loadEditResponse(userId, linkId);
        }

        StringBuilder query = new StringBuilder("update link set ");
        java.util.List<Object> arguments = new java.util.ArrayList<>();
        if (request.title() != null) {
            query.append("extracted_title = ?, title_source = 'USER_EDITED'");
            arguments.add(request.title());
        }
        if (request.summary() != null) {
            if (!arguments.isEmpty()) {
                query.append(", ");
            }
            query.append("ai_summary = ?, summary_source = 'USER_EDITED'");
            arguments.add(request.summary());
        }
        query.append(" where id = ? and user_id = ?");
        arguments.add(linkId);
        arguments.add(userId);

        int updated = jdbcTemplate.update(query.toString(), arguments.toArray());
        if (updated == 0) {
            throw new com.linkpocket.common.error.DomainException(LinkErrorCode.LINK_NOT_FOUND);
        }
        return loadEditResponse(userId, linkId);
    }

    private LinkEditResponse loadEditResponse(UUID userId, UUID linkId) {
        return jdbcTemplate.query(
                        "select extracted_title, ai_summary from link where id = ? and user_id = ?",
                        (resultSet, rowNum) -> new LinkEditResponse(
                                linkId,
                                resultSet.getString("extracted_title"),
                                resultSet.getString("ai_summary")
                        ),
                        linkId, userId
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new com.linkpocket.common.error.DomainException(LinkErrorCode.LINK_NOT_FOUND));
    }

    private String externalStatus(String status) {
        return switch (status) {
            case "PENDING", "FETCHING" -> "QUEUED";
            case "SUMMARIZING", "CHUNKING", "EMBEDDING" -> "PROCESSING";
            case "INDEXED" -> "READY";
            case "READY_WITHOUT_CONTENT", "READY_WITHOUT_INDEX" -> "READY_WITHOUT_CONTENT";
            case "FAILED" -> "FAILED";
            default -> "PROCESSING";
        };
    }
}
