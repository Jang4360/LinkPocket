package com.linkpocket.contract.link;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/02-link-save-minimal.md 실패 조건("동시 100회 저장에서 row가 2개 이상이면 실패")
 * + ADR-010 결정 1(unique constraint + INSERT ON CONFLICT 원자적 upsert)
 *
 * 이 테스트는 진짜 동시 요청을 흉내낸다 — CountDownLatch로 모든 스레드가 같은 순간에
 * 요청을 쏘게 만들어, "먼저 조회하고 나중에 삽입하는" TOCTOU 구현이었다면 잡아낼 수 있는
 * race를 재현한다. select-then-insert(예외 처리 없이)로 구현하면 이 테스트가 실패해야 한다.
 */
class LinkIdempotentSaveContractTest extends AbstractLinkContractTest {

    private static final int CONCURRENT_REQUESTS = 100;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void concurrent_duplicate_saves_produce_exactly_one_row_and_same_link_id() throws Exception {
        String sub = seedUser("concurrent-user@example.com", "Concurrent User");
        String url = "https://example.com/concurrently-saved-article";

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startGate = new CountDownLatch(1);
        List<String> returnedLinkIds = new CopyOnWriteArrayList<>();

        try {
            List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, CONCURRENT_REQUESTS)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        startGate.await();
                        MvcResult result = mockMvc.perform(post("/api/links")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(saveUrlRequestBody(url))
                                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                                .attributes(a -> {
                                                    a.put("sub", sub);
                                                    a.put("email", "concurrent-user@example.com");
                                                    a.put("name", "Concurrent User");
                                                })))
                                .andExpect(status().isOk())
                                .andReturn();
                        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                        returnedLinkIds.add(body.get("linkId").asText());
                        return null;
                    })
                    .collect(Collectors.toList());

            List<Future<Void>> futures = tasks.stream().map(executor::submit).collect(Collectors.toList());
            startGate.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        assertThat(returnedLinkIds).hasSize(CONCURRENT_REQUESTS);
        Set<String> distinctLinkIds = returnedLinkIds.stream().collect(Collectors.toSet());
        assertThat(distinctLinkIds)
                .as("동시 %d회 저장이 모두 같은 linkId를 반환해야 한다(멱등)", CONCURRENT_REQUESTS)
                .hasSize(1);

        Integer rowCount = jdbcTemplate.queryForObject(
                "select count(*) from link where user_id = (select id from app_user where google_sub = ?)",
                Integer.class, sub);
        assertThat(rowCount)
                .as("동시 %d회 저장 후에도 row는 1개여야 한다", CONCURRENT_REQUESTS)
                .isEqualTo(1);
    }
}
