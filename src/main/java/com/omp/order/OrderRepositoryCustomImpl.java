package com.omp.order;

import static com.omp.cart.QCart.cart;
import static com.omp.shop.QShop.shop;
import static com.omp.user.QUser.user;

import com.omp.cart.Cart;
import com.omp.shop.Shop;
import com.omp.user.User;
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
                                new CaseBuilder()
                                        .when(cart.isNull()
//                                                .or(cart.shopId.ne(shopId)).or(cart.userId.ne(ordererId))
                                        )
                                        .then(false)
                                        .otherwise(true).as("validCart")
                        )
                )
                .from(user, shop, cart)
                .where(user.id.eq(ordererId).and(shop.id.eq(shopId)).and(cart.id.eq(cartId)))
                .fetchOne();
    }

}
