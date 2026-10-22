package com.hugo.huds.sync.repository;

import com.hugo.huds.sync.entity.SyncMutation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SyncMutationRepository extends JpaRepository<SyncMutation, UUID> {

    /**
     *
     * @param clientMutationId The front end mutation id
     * @return
     */
    Optional<SyncMutation> findByActorUserIdAndClientMutationId(UUID actorUserId,UUID clientMutationId);
}
