package com.omp.config;

import com.omp.order.InvalidOrderException;
import com.omp.order.SyncCapacityExceededException;
import com.omp.order.async.AsyncCapacityExceededException;
import com.omp.order.async.AsyncOrderProperties;
import com.omp.order.async.OrderJobNotFoundException;
import com.omp.promotion.PromotionRejectedException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final AsyncOrderProperties asyncOrderProperties;

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<String> invalidOrder(InvalidOrderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(OrderJobNotFoundException.class)
    public ResponseEntity<String> jobNotFound(OrderJobNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    // 할인 주문을 받지 않은 이유. 409는 Tomcat이 응답 뒤 연결을 끊지 않는 코드다(매진 응답은 초당 수천 건 나올 수 있다).
    @ExceptionHandler(PromotionRejectedException.class)
    public ResponseEntity<Map<String, String>> promotionRejected(PromotionRejectedException e) {
        HttpStatus status = e.reason() == PromotionRejectedException.Reason.NOT_FOUND ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(Map.of("code", e.failureCode()));
    }

    // 포화 시 백프레셔: 몰래 동기 실행하지 않고 재시도를 안내한다. 응답 코드는 429(기본) 또는 503(AsyncOrderProperties).
    // 동기 빠른 거절(SyncOrderAdmission)도 같은 코드를 쓴다(비교할 때 응답 코드 차이가 방식 차이처럼 보이지 않게).
    @ExceptionHandler({AsyncCapacityExceededException.class, SyncCapacityExceededException.class})
    public ResponseEntity<String> capacityExceeded(RuntimeException e) {
        return ResponseEntity.status(asyncOrderProperties.rejectStatus())
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(e.getMessage());
    }
}
