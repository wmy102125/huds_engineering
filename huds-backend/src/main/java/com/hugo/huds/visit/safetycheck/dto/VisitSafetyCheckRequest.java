package com.hugo.huds.visit.safetycheck.dto;

import tools.jackson.databind.JsonNode;

/**
 * Body of a VISIT_SAFETY_CHECK CREATE or UPDATE mutation: section 2 of the diagnostic form.
 * The visit is identified by the entityId of the mutation, so it is not repeated in the body.
 *
 * <p>The content of the three objects is defined by the front end (it depends on the appliance and the repair).
 * The server only checks that each one is a JSON object of a reasonable size.
 * An empty value means "empty object" on create and "unchanged" on update.</p>
 */
public record VisitSafetyCheckRequest(
        /* SAFETY / ACCESS CHECKS. */
        JsonNode safetyChecks,
        /* UTILITY BASELINE: supply type and voltages. */
        JsonNode utilityBaseline,
        /* ENVIRONMENT BASELINE: water, gas and ambient notes. */
        JsonNode environmentBaseline
) {
}
