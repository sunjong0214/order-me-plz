package com.omp.cart;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CartService {
    private final CartRepository cartRepository;

    public Cart findCartBy(final Long id) {
        return cartRepository.findById(id).orElseThrow();
    }

    public Long saveCartBy(final CreateCartDto createCartDto) {
        return cartRepository.save(CreateCartDto.of(createCartDto)).getId();
    }
}
