package com.linkpocket.common.error;

public record ErrorResponse(
        String code,
        String domain,
        String message,
        String traceId,
        Object details
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.code(),
                errorCode.domain(),
                errorCode.defaultMessage(),
                java.util.UUID.randomUUID().toString(),
                null
        );
    }
}
