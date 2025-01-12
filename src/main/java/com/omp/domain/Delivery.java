package com.omp.domain;

import static jakarta.persistence.EnumType.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table
public class Delivery {
    @Id
    private Long id;
    @Enumerated(STRING)
    private DeliveryStatus status;
}
