package com.omp.menu;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class MenuService {
    private final MenuRepository menuRepository;

    public Menu findMenuBy(final Long id) {
        return menuRepository.findById(id).orElseThrow();
    }

    public Long saveMenuBy(final CreateMenuDto createMenuDto) {
        return menuRepository.save(CreateMenuDto.of(createMenuDto)).getId();
    }
}
