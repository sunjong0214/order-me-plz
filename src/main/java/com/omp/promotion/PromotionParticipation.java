package com.omp.promotion;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 할인 주문 한 건 = 한 행. 제약이 정합성의 마지막 방어선이다.
 * - 기본 키 (promotion_id, user_id): 1인 1회
 * - 유니크 (promotion_id, ticket_no): 같은 번호표를 두 주문이 쓸 수 없다 (MEMORY 방식. DB 방식은 ticket_no가 NULL이라 검사하지 않는다)
 * - 외래 키 (promotion_id, ticket_no) → promotion_tickets: 범위 밖 번호는 저장되지 않는다
 * "커밋 결과 모름"을 확인할 때도 (이벤트, 사용자) 행이 있는지 본다. 일반 주문 비용에 영향을 주지 않으려고 orders에 열을 더하지 않았다.
 * 스키마 정의용 엔티티이고 주문 경로는 JdbcTemplate으로 넣는다(식별자를 직접 주는 엔티티의 save는 merge가 되어 SELECT를 한 번 더 한다).
 */
@Entity
@Table(name = "promotion_participations",
        uniqueConstraints = @UniqueConstraint(name = PromotionParticipation.TICKET_UNIQUE, columnNames = {"promotion_id", "ticket_no"}))
@IdClass(PromotionParticipation.Key.class)
@NoArgsConstructor(access = PROTECTED)
public class PromotionParticipation {
    public static final String TICKET_UNIQUE = "uk_participation_ticket";

    @Id
    @Column(name = "promotion_id")
    private Long promotionId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "ticket_no")
    private Integer ticketNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 외래 키를 만들기 위한 읽기 전용 연관. 값은 위의 promotionId·ticketNo 열로 쓴다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
            @JoinColumn(name = "promotion_id", referencedColumnName = "promotion_id", insertable = false, updatable = false),
            @JoinColumn(name = "ticket_no", referencedColumnName = "ticket_no", insertable = false, updatable = false)},
            foreignKey = @ForeignKey(name = "fk_participation_ticket"))
    private PromotionTicket ticket;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long promotionId;
        private Long userId;
    }
}
