package com.linkpocket.link;

import java.util.UUID;

public record LinkSaveResponse(UUID linkId, String canonicalUrl, String status) {
}
