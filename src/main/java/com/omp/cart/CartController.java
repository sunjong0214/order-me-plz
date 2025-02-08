package com.omp.cart;

import com.omp.cart.dto.CreateCartRequest;
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
    public Cart getCartById(final @PathVariable Long id) {
        return cartService.findCartBy(id);
    }

    @PostMapping
    public Long createCart(final @RequestBody CreateCartRequest request) {
        return cartService.saveCartBy(CreateCartRequest.from(request));
    }
}
