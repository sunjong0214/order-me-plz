package com.omp.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "DELIVERIES")
public class Delivery {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "delivery_id")
    private Long id;
    @Enumerated(STRING)
    private DeliveryStatus status;
}
