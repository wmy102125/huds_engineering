package com.hugo.huds.invoice.repository;

import com.hugo.huds.invoice.entity.CaseInvoicePayment;
import com.hugo.huds.user.reponse.UserBriefResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CaseInvoicePaymentRepository extends JpaRepository<CaseInvoicePayment, UUID> {

    /**
     * One row of the change feed. A payment is never archived, so there is no archived flag.
     */
    interface ChangeRow {
        UUID getCaseInvoicePaymentId();

        UUID getCaseInvoiceId();

        BigDecimal getAmount();

        String getPaymentMethod();

        String getReferenceNo();

        Instant getReceivedAt();

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

    /**
     * Payments changed after the cursor (updated_at, case_invoice_payment_id), oldest first.
     */
    @Query(value = """
            select p.case_invoice_payment_id as caseInvoicePaymentId,
                   p.case_invoice_id         as caseInvoiceId,
                   p.amount                  as amount,
                   p.payment_method          as paymentMethod,
                   p.reference_no            as referenceNo,
                   p.received_at             as receivedAt,
                   p.created_at              as createdAt,
                   p.updated_at              as updatedAt,
                   p.created_by              as createdBy,
                   cu.user_name              as createdByName,
                   p.updated_by              as updatedBy,
                   uu.user_name              as updatedByName,
                   p.row_version             as rowVersion
              from case_invoice_payments p
              left join users cu on cu.user_id = p.created_by
              left join users uu on uu.user_id = p.updated_by
             where (p.updated_at, p.case_invoice_payment_id) > (:since, :sinceId)
             order by p.updated_at, p.case_invoice_payment_id
             limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);
}
