package com.hugo.huds.user.repository;

import com.hugo.huds.user.entity.SysUsers;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for accessing application users stored in {@code sys_users}.
 */
public interface SysUsersRepository extends JpaRepository<SysUsers, UUID> {

    Optional<SysUsers> findByEmailIgnoreCase(String email);
}

