package com.linkpocket.category;

import com.linkpocket.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CategoryErrorCode implements ErrorCode {
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없습니다."),
    CATEGORY_SYSTEM_PROTECTED(HttpStatus.BAD_REQUEST, "카테고리 없음은 수정하거나 삭제할 수 없습니다."),
    CATEGORY_DUPLICATE_NAME(HttpStatus.CONFLICT, "같은 이름의 카테고리가 이미 있습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    CategoryErrorCode(HttpStatus httpStatus, String defaultMessage) {
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
