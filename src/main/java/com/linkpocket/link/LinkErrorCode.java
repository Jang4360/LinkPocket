package com.linkpocket.link;

import com.linkpocket.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum LinkErrorCode implements ErrorCode {
    LINK_INVALID_URL(HttpStatus.BAD_REQUEST, "올바른 URL이 아닙니다."),
    LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    LinkErrorCode(HttpStatus httpStatus, String defaultMessage) {
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
