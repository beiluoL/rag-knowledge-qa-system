package com.example.ragkb.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        return buildResponse(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("code", 400);
        body.put("message", "参数校验失败");
        body.put("errors", errors);
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.badRequest().body(body);
    }

    /** 缺必填请求参数：返回 400，仅暴露参数名，不泄露内部信息 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return buildResponse(400, "缺少必需参数：" + e.getParameterName());
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPathVar(MissingPathVariableException e) {
        return buildResponse(400, "缺少路径变量：" + e.getVariableName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return buildResponse(400, "参数类型错误：" + e.getName());
    }

    /** 请求体缺失/格式错误（如 @RequestBody 为空）：返回 400 友好提示 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e) {
        return buildResponse(400, "请求体格式错误或缺失");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        return buildResponse(403, "权限不足，无法访问该资源");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return buildResponse(400, "文件大小超过限制（最大 20MB）");
    }

    /** 兜底：返回 500 固定文案，原始异常仅记日志，绝不回显给前端（防信息泄露） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("系统异常", e);
        return buildResponse(500, "服务器内部错误，请稍后重试");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(int code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());
        HttpStatus status = code >= 500 ? HttpStatus.INTERNAL_SERVER_ERROR
                : code == 403 ? HttpStatus.FORBIDDEN
                : HttpStatus.BAD_REQUEST;
        return new ResponseEntity<>(body, status);
    }
}
