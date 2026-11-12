package com.hugo.huds.visit.completion.dto;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Body of a VISIT_COMPLETION CREATE or UPDATE mutation: section 5 of the diagnostic form.
 * The visit of a record never changes, so on UPDATE it must match the stored row.
 *
 * <p>The three status fields are replaced as sent (empty means "not recorded"). The checks object is defined by
 * the front end; the server only checks that it is a JSON object of a reasonable size. An empty checks object
 * means "empty object" on create and "unchanged" on update.</p>
 */
public record VisitCompletionRequest(
        @NotNull(message = "ServiceVisitId is required")
        UUID serviceVisitId,
        /* VERIFICATION READINGS / TEST CYCLE / SAFETY CHECK: check code -> true when checked. */
        JsonNode verificationChecks,
        /* FINAL STATUS of the appliance when the technician leaves. */
        FinalStatus finalStatus,
        FixOutcome fixOutcome,
        GoogleReviewStatus googleReviewStatus
) {
}
