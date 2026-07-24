package com.linkpocket.auth;

import com.linkpocket.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
    AUTH_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "세션이 만료되었습니다. 다시 로그인해주세요."),
    AUTH_SESSION_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 세션입니다. 다시 로그인해주세요."),
    AUTH_OAUTH_STATE_MISMATCH(HttpStatus.BAD_REQUEST, "로그인 세션이 유효하지 않습니다. 다시 시도해주세요."),
    AUTH_OAUTH_CODE_EXCHANGE_FAILED(HttpStatus.BAD_GATEWAY, "Google 로그인에 실패했습니다. 잠시 후 다시 시도해주세요."),
    AUTH_REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh token입니다. 다시 로그인해주세요."),
    AUTH_REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "비정상적인 로그인 시도가 감지되어 모든 세션이 종료되었습니다."),
    AUTH_FORBIDDEN_RESOURCE(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    AUTH_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없습니다."),
    AUTH_PKCE_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "PKCE 검증에 실패했습니다. 다시 로그인해주세요."),
    AUTH_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    AuthErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
