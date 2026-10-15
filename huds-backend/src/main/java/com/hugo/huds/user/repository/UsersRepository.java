package com.hugo.huds.user.repository;

import com.hugo.huds.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for accessing application users stored in {@code sys_users}.
 */
public interface UsersRepository extends JpaRepository<Users, UUID> {

    Optional<Users> findByEmailIgnoreCase(String email);
}

