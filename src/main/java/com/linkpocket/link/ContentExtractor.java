package com.linkpocket.link;

import net.dankito.readability4j.Article;
import net.dankito.readability4j.Readability4J;

import java.util.Optional;

final class ContentExtractor {

    private static final int MINIMUM_BODY_LENGTH = 40;

    Optional<ExtractedContent> extract(String url, String html) {
        try {
            Article article = new Readability4J(url, html).parse();
            if (article == null || article.getTextContent() == null
                    || article.getTextContent().trim().length() < MINIMUM_BODY_LENGTH) {
                return Optional.empty();
            }
            String title = article.getTitle();
            if (title == null || title.isBlank()) {
                title = "Untitled";
            }
            return Optional.of(new ExtractedContent(title, article.getTextContent()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    record ExtractedContent(String title, String body) {
    }
}
