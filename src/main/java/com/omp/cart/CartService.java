package com.omp.cart;

import com.omp.cart.dto.AddOrderMenuRequest;
import com.omp.menu.MenuRepository;
import com.omp.orderMenu.OrderMenu;
import com.omp.orderMenu.OrderMenuRequest;
import com.omp.orderMenu.OrderMenuService;
import com.omp.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional
public class CartService {
    private final CartRepository cartRepository;
    private final ShopRepository shopRepository;
    private final MenuRepository menuRepository;
    private final OrderMenuService orderMenuService;

    public Cart findCartsBy(final Long id) {
        return cartRepository.findById(id).orElseThrow();
    }

    /*
        todo : cart는 어느 시점에 만들어지고 어느 시점에 장바구니 담기가 되고?
     */
    public Long saveCartBy(final AddOrderMenuRequest request) {
        if (!shopRepository.existsById(request.getShopId())) {
            throw new IllegalStateException();
        }

        Cart newCart = new Cart(request.getUserId(), request.getShopId());

        return cartRepository.save(newCart).getId();
    }

    public CartResponse addOrderMenu(final OrderMenuRequest orderMenuRequest) {
        Cart cart = cartRepository.findById(orderMenuRequest.getCartId()).orElseThrow();

        if (!menuRepository.existsById(orderMenuRequest.getMenuId())) {
            throw new IllegalStateException();
        }

        OrderMenu orderMenu = orderMenuService.createOrderMenu(orderMenuRequest);

        return new CartResponse(cart.getId(), orderMenu.getId());
    }
}
