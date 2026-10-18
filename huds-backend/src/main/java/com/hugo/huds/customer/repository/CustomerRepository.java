package com.hugo.huds.customer.repository;

import com.hugo.huds.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByEmailIgnoreCase(String email);

    // True if another customer(any customerId except the given one) already uses this email, ignoring case
    boolean existsByEmailIgnoreCaseAndCustomerIdNot(@Param("email") String email, @Param("customerId") UUID customerId);

    // find if the email has already exists
    boolean existsByEmailIgnoreCase(String email);

}
