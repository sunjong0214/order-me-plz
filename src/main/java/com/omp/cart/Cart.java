package com.omp.cart;

import static jakarta.persistence.GenerationType.IDENTITY;

import com.omp.menu.MenuList;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "CARTS")
public class Cart {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "cart_id")
    private Long id;
    private Long userId;
    private Long shopId;
    @Embedded
    private MenuList menuList;
}
