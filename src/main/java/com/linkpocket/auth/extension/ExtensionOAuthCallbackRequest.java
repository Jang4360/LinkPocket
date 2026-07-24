package com.linkpocket.auth.extension;

public record ExtensionOAuthCallbackRequest(String code, String codeVerifier, String redirectUri) {
}
