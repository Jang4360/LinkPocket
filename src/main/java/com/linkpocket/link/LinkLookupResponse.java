package com.linkpocket.link;

import java.util.UUID;

public record LinkLookupResponse(boolean saved, UUID linkId) {
}
