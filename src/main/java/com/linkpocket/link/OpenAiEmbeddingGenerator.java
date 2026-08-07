package com.linkpocket.link;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
public class OpenAiEmbeddingGenerator implements EmbeddingGenerator {

    static final String MODEL_VERSION = "text-embedding-3-small";

    private final RestClient restClient;
    private final String apiKey;

    public OpenAiEmbeddingGenerator(
            @Value("${linkpocket.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${linkpocket.openai.api-key:}") String apiKey
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public float[] embed(String chunkText) throws AiProcessingException {
        if (apiKey.isBlank()) {
            throw new AiProcessingException("EMBEDDING_API_ERROR", true);
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", MODEL_VERSION, "input", chunkText))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode values = response == null ? null : response.path("data").path(0).path("embedding");
            if (values == null || !values.isArray() || values.isEmpty()) {
                throw new AiProcessingException("EMBEDDING_API_ERROR", true);
            }
            float[] embedding = new float[values.size()];
            for (int index = 0; index < values.size(); index++) {
                embedding[index] = (float) values.get(index).asDouble();
            }
            return embedding;
        } catch (RestClientResponseException exception) {
            throw new AiProcessingException(
                    exception.getStatusCode().is4xxClientError()
                            ? "EMBEDDING_CONTENT_REJECTED" : "EMBEDDING_API_ERROR",
                    !exception.getStatusCode().is4xxClientError()
            );
        } catch (ResourceAccessException exception) {
            throw new AiProcessingException("EMBEDDING_API_ERROR", true);
        }
    }
}
