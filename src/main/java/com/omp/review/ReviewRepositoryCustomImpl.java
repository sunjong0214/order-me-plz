package com.omp.review;

import static com.omp.review.QReview.review;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ReviewRepositoryCustomImpl implements ReviewRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<ReviewDetail> getReviewDetails(final Long shopId) {
        return queryFactory.select(Projections.fields(ReviewDetail.class,
                        review.title,
                        review.detail,
                        review.rating))
                .from(review)
                .where(review.shop.id.eq(shopId))
                .limit(5)
                .offset(0)
                .fetch();
    }
}
