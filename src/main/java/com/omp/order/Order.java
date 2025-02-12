package com.omp.order;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ORDERS")
@NoArgsConstructor(access = PROTECTED)
public class Order {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "ORDER_ID")
    private Long id;
    @Positive
    private Long deliveryId;
    @Positive
    private Long ordererId;
    @Positive
    private Long shopId;
    @Embedded
    private OrderMenus orderMenus;

    public Order(Long deliveryId, Long ordererId, Long shopId, OrderMenus orderMenus) {
        this.deliveryId = deliveryId;
        this.ordererId = ordererId;
        this.shopId = shopId;
        this.orderMenus = orderMenus;
    }

    public Long getId() {
        return id;
    }
}
