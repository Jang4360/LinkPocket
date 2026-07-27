package com.linkpocket.link;

/**
 * plan-04 계약의 일부 — 요약 생성 경계(OpenAI 등 실제 LLM 호출)를 나타내는 seam.
 * 계약 테스트는 이 인터페이스를 테스트 전용 fake로 대체해, "job claim·재시도·멱등성" 로직만
 * 검증하고 실제 외부 LLM 연동은 이 계약의 범위 밖으로 둔다(LinkFetchService.java와 같은 관례 —
 * 순수 시그니처 선언, 로직 없음).
 */
public interface SummaryGenerator {

    String summarize(String content) throws AiProcessingException;
}
