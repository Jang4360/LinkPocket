package com.linkpocket.auth.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkpocket.auth.AuthErrorCode;
import com.linkpocket.auth.session.DeviceSession;
import com.linkpocket.auth.session.DeviceSessionRepository;
import com.linkpocket.auth.session.RefreshToken;
import com.linkpocket.auth.session.RefreshTokenRepository;
import com.linkpocket.auth.user.AppUser;
import com.linkpocket.auth.user.AppUserRepository;
import com.linkpocket.common.error.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class ExtensionAuthService {

    private static final long ACCESS_TOKEN_TTL_SECONDS = 900;

    private final AppUserRepository appUserRepository;
    private final DeviceSessionRepository deviceSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String tokenUri;
    private final String userInfoUri;
    private final SecureRandom secureRandom = new SecureRandom();

    public ExtensionAuthService(
            AppUserRepository appUserRepository,
            DeviceSessionRepository deviceSessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            ObjectMapper objectMapper,
            @Value("${spring.security.oauth2.client.provider.google.token-uri:https://oauth2.googleapis.com/token}") String tokenUri,
            @Value("${spring.security.oauth2.client.provider.google.user-info-uri:https://openidconnect.googleapis.com/v1/userinfo}") String userInfoUri
    ) {
        this.appUserRepository = appUserRepository;
        this.deviceSessionRepository = deviceSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
    }

    @Transactional
    public ExtensionTokenResponse exchangeCode(ExtensionOAuthCallbackRequest request) {
        String googleAccessToken = exchangeAuthorizationCode(request);
        GoogleUserInfo googleUser = loadUserInfo(googleAccessToken);
        AppUser user = appUserRepository.findByGoogleSub(googleUser.sub())
                .orElseGet(() -> appUserRepository.save(new AppUser(
                        UUID.randomUUID(), googleUser.sub(), googleUser.email(), googleUser.name(), Instant.now()
                )));

        DeviceSession deviceSession = deviceSessionRepository.save(new DeviceSession(
                UUID.randomUUID(), user, UUID.randomUUID(), Instant.now()
        ));
        return issueTokens(deviceSession);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public ExtensionTokenResponse refresh(String rawRefreshToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new DomainException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));

        DeviceSession deviceSession = refreshToken.getDeviceSession();
        if (deviceSession.getRevokedAt() != null) {
            throw new DomainException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }
        if (refreshToken.getConsumedAt() != null) {
            deviceSession.revoke(Instant.now());
            throw new DomainException(AuthErrorCode.AUTH_REFRESH_TOKEN_REUSED);
        }

        refreshToken.consume(Instant.now());
        return issueTokens(deviceSession);
    }

    private String exchangeAuthorizationCode(ExtensionOAuthCallbackRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", request.code());
        form.add("redirect_uri", request.redirectUri());
        form.add("code_verifier", request.codeVerifier());

        try {
            String response = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return requiredText(readJson(response), "access_token");
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new DomainException(AuthErrorCode.AUTH_PKCE_VERIFICATION_FAILED);
            }
            throw new DomainException(AuthErrorCode.AUTH_OAUTH_CODE_EXCHANGE_FAILED);
        } catch (ResourceAccessException exception) {
            throw new DomainException(AuthErrorCode.AUTH_OAUTH_CODE_EXCHANGE_FAILED);
        }
    }

    private GoogleUserInfo loadUserInfo(String googleAccessToken) {
        try {
            String response = restClient.get()
                    .uri(userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + googleAccessToken)
                    .retrieve()
                    .body(String.class);
            JsonNode json = readJson(response);
            return new GoogleUserInfo(
                    requiredText(json, "sub"),
                    requiredText(json, "email"),
                    requiredText(json, "name")
            );
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new DomainException(AuthErrorCode.AUTH_OAUTH_CODE_EXCHANGE_FAILED);
        }
    }

    private ExtensionTokenResponse issueTokens(DeviceSession deviceSession) {
        String refreshToken = randomToken();
        refreshTokenRepository.save(new RefreshToken(
                UUID.randomUUID(), deviceSession, hash(refreshToken), Instant.now()
        ));
        return new ExtensionTokenResponse(randomToken(), refreshToken, ACCESS_TOKEN_TTL_SECONDS);
    }

    private JsonNode readJson(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception exception) {
            throw new DomainException(AuthErrorCode.AUTH_OAUTH_CODE_EXCHANGE_FAILED);
        }
    }

    private String requiredText(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || value.asText().isBlank()) {
            throw new DomainException(AuthErrorCode.AUTH_OAUTH_CODE_EXCHANGE_FAILED);
        }
        return value.asText();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private record GoogleUserInfo(String sub, String email, String name) {
    }
}
