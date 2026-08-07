package com.linkpocket.contract.link;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약: plan/04-async-ai-pipeline.md AC "재시도 불가 사유는 1회 시도로 즉시 영구 실패 확정,
 * 재시도 가능 사유는 최대 2회까지 재시도 후 실패 확정" + ADR-014.
 * 그리고 "요약·임베딩 단계에서 실패해도 Link row·URL·제목·추출된 본문은 삭제되지 않고,
 * 목록에서 정상 노출된다".
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - 재시도 불가(retryable=false) 예외는 즉시 status='READY_WITHOUT_INDEX', failure_reason 기록.
 *  - 재시도 가능(retryable=true) 예외는 processing_attempt_count를 증가시키고 lease를 새로
 *    설정해 재시도, attempt_count가 2를 넘으면 READY_WITHOUT_INDEX로 확정.
 *  - 두 경우 모두 url·extracted_title·extracted_body는 그대로 남는다.
 */
class LinkProcessingRetryPolicyContractTest extends AbstractLinkProcessingContractTest {

    @Test
    void non_retryable_failure_gives_up_immediately_and_preserves_content() {
        UUID linkId = seedLinkAtStatus("FETCHED", "정책 위반으로 거부될 예정인 본문");
        fakeSummaryGenerator.queueNonRetryableFailure("SUMMARIZE_CONTENT_REJECTED");

        pollUntil(linkId, 5, status -> "READY_WITHOUT_INDEX".equals(status));

        assertThat(fakeSummaryGenerator.callCount.get()).isEqualTo(1);
        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("READY_WITHOUT_INDEX");
        assertThat(link.get("failure_reason")).isEqualTo("SUMMARIZE_CONTENT_REJECTED");
        assertThat(link.get("extracted_title")).isNotNull();
        assertThat(link.get("extracted_body")).isNotNull();
        assertThat(link.get("url")).isNotNull();
    }

    @Test
    void retryable_failure_gives_up_after_two_attempts() {
        UUID linkId = seedLinkAtStatus("FETCHED", "일시 장애를 흉내낼 본문");
        fakeSummaryGenerator.queueRetryableFailure("SUMMARIZE_API_ERROR");
        fakeSummaryGenerator.queueRetryableFailure("SUMMARIZE_API_ERROR");
        fakeSummaryGenerator.queueRetryableFailure("SUMMARIZE_API_ERROR");

        // 재시도가 lease 만료를 거쳐야 다시 claim되므로, 매 시도 사이에 lease를 강제로 만료시킨다.
        for (int i = 0; i < 6; i++) {
            String status = String.valueOf(loadLink(linkId).get("status"));
            if ("READY_WITHOUT_INDEX".equals(status)) {
                break;
            }
            linkProcessingWorker.pollAndProcessOnce();
            jdbcTemplate.update(
                    "update link set processing_lease_expires_at = ? where id = ? and status = 'SUMMARIZING'",
                    java.sql.Timestamp.from(expiredLease()), linkId);
        }

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("READY_WITHOUT_INDEX");
        assertThat(fakeSummaryGenerator.callCount.get()).isEqualTo(2);
        assertThat(link.get("extracted_body")).isNotNull();
    }

    @Test
    void retryable_failure_then_success_within_budget_completes_normally() {
        UUID linkId = seedLinkAtStatus("FETCHED", "한 번 실패했다가 복구되는 본문");
        fakeSummaryGenerator.queueRetryableFailure("SUMMARIZE_API_ERROR");
        fakeSummaryGenerator.queueSuccess("복구된 요약");

        for (int i = 0; i < 6; i++) {
            String status = String.valueOf(loadLink(linkId).get("status"));
            if ("SUMMARIZED".equals(status)) {
                break;
            }
            linkProcessingWorker.pollAndProcessOnce();
            jdbcTemplate.update(
                    "update link set processing_lease_expires_at = ? where id = ? and status = 'SUMMARIZING'",
                    java.sql.Timestamp.from(expiredLease()), linkId);
        }

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("SUMMARIZED");
        assertThat(fakeSummaryGenerator.callCount.get()).isEqualTo(2);
    }
}
