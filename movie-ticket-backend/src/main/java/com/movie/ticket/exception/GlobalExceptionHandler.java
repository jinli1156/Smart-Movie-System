package com.movie.ticket.exception;

import com.movie.ticket.common.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 集中处理业务异常、参数校验错误与系统异常，并转换为统一接口响应。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e) {
        log.warn("业务异常 [{}]: {}", e.getStatus().value(), e.getMessage());
        return ResponseEntity.status(e.getStatus())
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Result<?>> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("认证失败: {}", e.getMessage());
        return error(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<?>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return error(HttpStatus.FORBIDDEN, "权限不足");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Result<?>> handleValidationException(Exception e) {
        String message = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException ex = (MethodArgumentNotValidException) e;
            if (ex.getBindingResult().hasErrors()) {
                message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
            }
        } else if (e instanceof BindException) {
            BindException ex = (BindException) e;
            if (ex.getBindingResult().hasErrors()) {
                message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
            }
        }
        log.warn("参数校验异常: {}", message);
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<?>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突: {}", e.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "数据状态冲突，请刷新后重试");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        log.error("系统异常: ", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "系统异常，请联系管理员");
    }

    private ResponseEntity<Result<?>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Result.error(status.value(), message));
    }
}
