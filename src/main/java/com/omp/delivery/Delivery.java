package com.omp.delivery;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "DELIVERIES")
@NoArgsConstructor(access = PROTECTED)
public class Delivery {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "delivery_id")
    private Long id;
    @Enumerated(STRING)
    private DeliveryStatus status;

    public Delivery(DeliveryStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }
}
