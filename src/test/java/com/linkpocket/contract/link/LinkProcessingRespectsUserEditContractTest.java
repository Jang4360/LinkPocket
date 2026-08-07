package com.linkpocket.contract.link;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약: plan/05-categories.md 불변식 "*_source가 USER_EDITED인 필드는 AI 프로세스가
 * 절대 쓰지 않는다" + ADR-015 결정 2(원자적 조건부 UPDATE, 호출 전 확인으로 비용도 방지).
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - LinkProcessingWorkerImpl의 요약 단계가 SummaryGenerator를 호출하기 **전에**
 *    summary_source가 이미 USER_EDITED인지 확인하고, 그렇다면 호출 자체를 건너뛰고
 *    상태만 SUMMARIZED로 전이시킨다(사용자가 이미 값을 채워뒀으므로 완료로 본다).
 *  - 쓰기 자체도 `UPDATE ... WHERE summary_source = 'AI_GENERATED'` 조건으로 이중 방어.
 */
class LinkProcessingRespectsUserEditContractTest extends AbstractLinkProcessingContractTest {

    @Test
    void reclaimed_link_with_user_edited_summary_is_not_overwritten_and_generator_not_called() {
        UUID linkId = seedLinkAtStatus("FETCHED", "AI가 원래 요약했을 본문");

        // 정상 진행되어 요약이 한 번 생성됐다고 가정한 뒤, 사용자가 그 결과를 직접 고쳤다.
        pollUntil(linkId, 5, status -> "SUMMARIZED".equals(status));
        assertThat(fakeSummaryGenerator.callCount.get()).isEqualTo(1);
        jdbcTemplate.update(
                "update link set ai_summary = ?, summary_source = 'USER_EDITED' where id = ?",
                "사용자가 직접 고친 요약", linkId);

        // lease 오판으로 다른 worker가 이미 끝난(그리고 사용자가 고친) job을 재claim했다고 가정.
        jdbcTemplate.update(
                "update link set status = 'SUMMARIZING', processing_lease_expires_at = ? where id = ?",
                java.sql.Timestamp.from(expiredLease()), linkId);

        pollUntil(linkId, 5, status -> "SUMMARIZED".equals(status));

        assertThat(fakeSummaryGenerator.callCount.get()).isEqualTo(1);
        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("SUMMARIZED");
        assertThat(link.get("ai_summary")).isEqualTo("사용자가 직접 고친 요약");
        assertThat(link.get("summary_source")).isEqualTo("USER_EDITED");
    }
}
