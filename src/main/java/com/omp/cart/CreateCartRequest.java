package com.omp.cart;

import com.omp.menu.MenuList;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateCartRequest {
    @Positive
    private final Long userId;
    @Positive
    private final Long shopId;
    private final MenuList menuList;

    public static CreateCartDto from(final CreateCartRequest request) {
        return new CreateCartDto(request.userId, request.shopId, request.menuList);
    }
}
