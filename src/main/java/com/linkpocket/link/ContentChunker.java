package com.linkpocket.link;

import java.util.ArrayList;
import java.util.List;

final class ContentChunker {

    // 초기 가설, ADR-013 참고: 실제 문서 분포와 Recall@K 측정 뒤 조정한다.
    private static final int TARGET_CHUNK_SIZE = 1_000;
    // 초기 가설, ADR-013 참고: 경계 문맥 보존을 위한 15% overlap이다.
    private static final int OVERLAP_SIZE = 150;

    List<String> split(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("본문이 비어 있습니다.");
        }
        String[] paragraphs = body.trim().split("\\R\\s*\\R");
        if (paragraphs.length < 2 || containsOversizedParagraph(paragraphs)) {
            return fixedSizeChunks(body.trim());
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (current.length() > 0 && current.length() + paragraph.length() + 2 > TARGET_CHUNK_SIZE) {
                chunks.add(current.toString());
                current = new StringBuilder(overlap(current)).append(paragraph);
            } else {
                if (current.length() > 0) {
                    current.append("\n\n");
                }
                current.append(paragraph);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private boolean containsOversizedParagraph(String[] paragraphs) {
        for (String paragraph : paragraphs) {
            if (paragraph.length() > TARGET_CHUNK_SIZE) {
                return true;
            }
        }
        return false;
    }

    private List<String> fixedSizeChunks(String content) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + TARGET_CHUNK_SIZE);
            chunks.add(content.substring(start, end));
            if (end == content.length()) {
                break;
            }
            start = end - OVERLAP_SIZE;
        }
        return chunks;
    }

    private String overlap(StringBuilder content) {
        int start = Math.max(0, content.length() - OVERLAP_SIZE);
        return content.substring(start) + "\n\n";
    }
}
