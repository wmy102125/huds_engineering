package com.hugo.huds.sync.service;

import com.hugo.huds.sync.dto.SyncPushStatus;
import com.hugo.huds.sync.entity.SyncMutation;
import com.hugo.huds.sync.repository.SyncMutationRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Decides whether a mutation may run, based on the outcome of the mutations it depends on.
 *
 * @author Meiyan Wang
 * @date 2026-10-24 9:31 p.m.
 */
@Component
public class SyncDependencyChecker {

    private final SyncMutationRepository syncMutationRepository;

    public SyncDependencyChecker(SyncMutationRepository syncMutationRepository) {
        this.syncMutationRepository = syncMutationRepository;
    }

    /**
     * Finds the dependencies of a mutation that did not succeed.
     *
     * @param actorUserId  the user who sent the batch
     * @param dependsOn    the client mutation ids this mutation depends on (never null, may be empty)
     * @param batchResults the outcomes of the mutations already processed in this batch
     * @return the ids of the dependencies that are not accepted; empty means the mutation may run
     */
    public List<UUID> blockingDependencies(UUID actorUserId, List<UUID> dependsOn,
                                           Map<UUID, SyncPushStatus> batchResults) {
        if (dependsOn.isEmpty()) {
            return List.of();
        }

        // Outcomes from earlier batches. Only ACCEPTED and REJECTED are ever stored.
        Map<UUID, String> storedResults = syncMutationRepository
                .findByActorUserIdAndClientMutationIdIn(actorUserId, dependsOn).stream()
                .collect(Collectors.toMap(SyncMutation::getClientMutationId, SyncMutation::getStatus));

        return dependsOn.stream()
                .filter(dependencyId -> {
                    // A dependency in this batch was processed earlier, so its outcome is in the map.
                    SyncPushStatus batchStatus = batchResults.get(dependencyId);
                    if (batchStatus != null) {
                        return batchStatus != SyncPushStatus.ACCEPTED;
                    }
                    // Otherwise it must have been processed in an earlier batch. Unknown ids block too.
                    return !SyncPushStatus.ACCEPTED.name().equals(storedResults.get(dependencyId));
                })
                .toList();
    }
}