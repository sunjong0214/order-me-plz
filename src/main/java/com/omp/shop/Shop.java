package com.omp.shop;

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
@Table(name = "SHOPS")
@NoArgsConstructor(access = PROTECTED)
public class Shop {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "shop_id")
    private Long id;
    private String name;
    @Enumerated(STRING)
    private ShopCategory category;
    private boolean isOpen;

    public Shop(String name, ShopCategory category) {
        this.name = name;
        this.category = category;
        this.isOpen = false;
    }

    public Long getId() {
        return id;
    }
}
