package com.omp.review;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "REVIEWS")
@NoArgsConstructor(access = PROTECTED)
public class Review {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "review_id")
    private Long id;
    private String title;
    private Long writerId;
    private Long shopId;
    private String detail;
    private Double rating;

    public Review(final String title, final Long writerId, final Long shopId, final String detail,
                  final Double rating) {
        this.title = title;
        this.shopId = shopId;
        this.detail = detail;
        this.rating = rating;
    }

    public Long getId() {
        return id;
    }
}
