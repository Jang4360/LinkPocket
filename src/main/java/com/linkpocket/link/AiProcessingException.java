package com.linkpocket.link;

/**
 * plan-04(async-ai-pipeline) 계약의 일부 — 요약·임베딩 단계에서 실패를 표현하는 최소 타입.
 * ADR-014의 재시도 가능/불가 구분을 그대로 옮긴다: {@code retryable=true}면 최대 2회까지
 * 재시도 대상(lease 만료 재claim), {@code false}면 1회 시도로 즉시 영구 실패
 * (link.status → READY_WITHOUT_INDEX)로 확정한다.
 *
 * 이 파일은 순수 시그니처 선언이다(로직 없음) — 계약 테스트가 컴파일되려면 최소한 이 타입이
 * 존재해야 하기 때문에 Claude가 계약의 일부로 둔다(LinkFetchService.java와 동일한 관례).
 */
public class AiProcessingException extends Exception {

    private final String reason;
    private final boolean retryable;

    public AiProcessingException(String reason, boolean retryable) {
        super(reason);
        this.reason = reason;
        this.retryable = retryable;
    }

    public String reason() {
        return reason;
    }

    public boolean retryable() {
        return retryable;
    }
}
