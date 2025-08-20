package com.omp.orderMenu;

import com.omp.menu.MenuRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderMenuService {
    private final OrderMenuRepository orderMenuRepository;
    private final MenuRepository menuRepository;

    public List<OrderMenu> createOrderMenus(final List<OrderMenuRequest> orderMenus, final Long cartId) {
        return orderMenuRepository.saveAll(
                orderMenus.stream()
                        .map(OrderMenuRequest::from)
                        .toList());
    }

    public List<OrderMenu> createOrderMenus(final List<OrderMenu> orderMenus) {
        return orderMenuRepository.saveAll(orderMenus);
    }

    public void existsOrderMenus(List<OrderMenu> orderMenus) {
        List<Long> ids = orderMenus.stream()
                .map(OrderMenu::getMenuId)
                .toList();

        Long count = menuRepository.countByIdIn(ids);

        if (ids.size() != count) {
            throw new IllegalStateException();
        }
    }

    public List<OrderMenu> findOrderMenusByCartId(Long cartId) {
        return orderMenuRepository.findAllByCartId(cartId);
    }
}
