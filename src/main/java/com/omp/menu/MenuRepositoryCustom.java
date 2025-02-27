package com.omp.menu;

import com.omp.menu.dto.UpdateMenuDto;

public interface MenuRepositoryCustom {
    void updateMenu(UpdateMenuDto dto, Long id);
}
