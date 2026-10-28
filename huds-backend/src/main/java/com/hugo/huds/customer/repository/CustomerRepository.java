package com.hugo.huds.customer.repository;

import com.hugo.huds.appliance.entity.Appliance;
import com.hugo.huds.appliance.entity.ApplianceModel;
import com.hugo.huds.customer.entity.Customer;
import com.hugo.huds.user.reponse.UserBriefResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    /** One row of the change feed. Archived customers are included on purpose. */
    interface ChangeRow {
        UUID getCustomerId();
        String getCustomerName();
        String getEmail();
        String getPhone();
        String getNotes();
        Long getRowVersion();
        Instant getCreatedAt();
        UUID getCreatedBy();
        String getCreatedByName();
        Instant getUpdatedAt();
        UUID getUpdatedBy();
        String getUpdatedByName();
        Boolean getArchived();

        /** The creator as one object, or null when the row has no creator. */
        default UserBriefResponse getCreator() {
            return getCreatedBy() == null ? null : new UserBriefResponse(getCreatedBy(), getCreatedByName());
        }

        /** The last editor as one object, or null when the row has no editor. */
        default UserBriefResponse getEditor() {
            return getUpdatedBy() == null ? null : new UserBriefResponse(getUpdatedBy(), getUpdatedByName());
        }
    }
    List<Customer> findByEmailIgnoreCase(String email);

    // True if another customer(any customerId except the given one) already uses this email, ignoring case
    boolean existsByEmailIgnoreCaseAndCustomerIdNot(String email, UUID customerId);

    // Find if the email has already exists
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Finds the id of the active customer that uses this email, ignoring case.
     *
     * @param email the email address
     * @return the id of the existing customer, or empty if there is none
     */
    @Query("select c.customerId from Customer c where lower(c.email) = lower(:email)")
    Optional<UUID> findIdByEmail(@Param("email") String email);

    /**
     * Same as {@link #findIdByEmail}, but ignores the customer with the given id (used when updating).
     */
    @Query("""
            select c.customerId from Customer c
            where lower(c.email) = lower(:email)
              and c.customerId <> :excludeId
            """)
    Optional<UUID> findIdByEmailExcluding(@Param("email") String email, @Param("excludeId") UUID excludeId);

    /**
     * Finds customer by customerName, email, phone.
     *
     * @param customerName the  optional customer name filter
     * @param email        the optional email filter
     * @param phone        the optional phone filter
     * @return matching appliance models
     */
    @Query("""
            select m from Customer m
                        where (:customerName = '' or lower(m.customerName) like lower(concat('%', :customerName, '%')))
                          and (:email = '' or lower(m.email) like lower(concat('%', :email, '%')))
                          and (:phone = '' or lower(m.phone) like lower(concat('%', :phone, '%')))
            """)
    List<Customer> findByFilters(@Param("customerName") String customerName,
                                 @Param("email") String email,
                                 @Param("phone") String phone,
                                 Sort sort);

    /**
     * Finds customer by customerName, email, phone.
     *
     * @param customerName the  optional customer name filter
     * @param email        the optional email filter
     * @param phone        the optional phone filter
     * @return matching appliance models
     */
    @Query("""
            select m from Customer m
                        where (:customerName = '' or lower(m.customerName) like lower(concat('%', :customerName, '%')))
                          and (:email = '' or lower(m.email) like lower(concat('%', :email, '%')))
                          and (:phone = '' or lower(m.phone) like lower(concat('%', :phone, '%')))
            """)
    Page<Customer> findPageByFilters(@Param("customerName") String customerName,
                                     @Param("email") String email,
                                     @Param("phone") String phone,
                                     Pageable pageable);


    /**
     * Customers changed after the cursor (updated_at, customer_id), oldest first.
     * Native SQL is needed because @SQLRestriction on Customer would hide archived rows from JPQL.
     */
    @Query(value = """
            select c.customer_id as customerId,
                   c.customer_name as customerName,
                   c.email as email,
                   c.phone as phone,
                   c.notes as notes,
                   c.row_version as rowVersion,
                   c.created_at as createdAt,
                   c.created_by as createdBy,
                   cu.user_name as createdByName,
                   c.updated_at as updatedAt,
                   c.updated_by as updatedBy,
                   uu.user_name as updatedByName,
                   (c.archived_at is not null) as archived
            from customers c
            left join users cu on cu.user_id = c.created_by
            left join users uu on uu.user_id = c.updated_by
            where (c.updated_at, c.customer_id) > (:since, :sinceId)
            order by c.updated_at, c.customer_id
            limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);
}
