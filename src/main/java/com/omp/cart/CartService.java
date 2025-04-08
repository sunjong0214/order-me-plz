package com.omp.cart;

import com.omp.cart.dto.CreateCartRequest;
import com.omp.menu.Menu;
import com.omp.menu.MenuRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CartService {
    private final CartRepository cartRepository;
    private final MenuRepository menuRepository;

    public Cart findCartsBy(final Long id) {
        return cartRepository.findById(id).orElseThrow();
    }

    public Long saveCartBy(final CreateCartRequest request) {
        List<Menu> menus = menuRepository.findAllById(request.getMenuIds());
        Cart newCart = new Cart(request.getUserId(), request.getShopId(), menus);
        return cartRepository.save(newCart).getId();
    }
}
