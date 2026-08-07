package com.linkpocket.link;

import java.util.List;
import java.util.UUID;

public record LinkCategoryUpdateRequest(List<UUID> categoryIds) {
}
