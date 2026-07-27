package com.linkpocket.link;

/**
 * plan-04 계약의 일부 — 임베딩 생성 경계(OpenAI 등 실제 임베딩 호출)를 나타내는 seam.
 * SummaryGenerator.java와 동일한 이유로 순수 시그니처만 선언한다.
 */
public interface EmbeddingGenerator {

    float[] embed(String chunkText) throws AiProcessingException;
}
