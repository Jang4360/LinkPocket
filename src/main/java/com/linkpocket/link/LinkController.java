package com.linkpocket.link;

import com.linkpocket.auth.AuthErrorCode;
import com.linkpocket.auth.user.AppUser;
import com.linkpocket.auth.web.AuthenticatedUserService;
import com.linkpocket.common.error.DomainException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final AuthenticatedUserService authenticatedUserService;
    private final LinkService linkService;

    public LinkController(AuthenticatedUserService authenticatedUserService, LinkService linkService) {
        this.authenticatedUserService = authenticatedUserService;
        this.linkService = linkService;
    }

    @PostMapping
    public ResponseEntity<LinkSaveResponse> save(
            @RequestBody LinkSaveRequest request,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        if (principal == null) {
            throw new DomainException(AuthErrorCode.AUTH_SESSION_INVALID);
        }
        AppUser user = authenticatedUserService.findByAuthenticatedPrincipal(principal);
        return ResponseEntity.ok(linkService.save(user.getId(), request.url()));
    }
}
