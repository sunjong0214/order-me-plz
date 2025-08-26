package com.omp.menu;

import com.omp.menu.dto.MenuResponse;
import com.omp.menu.dto.UpdateMenuDto;
import java.util.List;
import org.springframework.data.domain.Slice;

public interface MenuRepositoryCustom {
    void updateMenu(UpdateMenuDto dto, Long id);

    Slice<MenuResponse> findMenusBy(int pageSize, Long shopId, Long cursor);

    Long countByIdIn(List<Long> ids);
}
