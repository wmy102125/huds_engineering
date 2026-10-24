package com.hugo.huds.user.service;

import com.hugo.huds.user.entity.Users;
import com.hugo.huds.user.reponse.UserBriefResponse;
import com.hugo.huds.user.repository.UsersRepository;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Meiyan Wang
 * @date 2026-10-23 11:31 a.m.
 */
@Validated
@Service
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UsersRepository usersRepository;

    public UserService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    /**
     * Looks up for the short details of several users with a single query
     * Ids are null or empty are skipped, So the caller must handle a missing entry.
     *
     * @param uuids the ids of the users to look up, my contains null and duplicate
     * @return the users by id; empty if no id matches
     */
    public Map<UUID, UserBriefResponse> findByUserIds(@NotNull Collection<UUID> uuids) {
        // Drop nulls and duplicates first: findAllById rejects a null element.
        Collection<UUID> ids = uuids.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return usersRepository.findAllById(ids).stream().collect(Collectors.toMap(Users::getUserId, user -> new UserBriefResponse(user.getUserId(), user.getUserName())));
    }
}
