package com.linkpocket.link;

import java.util.UUID;

/**
 * plan-03(safe-fetch-extract) 계약의 서비스 시그니처 — 이 plan은 공개 API가 없어(docs/plan/03
 * -safe-fetch-extract.md "스코프 판단") 계약 테스트가 이 인터페이스를 직접 호출한다.
 *
 * 이 파일은 순수 시그니처 선언이다(로직 없음) — 계약 테스트가 Java 타입 시스템상 컴파일되려면
 * 최소한 이 타입이 존재해야 하기 때문에 Claude가 계약의 일부로 둔다. 실제 구현(Codex)은
 * 이 인터페이스를 구현하는 @Service 클래스(예: LinkFetchServiceImpl)로 제공한다.
 */
public interface LinkFetchService {

    /**
     * PENDING 상태의 Link를 안전하게 fetch·추출해 상태를 전이시킨다.
     * 이미 FETCHING 이상으로 전이된 Link(다른 호출이 선점)라면 아무 것도 하지 않고 반환한다.
     */
    void fetchAndExtract(UUID linkId);
}
