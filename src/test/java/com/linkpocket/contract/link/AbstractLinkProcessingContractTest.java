package com.linkpocket.contract.link;

import com.linkpocket.link.AiProcessingException;
import com.linkpocket.link.EmbeddingGenerator;
import com.linkpocket.link.LinkProcessingWorker;
import com.linkpocket.link.SummaryGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * plan-04(async-ai-pipeline) 계약 테스트들의 공통 기반.
 * 이 파일도 contract 패키지 소속이며 사람/Claude만 수정한다(docs/development-loop.md).
 *
 * === 왜 실제 OpenAI/WireMock이 아니라 Fake 빈을 쓰는가 ===
 * 이 plan의 위험 로직(ADR-012·013·014)은 "LLM을 어떻게 호출하는가"가 아니라 "job claim·
 * 트랜잭션 경계·재시도·멱등성을 어떻게 관리하는가"다. 실제 OpenAI 연동은 이 계약의 범위 밖이라
 * SummaryGenerator/EmbeddingGenerator를 순수 seam으로 두고, 계약 테스트는 호출 횟수·성공/실패를
 * 결정적으로 제어할 수 있는 Fake로 대체한다. green으로 만들려면 Codex는 이 두 인터페이스를 구현한
 * 실제 OpenAI 연동 빈을 만들되, **테스트 컨텍스트에서는 아래 Fake가 @Primary로 대체**하므로
 * 실제 네트워크 호출은 계약 테스트 중 발생하지 않는다.
 *
 * green으로 만들려면 Codex가 지원해야 하는 것(스키마):
 *  - `link.processing_lease_expires_at timestamptz`, `link.processing_attempt_count int default 0`
 *  - 요약/임베딩 결과를 `(link_id, input_hash, model_version)` unique로 저장하는 테이블(들) —
 *    이름은 Codex 재량, 이 계약은 `link.status`·`link.extracted_title`·`link.extracted_body`·
 *    `link.failure_reason`과 위 두 컬럼만으로 검증한다.
 */
// 주의: @TestConfiguration 중첩 클래스의 자동 인식은 "실제 실행되는 서브클래스"의
// declaredClasses만 보고, 이 상속 원본(추상 클래스)의 중첩 클래스는 보지 않는다 —
// 그래서 명시적으로 @Import를 걸어야 한다(자동 인식에 의존하면 서브클래스에서 Fake 빈이
// 등록되지 않아 NoSuchBeanDefinitionException이 난다, 실제로 겪은 문제).
@SpringBootTest
@Import(AbstractLinkProcessingContractTest.FakeGeneratorsConfig.class)
abstract class AbstractLinkProcessingContractTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class FakeGeneratorsConfig {
        @Bean
        @Primary
        FakeSummaryGenerator fakeSummaryGenerator() {
            return new FakeSummaryGenerator();
        }

        @Bean
        @Primary
        FakeEmbeddingGenerator fakeEmbeddingGenerator() {
            return new FakeEmbeddingGenerator();
        }
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected LinkProcessingWorker linkProcessingWorker;

    @Autowired
    protected FakeSummaryGenerator fakeSummaryGenerator;

    @Autowired
    protected FakeEmbeddingGenerator fakeEmbeddingGenerator;

    @BeforeEach
    void resetFakes() {
        fakeSummaryGenerator.reset();
        fakeEmbeddingGenerator.reset();
    }

    /** app_user + 지정된 상태의 link를 직접 seed하고 linkId를 반환한다. */
    protected UUID seedLinkAtStatus(String status, String extractedBody) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into app_user (id, google_sub, email, name, created_at) values (?, ?, ?, ?, now())",
                userId, "proc-sub-" + userId, "proc-user-" + userId + "@example.com", "Processing User");

        UUID linkId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into link (id, user_id, url, canonical_url, status, created_at, extracted_title, extracted_body) " +
                        "values (?, ?, ?, ?, ?, now(), ?, ?)",
                linkId, userId, "https://example.com/" + linkId, "https://example.com/" + linkId,
                status, "제목", extractedBody);
        return linkId;
    }

    /** claim 상태(*ING)로 seed하면서 lease 만료 시각을 직접 지정한다(과거면 즉시 재claim 대상). */
    protected UUID seedLinkClaimed(String claimStatus, String extractedBody, Instant leaseExpiresAt, int attemptCount) {
        UUID linkId = seedLinkAtStatus(claimStatus, extractedBody);
        jdbcTemplate.update(
                "update link set processing_lease_expires_at = ?, processing_attempt_count = ? where id = ?",
                java.sql.Timestamp.from(leaseExpiresAt), attemptCount, linkId);
        return linkId;
    }

    protected Instant expiredLease() {
        return Instant.now().minus(1, ChronoUnit.HOURS);
    }

    protected Map<String, Object> loadLink(UUID linkId) {
        return jdbcTemplate.queryForMap("select * from link where id = ?", linkId);
    }

    /** 지정한 단계까지 도달할 때까지(또는 최대 회수까지) worker를 반복 호출한다 — 결정적 진행. */
    protected void pollUntil(UUID linkId, int maxPolls, java.util.function.Predicate<String> reachedTarget) {
        for (int i = 0; i < maxPolls; i++) {
            String status = String.valueOf(loadLink(linkId).get("status"));
            if (reachedTarget.test(status)) {
                return;
            }
            linkProcessingWorker.pollAndProcessOnce();
        }
    }

    /** 테스트가 호출 횟수·성공/실패를 결정적으로 제어하는 SummaryGenerator fake. */
    protected static class FakeSummaryGenerator implements SummaryGenerator {
        final AtomicInteger callCount = new AtomicInteger();
        private final Deque<Supplier<String>> behaviors = new ArrayDeque<>();
        private volatile long delayMillis = 0;

        @Override
        public String summarize(String content) throws AiProcessingException {
            callCount.incrementAndGet();
            sleep();
            Supplier<String> behavior;
            synchronized (behaviors) {
                behavior = behaviors.isEmpty() ? null : behaviors.pollFirst();
            }
            if (behavior == null) {
                return "요약: " + content.hashCode();
            }
            return behavior.get();
        }

        /** 다음 호출에서 재시도 불가 사유로 실패하게 큐잉한다. */
        void queueNonRetryableFailure(String reason) {
            queue(() -> {
                throw sneaky(new AiProcessingException(reason, false));
            });
        }

        /** 다음 호출에서 재시도 가능 사유로 실패하게 큐잉한다. */
        void queueRetryableFailure(String reason) {
            queue(() -> {
                throw sneaky(new AiProcessingException(reason, true));
            });
        }

        void queueSuccess(String result) {
            queue(() -> result);
        }

        private void queue(Supplier<String> behavior) {
            synchronized (behaviors) {
                behaviors.addLast(behavior);
            }
        }

        void withDelay(long millis) {
            this.delayMillis = millis;
        }

        void reset() {
            callCount.set(0);
            synchronized (behaviors) {
                behaviors.clear();
            }
            delayMillis = 0;
        }

        private void sleep() {
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @SuppressWarnings("unchecked")
        private static <T extends Throwable> RuntimeException sneaky(Throwable t) throws T {
            throw (T) t;
        }
    }

    /** 임베딩용 Fake — 호출 횟수만 계약 검증에 쓰인다. */
    protected static class FakeEmbeddingGenerator implements EmbeddingGenerator {
        final AtomicInteger callCount = new AtomicInteger();

        @Override
        public float[] embed(String chunkText) {
            callCount.incrementAndGet();
            return new float[]{0.1f, 0.2f, 0.3f};
        }

        void reset() {
            callCount.set(0);
        }
    }
}
