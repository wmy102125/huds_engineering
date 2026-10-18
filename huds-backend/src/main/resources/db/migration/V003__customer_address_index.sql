-- V003: data-integrity constraints for the customer module.

-- 1. One customer per email, ignoring case. Customers without an email are not constrained.
CREATE UNIQUE INDEX ux_customers_email_lower
    ON customers (lower(email))
    WHERE email IS NOT NULL AND email <> '';

-- 2. Every service address must belong to a customer.
ALTER TABLE service_addresses
    ALTER COLUMN customer_id SET NOT NULL;

-- 3. At most one active default address per customer.
CREATE UNIQUE INDEX ux_service_addresses_one_default
    ON service_addresses (customer_id)
    WHERE is_default = true AND archived_at IS NULL;