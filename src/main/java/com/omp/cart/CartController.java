package com.omp.cart;

import com.omp.cart.dto.AddOrderMenuRequest;
import com.omp.orderMenu.OrderMenuRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/cart")
@RestController
public class CartController {
    private final CartService cartService;

    @GetMapping("/{id}")
    public Cart getCartsById(final @PathVariable Long id) {
        return cartService.findCartsBy(id);
    }

    @PostMapping
    public CartResponse addCartMenu(final @RequestBody AddOrderMenuRequest request) {
        Long cartId = request.getCartId();
        if (cartId == null) {
            cartId = cartService.saveCartBy(request);
        }
        return cartService.addOrderMenu(new OrderMenuRequest(request.getMenuId(), cartId, request.getQuantity()));
    }
}
