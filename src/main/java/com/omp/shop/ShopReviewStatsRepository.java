package com.omp.shop;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopReviewStatsRepository extends JpaRepository<ShopReviewStats, Long> {

    // 이전 값을 읽지 않는 단일 원자 UPDATE. 증가 연산은 실행 순서와 무관하게 결과가 같으므로 동시 갱신의 덮어쓰기가 없다
    // (이벤트 누락·중복 적용까지 막는 것은 아니다). 리뷰 INSERT의 FK 검사가 S락을 잡는 shops 행과 다른 행을 갱신하므로
    // 기존의 같은 행 S락→X락 승격 경로도 없다. stats 행 자체의 락은 사용한다.
    //
    // 대입 순서 주의: MySQL은 단일 테이블 UPDATE의 SET 절을 왼쪽부터 평가하고, 뒤의 대입은 갱신된 값을 본다.
    // 평균을 맨 앞에 두어 (원래 합계 + r) / (원래 개수 + 1)로 계산되게 한다. 뒤에 두면 증가분이 두 번 반영된다.
    @Modifying
    @Query("update ShopReviewStats s set "
            + "s.averageRating = (s.ratingSum + :rating) / (s.reviewCount + 1), "
            + "s.ratingSum = s.ratingSum + :rating, "
            + "s.reviewCount = s.reviewCount + 1 "
            + "where s.shopId = :shopId")
    int applyReviewRating(@Param("shopId") Long shopId, @Param("rating") BigDecimal rating);
}
