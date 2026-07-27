package com.linkpocket.contract.link;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약: plan/04-async-ai-pipeline.md AC "같은 Link에 대해 요약/임베딩이 두 번 실행돼도(재claim으로
 * 인한 재시도 포함), 이미 결과가 있으면 LLM을 다시 호출하지 않고 기존 결과를 재사용한다" +
 * ADR-012 결정 3(호출 전 존재 조회).
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - SummaryGenerator를 부르기 전에 `(link_id, input_hash, model_version)`로 기존 결과가
 *    있는지 먼저 조회한다. 있으면 호출을 건너뛰고 그 결과를 그대로 사용해 상태만 전이시킨다.
 */
class LinkProcessingIdempotencyContractTest extends AbstractLinkProcessingContractTest {

    @Test
    void reclaim_after_completed_summary_does_not_call_generator_again() {
        UUID linkId = seedLinkAtStatus("FETCHED", "멱등성 테스트용 충분히 긴 본문 내용입니다.");

        pollUntil(linkId, 5, status -> "SUMMARIZED".equals(status));
        assertThat(loadLink(linkId).get("status")).isEqualTo("SUMMARIZED");
        assertThat(fakeSummaryGenerator.callCount.get()).isEqualTo(1);

        // lease 오판으로 다른 worker가 이미 끝난 job을 다시 SUMMARIZING으로 되돌렸다고 가정
        // (예: 매우 드문 race). 이 상태에서 재claim이 일어나도 결과가 이미 있으므로 재호출 없이
        // 바로 완료 처리돼야 한다.
        jdbcTemplate.update(
                "update link set status = 'SUMMARIZING', processing_lease_expires_at = ? where id = ?",
                java.sql.Timestamp.from(expiredLease()), linkId);

        pollUntil(linkId, 5, status -> "SUMMARIZED".equals(status));

        assertThat(fakeSummaryGenerator.callCount.get()).isEqualTo(1);
        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("SUMMARIZED");
    }
}
