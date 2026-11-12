package com.hugo.huds.customer.dto;

/**
 * Account type of a customer: who the customer is and who pays.
 *
 *  <p>The values must match the CHECK constraint on {@code customers.customer_type}.</p>
 * @author Meiyan Wang
 * @date 2026-11-10 10:29 a.m.
 */
public enum CustomerType {
    /** Normal private customer who pays on the spot. */
    RETAIL_COD,

    /** Business, property manager or landlord. */
    PROPERTY_LANDLORD,

    /** The repair is paid by a third-party warranty company. */
    THIRD_PARTY_WARRANTY,

    /** The repair is paid by the manufacturer (OEM / factory). */
    OEM_FACTORY
}
