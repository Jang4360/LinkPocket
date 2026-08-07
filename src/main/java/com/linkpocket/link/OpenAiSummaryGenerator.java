package com.linkpocket.link;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Service
public class OpenAiSummaryGenerator implements SummaryGenerator {

    static final String MODEL_VERSION = "gpt-4o-mini";

    private final RestClient restClient;
    private final String apiKey;

    public OpenAiSummaryGenerator(
            @Value("${linkpocket.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${linkpocket.openai.api-key:}") String apiKey
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public String summarize(String content) throws AiProcessingException {
        if (apiKey.isBlank()) {
            throw new AiProcessingException("SUMMARIZE_API_ERROR", true);
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", MODEL_VERSION,
                            "messages", List.of(
                                    Map.of("role", "system", "content", "링크 본문을 한국어로 세 문장 이내로 요약하세요."),
                                    Map.of("role", "user", "content", content)
                            )
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            String summary = response == null ? null : response.path("choices").path(0)
                    .path("message").path("content").asText();
            if (summary == null || summary.isBlank()) {
                throw new AiProcessingException("SUMMARIZE_API_ERROR", true);
            }
            return summary;
        } catch (RestClientResponseException exception) {
            throw new AiProcessingException(
                    exception.getStatusCode().is4xxClientError()
                            ? "SUMMARIZE_CONTENT_REJECTED" : "SUMMARIZE_API_ERROR",
                    !exception.getStatusCode().is4xxClientError()
            );
        } catch (ResourceAccessException exception) {
            throw new AiProcessingException("SUMMARIZE_API_ERROR", true);
        }
    }
}
