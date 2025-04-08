package com.omp.shop.dto;

import com.omp.review.ReviewDetail;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ShopInfo {
    private Long shopId;
    private String name;
    private boolean isOpen;
    private BigDecimal averageRating;
    private List<ReviewDetail> recentlyReviewDetail;
}
