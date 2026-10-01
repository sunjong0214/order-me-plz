package com.omp.order;

import static com.omp.cart.QCart.cart;
import static com.omp.shop.QShop.shop;
import static com.omp.user.QUser.user;

import com.omp.order.dto.OrderValidateDto;
import com.omp.user.UserStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OrderRepositoryCustomImpl implements OrderRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public OrderValidateDto validateOrder(Long ordererId, Long shopId, Long cartId) {
        return queryFactory.select(
                        Projections.constructor(OrderValidateDto.class,
                                new CaseBuilder()
                                        .when(user.isNull().or(user.status.eq(UserStatus.BAN)))
                                        .then(false)
                                        .otherwise(true).as("validOrderer"),
                                new CaseBuilder()
                                        .when(shop.isNull().or(shop.isOpen.eq(false)))
                                        .then(false)
                                        .otherwise(true).as("validShop"),
                                // 장바구니 소유: 주문자의 것이고 주문한 가게의 것이어야 한다.
                                // 같음(eq)으로 묻는다. 다름(ne)으로 물으면 user_id·shop_id가 NULL일 때 비교가 참이 아니어서 통과한다.
                                new CaseBuilder()
                                        .when(cart.userId.eq(ordererId).and(cart.shopId.eq(shopId)))
                                        .then(true)
                                        .otherwise(false).as("validCart")
                        )
                )
                .from(user, shop, cart)
                .where(user.id.eq(ordererId).and(shop.id.eq(shopId)).and(cart.id.eq(cartId)))
                .fetchOne();
    }

}
