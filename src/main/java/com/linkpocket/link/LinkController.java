package com.linkpocket.link;

import com.linkpocket.auth.AuthErrorCode;
import com.linkpocket.auth.user.AppUser;
import com.linkpocket.auth.web.AuthenticatedUserService;
import com.linkpocket.common.error.DomainException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final AuthenticatedUserService authenticatedUserService;
    private final LinkService linkService;
    private final LinkCategoryService linkCategoryService;

    public LinkController(
            AuthenticatedUserService authenticatedUserService,
            LinkService linkService,
            LinkCategoryService linkCategoryService
    ) {
        this.authenticatedUserService = authenticatedUserService;
        this.linkService = linkService;
        this.linkCategoryService = linkCategoryService;
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

    @GetMapping("/lookup")
    public ResponseEntity<LinkLookupResponse> lookup(
            @RequestParam String url,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        AppUser user = authenticatedUser(principal);
        return ResponseEntity.ok(linkService.lookup(user.getId(), url));
    }

    @GetMapping("/{linkId}/status")
    public ResponseEntity<LinkStatusResponse> status(
            @PathVariable UUID linkId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        AppUser user = authenticatedUser(principal);
        return ResponseEntity.ok(linkService.status(user.getId(), linkId));
    }

    @DeleteMapping("/{linkId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID linkId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        AppUser user = authenticatedUser(principal);
        linkService.delete(user.getId(), linkId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{linkId}/categories")
    public ResponseEntity<Void> replaceCategories(
            @PathVariable UUID linkId,
            @RequestBody LinkCategoryUpdateRequest request,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        AppUser user = authenticatedUser(principal);
        linkCategoryService.replaceCategories(user.getId(), linkId, request.categoryIds());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{linkId}")
    public ResponseEntity<LinkEditResponse> edit(
            @PathVariable UUID linkId,
            @RequestBody LinkEditRequest request,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        AppUser user = authenticatedUser(principal);
        return ResponseEntity.ok(linkService.edit(user.getId(), linkId, request));
    }

    private AppUser authenticatedUser(OAuth2User principal) {
        if (principal == null) {
            throw new DomainException(AuthErrorCode.AUTH_SESSION_INVALID);
        }
        return authenticatedUserService.findByAuthenticatedPrincipal(principal);
    }
}
