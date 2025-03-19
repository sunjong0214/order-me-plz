package com.omp.shop;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopRepository extends JpaRepository<Shop, Long>, ShopRepositoryCustom {
    @Lock(LockModeType.OPTIMISTIC)
    @Query("select s from Shop s where s.id = :shopId")
    Optional<Shop> findByIdWithOptimisticLock(@Param("shopId") Long id);
}
