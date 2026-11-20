package dev.knowledgehub.shared;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiErrors {
    private ResponseEntity<?> error(int status, String code) {
        return ResponseEntity.status(status).body(Map.of("code",code,"correlationId",MDC.get("correlationId") == null ? "unknown" : MDC.get("correlationId")));
    }
    @ExceptionHandler(HubException.class) ResponseEntity<?> hub(HubException e) { return error(e.status(),e.code()); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid(Exception e) { return error(400,"INVALID_INPUT"); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> size(Exception e) { return error(413,"UPLOAD_TOO_LARGE"); }
    @ExceptionHandler(DataAccessException.class) ResponseEntity<?> db(Exception e) { return error(503,"STORAGE_UNAVAILABLE"); }
}
