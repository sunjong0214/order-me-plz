package com.omp.orderMenu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
public class OrderMenu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_MENU_ID")
    @Getter
    private Long id;
    @Getter
    private Long menuId;
    private Long cartId;
    private int quantity;

    public OrderMenu(Long menuId, Long cartId, int quantity) {
        this.menuId = menuId;
        this.cartId = cartId;
        this.quantity = quantity;
    }
}
