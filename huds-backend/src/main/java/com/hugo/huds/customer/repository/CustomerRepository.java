package com.hugo.huds.customer.repository;

import com.hugo.huds.appliance.entity.ApplianceModel;
import com.hugo.huds.customer.entity.Customer;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByEmailIgnoreCase(String email);

    // True if another customer(any customerId except the given one) already uses this email, ignoring case
    boolean existsByEmailIgnoreCaseAndCustomerIdNot(String email, UUID customerId);

    // Find if the email has already exists
    boolean existsByEmailIgnoreCase(String email);

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
}
