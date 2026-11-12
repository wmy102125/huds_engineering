package com.hugo.huds.visit.completion.dto;

/**
 * Where the request for a Google review stands. A record without a value has not recorded it yet.
 */
public enum GoogleReviewStatus {
    NOT_REQUESTED,
    PENDING,
    REQUESTED,
    COMPLETED,
    DECLINED
}
