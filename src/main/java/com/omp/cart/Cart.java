package com.omp.cart;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import com.omp.menu.MenuList;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
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
    private MenuList menuList;

    public Cart(Long userId, Long shopId, MenuList menuList) {
        this.userId = userId;
        this.shopId = shopId;
        this.menuList = menuList;
    }

    public Long getId() {
        return id;
    }
}
