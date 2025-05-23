package com.omp.orderMenu;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderMenuRepository extends JpaRepository<OrderMenu, Long> {
    @Query("select om from OrderMenu om where om.cartId = :cartId")
    List<OrderMenu> findAllByCartId(@Param(value = "cartId") Long cartId);
}
