package com.omp.menu;


import static com.omp.menu.QMenu.menu;
import static org.springframework.util.StringUtils.hasText;

import com.omp.menu.dto.UpdateMenuDto;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MenuRepositoryCustomImpl implements MenuRepositoryCustom {
    private final JPAQueryFactory queryFactory;

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
