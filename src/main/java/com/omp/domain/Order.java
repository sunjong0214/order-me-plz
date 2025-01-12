package com.omp.domain;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ORDERS")
public class Order {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "ORDER_ID")
    private Long id;
    private Long deliveryId;
    private Long paymentId;
    private Long shopId;
}
