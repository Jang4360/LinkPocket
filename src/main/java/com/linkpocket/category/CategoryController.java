package com.linkpocket.category;

import com.linkpocket.auth.AuthErrorCode;
import com.linkpocket.auth.user.AppUser;
import com.linkpocket.auth.web.AuthenticatedUserService;
import com.linkpocket.common.error.DomainException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final AuthenticatedUserService authenticatedUserService;
    private final CategoryService categoryService;

    public CategoryController(
            AuthenticatedUserService authenticatedUserService,
            CategoryService categoryService
    ) {
        this.authenticatedUserService = authenticatedUserService;
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @RequestBody CategoryRequest request,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        return ResponseEntity.ok(categoryService.create(userId(principal), request.name()));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list(@AuthenticationPrincipal OAuth2User principal) {
        return ResponseEntity.ok(categoryService.list(userId(principal)));
    }

    @PatchMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> rename(
            @PathVariable UUID categoryId,
            @RequestBody CategoryRequest request,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        return ResponseEntity.ok(categoryService.rename(userId(principal), categoryId, request.name()));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal OAuth2User principal
    ) {
        categoryService.delete(userId(principal), categoryId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(OAuth2User principal) {
        if (principal == null) {
            throw new DomainException(AuthErrorCode.AUTH_SESSION_INVALID);
        }
        AppUser user = authenticatedUserService.findByAuthenticatedPrincipal(principal);
        return user.getId();
    }
}
