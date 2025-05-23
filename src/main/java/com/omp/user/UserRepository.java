package com.omp.user;

import jakarta.validation.constraints.Positive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("select u.status from User u where u.id = :userId")
    UserStatus findStatusById(@Param(value = "userId") @Positive Long ordererId);
}
