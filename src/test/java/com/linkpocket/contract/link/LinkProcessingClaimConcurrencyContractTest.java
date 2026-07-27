package com.linkpocket.contract.link;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약: plan/04-async-ai-pipeline.md AC "worker 여러 개가 동시에 polling해도 같은 Link를 두
 * worker가 동시에 처리하지 않는다" + ADR-012 결정 1(SKIP LOCKED 배타 claim).
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - LinkProcessingWorker.pollAndProcessOnce()가 claim 시 `SELECT ... FOR UPDATE SKIP LOCKED`로
 *    배타적으로 행을 잡아 status를 즉시 *ING로 전이(짧은 트랜잭션)한 뒤에만 실제 처리를 수행한다.
 */
class LinkProcessingClaimConcurrencyContractTest extends AbstractLinkProcessingContractTest {

    @Test
    void concurrent_polls_result_in_exactly_one_summarize_call_per_link() throws Exception {
        fakeSummaryGenerator.withDelay(200);
        UUID linkId = seedLinkAtStatus("FETCHED", "이 링크는 충분히 긴 본문을 가지고 있다고 가정한다.");

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            Future<?>[] futures = new Future<?>[4];
            for (int i = 0; i < 4; i++) {
                futures[i] = executor.submit(() -> {
                    await(startGate);
                    linkProcessingWorker.pollAndProcessOnce();
                });
            }
            startGate.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        assertThat(fakeSummaryGenerator.callCount.get()).isEqualTo(1);
        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("SUMMARIZED");
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
