package com.linkpocket.auth.extension;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/extension")
public class ExtensionAuthController {

    private final ExtensionAuthService extensionAuthService;

    public ExtensionAuthController(ExtensionAuthService extensionAuthService) {
        this.extensionAuthService = extensionAuthService;
    }

    @PostMapping("/oauth/callback")
    public ResponseEntity<ExtensionTokenResponse> callback(@RequestBody ExtensionOAuthCallbackRequest request) {
        return ResponseEntity.ok(extensionAuthService.exchangeCode(request));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ExtensionTokenResponse> refresh(@RequestBody ExtensionRefreshRequest request) {
        return ResponseEntity.ok(extensionAuthService.refresh(request.refreshToken()));
    }
}
