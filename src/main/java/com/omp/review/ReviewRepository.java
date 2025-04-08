package com.omp.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long>, ReviewRepositoryCustom {
    @Query("select count(*) from Review r where r.shop.id = :shopId")
    int countBy(@Param("shopId") Long shopId);


}
