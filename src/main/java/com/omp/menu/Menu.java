package com.omp.menu;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import com.omp.menu.dto.UpdateMenuDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "MENUS")
@NoArgsConstructor(access = PROTECTED)
public class Menu {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "menu_id")
    private Long id;
    @Positive
    private Long shopId;
    @NotBlank
    private String name;
    @PositiveOrZero
    private int price;
    @PositiveOrZero
    @Version
    private int quantity;
    private String description;

    public Menu(Long shopId, int quantity, String name, Integer price, String description) {
        this.quantity = quantity;
        this.shopId = shopId;
        this.name = name;
        this.price = price;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public int deductQuantity() {
        return --quantity;
    }

    public Menu changeInfo(final UpdateMenuDto dto) {
        this.name = dto.name();
        this.price = dto.price();
        this.quantity = dto.quantity();
        this.description = dto.description();
        return this;
    }
}
