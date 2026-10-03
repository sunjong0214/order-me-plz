package com.omp.menu;

import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

import com.omp.menu.dto.ChangeMenuRequest;
import com.omp.menu.dto.CreateMenuRequest;
import com.omp.menu.dto.MenuResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/menu")
public class MenuController {
    private final MenuService menuService;

    @GetMapping("/{id}")
    public Menu getMenu(final @PathVariable Long id) {
        return menuService.findMenuBy(id);
    }

    @PostMapping
    public Long createMenu(final @RequestBody CreateMenuRequest request) {
        return menuService.saveMenuBy(CreateMenuRequest.from(request));
    }

    @PatchMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void changeMenuInfo(final @PathVariable Long id, final @RequestBody ChangeMenuRequest request) {
        menuService.updateMenuBy(ChangeMenuRequest.from(request), id);
    }

    /**
     * 가게의 메뉴 목록(커서 페이지, menu_id 내림차순). 첫 페이지는 cursor 없이, 다음 페이지는 이전 페이지의 마지막 id를 cursor로.
     */
    @GetMapping
    @ResponseStatus(OK)
    public Slice<MenuResponse> getMenus(final @RequestParam(required = false) Long cursor, final @RequestParam int pageSize,
                                        final @RequestParam Long shopId) {
        return menuService.findMenusBy(pageSize, shopId, cursor);
    }
}