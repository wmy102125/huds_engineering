package com.hugo.huds.diagnosticcase.service;

import com.hugo.huds.diagnosticcase.dto.CaseStateEventResponse;
import com.hugo.huds.diagnosticcase.dto.WorkflowStatus;
import com.hugo.huds.diagnosticcase.repository.CaseStateEventRepository;
import com.hugo.huds.sync.dto.SyncPullResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-29 2:10 p.m.
 */
@Service
public class CaseStateEventService {
    private final CaseStateEventRepository caseStateEventRepository;

    public CaseStateEventService(CaseStateEventRepository caseStateEventRepository) {
        this.caseStateEventRepository = caseStateEventRepository;
    }

    /**
     * Searches the change rows by since, sinceId and limit.
     *
     * @param since   the change rows start time
     * @param sinceId the change rows start id
     * @param limit   this search batch max size number
     * @return matching rows
     */
    public SyncPullResponse fetchChanges(Instant since, UUID sinceId, int limit) {
        List<CaseStateEventRepository.ChangeRow> rows = caseStateEventRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<CaseStateEventRepository.ChangeRow> page = hasMore ? rows.subList(0, limit) : rows;

        List<CaseStateEventResponse> detailResponses = page.stream().map(changeRow -> {
            return new CaseStateEventResponse(
                    changeRow.getCaseStateEventId(),
                    changeRow.getDiagnosticCaseId(),
                    changeRow.getFromState() == null ? null : WorkflowStatus.valueOf(changeRow.getFromState()),
                    changeRow.getToState()== null ? null : WorkflowStatus.valueOf(changeRow.getToState()),
                    changeRow.getReason(),
                    changeRow.getActor(),
                    changeRow.getOccurredAt(),
                    changeRow.getClientRecordedAt()
            );
        }).toList();
        CaseStateEventRepository.ChangeRow last = page.get(page.size() - 1);
        Instant nextSince = last.getOccurredAt();
        UUID nextSinceId = last.getCaseStateEventId();
        return new SyncPullResponse(detailResponses, nextSince, nextSinceId, hasMore);
    }
}
