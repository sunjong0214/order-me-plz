package com.omp.menu;

import com.omp.menu.dto.CreateMenuRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/menu")
public class MenuController {
    private final MenuService menuService;

    @GetMapping("/{id}")
    public Menu getMenu(final Long id) {
        return menuService.findMenuBy(id);
    }

    @PostMapping
    public Long createMenu(final CreateMenuRequest request) {
        return menuService.saveMenuBy(CreateMenuRequest.from(request));
    }
}
