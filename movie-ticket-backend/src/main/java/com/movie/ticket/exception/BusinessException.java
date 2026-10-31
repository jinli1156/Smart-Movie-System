package com.movie.ticket.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 可预期业务失败的异常载体，携带状态码与面向用户的错误信息。 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;
    private final HttpStatus status;

    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public BusinessException(Integer code, String message) {
        this(resolveStatus(code), code, message);
    }

    public BusinessException(HttpStatus status, String message) {
        this(status, status.value(), message);
    }

    public BusinessException(HttpStatus status, Integer code, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    private static HttpStatus resolveStatus(Integer code) {
        HttpStatus resolved = code == null ? null : HttpStatus.resolve(code);
        return resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
    }
}
