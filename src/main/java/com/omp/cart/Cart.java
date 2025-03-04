package com.omp.cart;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import com.omp.menu.Menus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
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
    private Long shopId;
    @Embedded
    private Menus menus;

    public Cart(Long userId, Long shopId, Menus menus) {
        this.userId = userId;
        this.shopId = shopId;
        this.menus = menus;
    }

    public Long getId() {
        return id;
    }
}
