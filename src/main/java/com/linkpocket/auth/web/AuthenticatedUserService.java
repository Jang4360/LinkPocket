package com.linkpocket.auth.web;

import com.linkpocket.auth.AuthErrorCode;
import com.linkpocket.auth.user.AppUser;
import com.linkpocket.auth.user.AppUserRepository;
import com.linkpocket.common.error.DomainException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedUserService {

    private final AppUserRepository appUserRepository;

    public AuthenticatedUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public AppUser findByAuthenticatedPrincipal(OAuth2User principal) {
        Object sub = principal.getAttributes().get("sub");
        if (!(sub instanceof String googleSub) || googleSub.isBlank()) {
            throw new DomainException(AuthErrorCode.AUTH_SESSION_INVALID);
        }

        return appUserRepository.findByGoogleSub(googleSub)
                .orElseThrow(() -> new DomainException(AuthErrorCode.AUTH_SESSION_INVALID));
    }
}
