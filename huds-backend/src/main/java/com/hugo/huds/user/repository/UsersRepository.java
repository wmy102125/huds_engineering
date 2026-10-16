package com.hugo.huds.user.repository;

import com.hugo.huds.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for accessing application users stored in {@code sys_users}.
 */
public interface UsersRepository extends JpaRepository<Users, UUID> {

    Optional<Users> findByEmailIgnoreCase(String email);

    @Modifying(clearAutomatically = true,flushAutomatically = true)
    @Query("UPDATE Users u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.userId = :userId")
    int incrementFailedLoginAttempts(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true,flushAutomatically = true)
    @Query("UPDATE Users u SET u.lockedUntil=:lockedUntil , u.failedLoginAttempts = 0 WHERE u.userId = :userId")
    int lockUntil(@Param("userId") UUID userId, @Param("lockedUntil") Instant lockedUntil);

    @Modifying(clearAutomatically = true,flushAutomatically = true)
    @Query("UPDATE Users u SET u.failedLoginAttempts = 0 ,u.lockedUntil = null WHERE u.userId = :userId")
    int clearFailures(@Param("userId") UUID userId);
}

