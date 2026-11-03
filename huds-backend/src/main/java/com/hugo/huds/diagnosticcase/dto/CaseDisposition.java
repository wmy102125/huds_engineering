package com.hugo.huds.diagnosticcase.dto;

/**
 * @author Meiyan Wang
 * @date 2026-10-28 12:22 p.m.
 */
public enum CaseDisposition {
    FIXED_VERIFIED,
    UNRESOLVED,
    CUSTOMER_DECLINED,
    BER,
    UNSAFE,
    NO_FAULT_FOUND,
    REFERRED,
    /** Set by the server when a case is cancelled. A client cannot choose it when closing. */
    CANCELLED
}
