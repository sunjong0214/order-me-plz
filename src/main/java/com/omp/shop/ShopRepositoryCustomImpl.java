package com.omp.shop;


import static com.omp.shop.QShop.shop;

import com.omp.shop.dto.ShopInfo;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

@RequiredArgsConstructor
public class ShopRepositoryCustomImpl implements ShopRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public Slice<ShopInfo> findBy(final ShopCategory category, final Long cursor, final int pageSize) {
        List<ShopInfo> shops = queryFactory.select(
                        Projections.constructor(ShopInfo.class,
                                shop.id.as("shopId"),
                                shop.name,
                                shop.isOpen))
                .from(shop)
                .where(shop.category.eq(category))
                .where(checkCursor(cursor))
                .limit(pageSize + 1)
                .fetch();

        boolean hasNext = false;
        if (shops.size() > pageSize) {
            hasNext = true;
            shops.remove(shops.removeLast());
        }

        PageRequest pageRequest = PageRequest.ofSize(pageSize);
        return new SliceImpl<>(shops, pageRequest, hasNext);
    }

    private static BooleanExpression checkCursor(Long cursor) {
        return cursor == null ? null : shop.id.lt(cursor);
    }
}
