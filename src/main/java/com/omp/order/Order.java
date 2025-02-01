package com.omp.order;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ORDERS")
@NoArgsConstructor(access = PROTECTED)
public class Order {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "ORDER_ID")
    private Long id;
    private Long deliveryId;
    private Long paymentId;
    private Long shopId;

    public Order(Long deliveryId, Long paymentId, Long shopId) {
        this.deliveryId = deliveryId;
        this.paymentId = paymentId;
        this.shopId = shopId;
    }

    public Long getId() {
        return id;
    }
}
