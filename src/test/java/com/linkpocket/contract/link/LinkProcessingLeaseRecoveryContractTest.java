package com.linkpocket.contract.link;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약: plan/04-async-ai-pipeline.md AC "claim 이후 worker가 죽거나 AI API가 무응답이어도,
 * lease_expires_at 초과 시 다른 worker가 자동으로 재claim해 처리를 이어간다" + ADR-012 결정 2.
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - claim 대상 조회 시 `status='PENDING'`류뿐 아니라 `status='SUMMARIZING' 등 *ING AND
 *    processing_lease_expires_at < now()`도 함께 claim 후보로 삼는다.
 */
class LinkProcessingLeaseRecoveryContractTest extends AbstractLinkProcessingContractTest {

    @Test
    void expired_lease_on_stuck_processing_link_is_reclaimed_and_completed() {
        UUID linkId = seedLinkClaimed("SUMMARIZING", "본문이 있는 링크", expiredLease(), 0);

        pollUntil(linkId, 5, status -> "SUMMARIZED".equals(status) || "READY_WITHOUT_INDEX".equals(status));

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("SUMMARIZED");
        assertThat(fakeSummaryGenerator.callCount.get()).isEqualTo(1);
    }

    @Test
    void active_lease_on_processing_link_is_not_reclaimed() {
        // lease가 아직 유효(미래)하면 다른 worker가 이 Link를 건드리면 안 된다 —
        // 실제 처리 중인 worker와 경쟁하지 않는다는 배타성의 다른 얼굴.
        UUID linkId = seedLinkClaimed("SUMMARIZING", "본문이 있는 링크",
                java.time.Instant.now().plusSeconds(300), 0);

        linkProcessingWorker.pollAndProcessOnce();

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("SUMMARIZING");
        assertThat(fakeSummaryGenerator.callCount.get()).isEqualTo(0);
    }
}
