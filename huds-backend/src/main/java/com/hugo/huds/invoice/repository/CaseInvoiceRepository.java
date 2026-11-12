package com.hugo.huds.invoice.repository;

import com.hugo.huds.invoice.entity.CaseInvoice;
import com.hugo.huds.user.reponse.UserBriefResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseInvoiceRepository extends JpaRepository<CaseInvoice, UUID> {

    /**
     * One row of the change feed, including the amounts the database works out.
     * A bill is never archived, so there is no archived flag.
     */
    interface ChangeRow {
        UUID getCaseInvoiceId();

        UUID getDiagnosticCaseId();

        String getInvoiceStatus();

        Instant getIssuedAt();

        BigDecimal getServiceCallFee();

        BigDecimal getLabourFee();

        BigDecimal getPartsFee();

        BigDecimal getDiscountAmount();

        BigDecimal getTaxRate();

        BigDecimal getSubtotalAmount();

        BigDecimal getTaxAmount();

        BigDecimal getTotalAmount();

        Instant getCreatedAt();

        Instant getUpdatedAt();

        UUID getCreatedBy();

        String getCreatedByName();

        UUID getUpdatedBy();

        String getUpdatedByName();

        Long getRowVersion();

        default UserBriefResponse getCreator() {
            return getCreatedBy() == null || getCreatedByName() == null
                    ? null : new UserBriefResponse(getCreatedBy(), getCreatedByName());
        }

        default UserBriefResponse getEditor() {
            return getUpdatedBy() == null || getUpdatedByName() == null
                    ? null : new UserBriefResponse(getUpdatedBy(), getUpdatedByName());
        }
    }

    /** The bill of a case, if any (one per case). */
    Optional<CaseInvoice> findByDiagnosticCaseId(UUID diagnosticCaseId);

    /**
     * Bills changed after the cursor (updated_at, case_invoice_id), oldest first.
     */
    @Query(value = """
            select i.case_invoice_id    as caseInvoiceId,
                   i.diagnostic_case_id as diagnosticCaseId,
                   i.invoice_status     as invoiceStatus,
                   i.issued_at          as issuedAt,
                   i.service_call_fee   as serviceCallFee,
                   i.labour_fee         as labourFee,
                   i.parts_fee          as partsFee,
                   i.discount_amount    as discountAmount,
                   i.tax_rate           as taxRate,
                   i.subtotal_amount    as subtotalAmount,
                   i.tax_amount         as taxAmount,
                   i.total_amount       as totalAmount,
                   i.created_at         as createdAt,
                   i.updated_at         as updatedAt,
                   i.created_by         as createdBy,
                   cu.user_name         as createdByName,
                   i.updated_by         as updatedBy,
                   uu.user_name         as updatedByName,
                   i.row_version        as rowVersion
              from case_invoices i
              left join users cu on cu.user_id = i.created_by
              left join users uu on uu.user_id = i.updated_by
             where (i.updated_at, i.case_invoice_id) > (:since, :sinceId)
             order by i.updated_at, i.case_invoice_id
             limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);
}
