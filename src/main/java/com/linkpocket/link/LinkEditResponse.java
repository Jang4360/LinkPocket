package com.linkpocket.link;

import java.util.UUID;

public record LinkEditResponse(UUID id, String title, String summary) {
}
