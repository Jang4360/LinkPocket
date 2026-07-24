package com.linkpocket.auth.extension;

public record ExtensionTokenResponse(String accessToken, String refreshToken, long expiresIn) {
}
