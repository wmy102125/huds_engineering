package com.hugo.huds.customer.repository;

import com.hugo.huds.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByEmailIgnoreCase(String email);

    // True if another customer(any customerId except the given one) already uses this email, ignoring case
    boolean existsByEmailIgnoreCaseAndCustomerIdNot(String email,UUID customerId);

    // Find if the email has already exists
    boolean existsByEmailIgnoreCase(String email);
}
