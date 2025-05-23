package com.omp.order;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ORDERS")
@NoArgsConstructor(access = PROTECTED)
public class Order {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "ORDER_ID")
    private Long id;
    @Positive
    private Long ordererId;
    @Positive
    private Long cartId;
    @Positive
    private Long shopId;
    @Positive
    @Setter
    private Long deliveryId;

    public Order(Long ordererId, Long cartId, Long shopId) {
        this.ordererId = ordererId;
        this.cartId = cartId;
        this.shopId = shopId;
    }

    public Long getId() {
        return id;
    }
}
