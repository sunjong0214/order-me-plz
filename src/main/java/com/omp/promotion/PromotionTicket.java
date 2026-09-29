package com.omp.promotion;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * MEMORY 방식의 번호표 1~총 재고. 이벤트를 만들 때 한 번에 채운다.
 * 참여 행이 이 행을 외래 키로 가리키므로, 메모리 발급기에 버그가 있어도 범위 밖 번호로는 할인 주문이 저장되지 않는다. 스키마 정의용.
 */
@Entity
@Table(name = "promotion_tickets")
@IdClass(PromotionTicket.Key.class)
@NoArgsConstructor(access = PROTECTED)
public class PromotionTicket {
    @Id
    @Column(name = "promotion_id")
    private Long promotionId;

    @Id
    @Column(name = "ticket_no")
    private Integer ticketNo;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long promotionId;
        private Integer ticketNo;
    }
}
