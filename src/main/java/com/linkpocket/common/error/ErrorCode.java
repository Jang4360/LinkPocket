package com.linkpocket.common.error;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    String code();

    HttpStatus httpStatus();

    String defaultMessage();

    default String domain() {
        return code().substring(0, code().indexOf('_'));
    }
}
