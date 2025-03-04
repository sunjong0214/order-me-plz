package com.omp.cart;

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
    private CartMenus cartMenus = new CartMenus();

    public Cart(Long userId, Long shopId, CartMenus cartMenus) {
        this.userId = userId;
        this.shopId = shopId;
        this.cartMenus = cartMenus;
    }

    public Long getId() {
        return id;
    }
}
