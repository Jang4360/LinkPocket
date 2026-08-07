package com.linkpocket.link;

import java.time.Instant;
import java.util.UUID;

public record LinkStatusResponse(UUID linkId, String status, Instant updatedAt) {
}
