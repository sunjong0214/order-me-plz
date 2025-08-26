package com.omp.menu;


import static com.omp.menu.QMenu.menu;
import static org.springframework.util.StringUtils.hasText;

import com.omp.menu.dto.MenuResponse;
import com.omp.menu.dto.UpdateMenuDto;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

@RequiredArgsConstructor
public class MenuRepositoryCustomImpl implements MenuRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public Slice<MenuResponse> findMenusBy(int pageSize, Long shopId, Long cursor) {
        List<MenuResponse> menus = queryFactory.select(Projections.fields(MenuResponse.class,
                        menu.id,
                        menu.name,
                        menu.price,
                        menu.isSoldOut,
                        menu.description
                )).from(menu)
                .where(menu.id.lt(cursor).and(menu.shopId.eq(shopId)))
                .limit(pageSize)
                .fetch();

        boolean hasNext = false;
        if (menus.size() > pageSize) {
            hasNext = true;
            menus.removeLast();
        }

        return new SliceImpl<>(menus, PageRequest.ofSize(pageSize), hasNext);
    }

    @Override
    public Long countByIdIn(List<Long> ids) {
        return queryFactory.select(menu.count())
                .from(menu)
                .where(menu.id.in(ids))
                .fetchOne();
    }

    @Override
    public void updateMenu(final UpdateMenuDto dto, final Long id) {
        JPAUpdateClause updateClause = queryFactory.update(menu)
                .where(menu.id.eq(id));

        if (hasText(dto.name())) {
            updateClause.set(menu.name, dto.name());
        }

        if (hasText(dto.description())) {
            updateClause.set(menu.description, dto.description());
        }

        if (dto.price() != null) {
            updateClause.set(menu.price, dto.price());
        }

        if (dto.quantity() != null) {
            updateClause.set(menu.quantity, dto.quantity());
        }

        if (dto.isSoldOut() != null) {
            updateClause.set(menu.isSoldOut, dto.isSoldOut());
        }

        updateClause.execute();
    }
}
