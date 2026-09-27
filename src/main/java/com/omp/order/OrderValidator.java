package com.omp.order;

import com.omp.order.dto.OrderValidateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주문 검증(validateOrder 단일 쿼리 1회). 동기, 비동기 SPLIT의 접수, 비동기 SINGLE의 워커가 같은 규칙을 쓴다.
 * 호출한 쪽의 트랜잭션 안에서 실행된다.
 */
@RequiredArgsConstructor
@Component
public class OrderValidator {
    private final OrderRepository orderRepository;

    public void validate(Long ordererId, Long shopId, Long cartId) {
        OrderValidateDto validDto = orderRepository.validateOrder(ordererId, shopId, cartId);
        // 세 행 중 하나라도 없으면 조인 결과가 없어 null이 온다.
        if (validDto == null || !validDto.isValid()) {
            throw new InvalidOrderException(ordererId, shopId, cartId);
        }
    }
}
