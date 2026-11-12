package com.hugo.huds.visit.dto;

/**
 *  * Kind of a visit, as on the diagnostic form.
 *  *
 *  * <p>The values must match the CHECK constraint on {@code service_visits.visit_kind}.</p>
 *
 * @author Meiyan Wang
 * @date 2026-11-10 10:34 a.m.
 */
public enum VisitKind {
    /** The first visit of the case. */
    FIRST,

    /** A later visit of the same case, for example to fit ordered parts. */
    FOLLOW_UP,

    /** The customer called back after an earlier repair. */
    RECALL
}
