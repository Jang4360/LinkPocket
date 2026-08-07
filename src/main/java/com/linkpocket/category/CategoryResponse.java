package com.linkpocket.category;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, boolean isSystem) {
}
