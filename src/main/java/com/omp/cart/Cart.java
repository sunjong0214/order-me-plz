package com.omp.cart;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CARTS")
@NoArgsConstructor(access = PROTECTED)
public class Cart {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "cart_id")
    private Long id;
    @Positive
    private Long userId;
    @Positive
    @Getter
    private Long shopId;

    public Cart(Long userId, Long shopId) {
        this.userId = userId;
        this.shopId = shopId;
    }

    public Long getId() {
        return id;
    }
}
