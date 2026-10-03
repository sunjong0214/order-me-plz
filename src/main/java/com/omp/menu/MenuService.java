package com.omp.menu;

import com.omp.menu.dto.CreateMenuDto;
import com.omp.menu.dto.MenuResponse;
import com.omp.menu.dto.UpdateMenuDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MenuService {
    private final MenuRepository menuRepository;
    private final MenuRepositoryCustom menuRepositoryCustomImpl;

    public Menu findMenuBy(final Long id) {
        return menuRepository.findById(id).orElseThrow();
    }

    public Long saveMenuBy(final CreateMenuDto createMenuDto) {
        return menuRepository.save(CreateMenuDto.from(createMenuDto)).getId();
    }

    // QueryDSL 벌크 UPDATE는 트랜잭션 없이 실행하면 TransactionRequiredException으로 실패한다
    @Transactional
    public void updateMenuBy(final UpdateMenuDto dto, final Long id) {
        menuRepositoryCustomImpl.updateMenu(dto, id);
    }

    public Slice<MenuResponse> findMenusBy(int pageSize, Long shopId, Long cursor) {
        return menuRepository.findMenusBy(pageSize, shopId, cursor);
    }
}
