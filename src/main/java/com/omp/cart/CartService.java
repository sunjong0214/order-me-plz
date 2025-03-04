package com.omp.cart;

import com.omp.cart.dto.CreateCartDto;
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
        return cartRepository.save(CreateCartDto.from(createCartDto)).getId();
    }
}
