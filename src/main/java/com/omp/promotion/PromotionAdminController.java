package com.omp.promotion;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 벤치마크·테스트용 이벤트 관리 API. omp.promotion.admin-api=true일 때만 뜬다(인증이 없으므로 운영에서는 끈다). */
@RestController
@RequestMapping("/api/v1/promotions")
@ConditionalOnProperty(name = "omp.promotion.admin-api", havingValue = "true")
@RequiredArgsConstructor
public class PromotionAdminController {
    private final PromotionAdminService service;

    public record CreateRequest(String name, int totalStock, LocalDateTime startsAt, LocalDateTime endsAt) {}

    @PostMapping
    public Map<String, Long> create(@RequestBody CreateRequest request) {
        return Map.of("promotionId", service.create(request.name(), request.totalStock(), request.startsAt(), request.endsAt()));
    }

    @PostMapping("/{promotionId}/reset")
    public Map<String, Object> reset(@PathVariable long promotionId) {
        service.reset(promotionId);
        return service.check(promotionId);
    }

    @GetMapping("/{promotionId}/check")
    public Map<String, Object> check(@PathVariable long promotionId) {
        return service.check(promotionId);
    }
}
