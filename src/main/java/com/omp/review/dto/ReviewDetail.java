package com.omp.review.dto;

import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReviewDetail {
    private String title;
    private String detail;
    private BigDecimal rating;
}
