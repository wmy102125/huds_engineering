package com.hugo.huds.sync.repository;

import com.hugo.huds.sync.dto.SyncPushStatus;
import com.hugo.huds.sync.entity.SyncMutation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncMutationRepository extends JpaRepository<SyncMutation, UUID> {

    /**
     *
     * @param clientMutationId The front end mutation id
     * @return
     */
    Optional<SyncMutation> findByActorUserIdAndClientMutationId(UUID actorUserId,UUID clientMutationId);
    /**
     * Finds the already processed mutations of one user among the given client mutation ids.
     *
     * @param actorUserId       the user who sent the mutations
     * @param clientMutationIds the client generated mutation ids to look up
     * @return the mutations that were processed before
     */
    List<SyncMutation> findByActorUserIdAndClientMutationIdIn(UUID actorUserId, Collection<UUID> clientMutationIds);

    /**
     * Finds mutation status by actorUserId and clientMutationId.
     * All are required filters.
     *
     * @param actorUserId       the user who sent the mutations
     * @param clientMutationId the client generated mutation id to look up
     * @return the status of this mutation record
     */
    @Query("""
            select m.status from SyncMutation m
            where  m.actorUserId = :actorUserId
              and  m.clientMutationId = :clientMutationId
            """)
    Optional<SyncPushStatus> findStatus(@Param("actorUserId") UUID actorUserId, @Param("clientMutationId")UUID clientMutationId);

}
