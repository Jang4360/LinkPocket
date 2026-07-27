package com.linkpocket.link;

/**
 * plan-04 계약의 일부 — job claim(SKIP LOCKED) polling worker의 seam.
 * 운영에서는 {@code @Scheduled}가 주기적으로 {@link #pollAndProcessOnce()}를 호출하지만,
 * 계약 테스트는 타이밍에 의존하지 않기 위해 이 메서드를 직접, 결정적으로 호출한다.
 *
 * 한 번 호출에 "claim 가능한 모든 Link"를 배치로 집어(SKIP LOCKED) 각자의 현재 상태에 맞는
 * 다음 단계(fetch/summarize/chunk/embed+index)를 한 단계씩 진행시킨다 — 여러 단계를 한 번에
 * 끝까지 진행하지 않는다(ADR-012 결정 2, tx 경계를 단계별로 짧게 유지).
 */
public interface LinkProcessingWorker {

    void pollAndProcessOnce();
}
