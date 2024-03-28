package io.nbc.selectedseat.web.excpetion;

import io.nbc.selectedseat.web.common.dto.ResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ResponseDTO<String>> handleException(
        RuntimeException ex
    ) {
        return ResponseEntity.badRequest()
            .body(
                ResponseDTO.<String>builder()
                    .statusCode(HttpStatus.BAD_REQUEST.value())
                    .message(ex.getMessage())
                    .data(null)
                    .build()
            );
    }
}
