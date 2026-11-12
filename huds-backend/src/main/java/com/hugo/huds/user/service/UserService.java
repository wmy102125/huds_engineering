package com.hugo.huds.user.service;

import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.user.dto.UserResponse;
import com.hugo.huds.user.entity.User;
import com.hugo.huds.user.reponse.UserBriefResponse;
import com.hugo.huds.user.repository.UserRepository;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
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

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getUserId, user -> new UserBriefResponse(user.getUserId(), user.getUserName())));
    }

    public SyncPullResponse fetchChanges(Instant since, UUID sinceId, int limit) {
        List<UserRepository.UserRow> rows = userRepository.findUserChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }
        boolean hasMore = rows.size() > limit;
        List<UserRepository.UserRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<UserResponse> responses = page.stream()
                .map(row -> new UserResponse(row.getUserId(), row.getUserName(), row.getRole(), Boolean.TRUE.equals(row.getActive())))
                .toList();
        UserRepository.UserRow last = page.get(page.size() - 1);
        return new SyncPullResponse(responses, last.getUpdatedAt(), last.getUserId(), hasMore);
    }
}
