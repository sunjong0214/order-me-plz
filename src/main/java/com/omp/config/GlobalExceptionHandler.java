package com.omp.config;

import com.omp.order.InvalidOrderException;
import com.omp.order.async.AsyncCapacityExceededException;
import com.omp.order.async.OrderJobNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<String> invalidOrder(InvalidOrderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(OrderJobNotFoundException.class)
    public ResponseEntity<String> jobNotFound(OrderJobNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    // 포화 시 백프레셔: 몰래 동기 실행하지 않고 재시도를 안내한다.
    @ExceptionHandler(AsyncCapacityExceededException.class)
    public ResponseEntity<String> capacityExceeded(AsyncCapacityExceededException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(e.getMessage());
    }
}
