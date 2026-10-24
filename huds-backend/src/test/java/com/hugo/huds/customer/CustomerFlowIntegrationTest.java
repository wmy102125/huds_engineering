package com.hugo.huds.customer;

import com.hugo.huds.address.entity.ServiceAddress;
import com.hugo.huds.customer.entity.Customer;
import com.hugo.huds.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author Meiyan Wang
 * @date 2026-10-18 12:10 p.m.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "huds.auth.ip-failure-limit=1000",
        "huds.auth.max-failed-attempts=5"
})
@AutoConfigureMockMvc
public class CustomerFlowIntegrationTest extends AbstractIntegrationTest {
    private static final String CUSTOMERS = "/api/v1/customers";

    private Map<String, String> validCustomer(UUID uuid) {
        Map<String, String> body = new HashMap<>();
        body.put("email", "it." + uuid + "@example.com");
        body.put("customerName", "Integration Test");
        body.put("phone", "9025550100");
        body.put("notes", "created by integration test");
//        body.put("addressLine1", "1 Test St");
//        body.put("addressLine2", "Unit 2");
//        body.put("city", "Sydney");
//        body.put("province", "NS");
//        body.put("postalCode", "B1P 1A1");
//        body.put("countryCode", "CA");
        return body;
    }

    /**
     * create customer test fields
     **/
//    @Test
//    void createCustomerStoresCustomerAndDefaultAddress() throws Exception {
//        MvcResult result = mockMvc.perform(authed(post(CUSTOMERS))
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(json(validCustomer(UUID.randomUUID()))))
//                .andExpect(status().isCreated())
//                .andExpect(header().exists("Location"))
//                .andExpect(jsonPath("$.customerId").exists())
//                .andReturn();
//        UUID customerId = UUID.fromString(jsonOf(result).get("customerId").asText());
//
//        Long customers = jdbc.queryForObject("select count(*) from customers where customer_id= ?", Long.class, customerId);
//        assertEquals(1, customers);

//        Long serviceAddresses = jdbc.queryForObject("select count(*) from service_addresses where customer_id = ? and is_default = true", Long.class, customerId);
//        assertEquals(1, serviceAddresses);
 //   }

//    @Test
//    void createCustomerDuplicatedEmailReturns409() throws Exception {
//        Map<String, String> body = validCustomer(UUID.randomUUID());
//        mockMvc.perform(authed(post(CUSTOMERS)).contentType(MediaType.APPLICATION_JSON).content(json(body))).andExpect(status().isCreated());
//
//        mockMvc.perform(authed(post(CUSTOMERS)).contentType(MediaType.APPLICATION_JSON).content(json(body))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
//    }

//    @Test
//    void messingAddressLine1Returns400NamingTheField() throws Exception {
//        Map<String, String> body = validCustomer(UUID.randomUUID());
//        body.remove("addressLine1");
//
//        mockMvc.perform(authed(post(CUSTOMERS)).contentType(MediaType.APPLICATION_JSON).content(json(body)))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"))
//                .andExpect(jsonPath("$.fieldViolations[0].field").value("addressLine1"))
//        ;
//    }
//
//    @Test
//    void createWithoutLoginReturns401() throws Exception {
//        mockMvc.perform(withCsrfOnly(post(CUSTOMERS))
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(json(validCustomer(UUID.randomUUID()))))
//                .andExpect(status().isUnauthorized())
//                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
//    }
//
//    @Test
//    void createWithoutCustomerWritePermissionReturns403() throws Exception {
//
//        Cookie technician = loginAs("TECHNICIAN");
//        mockMvc.perform(authed(post(CUSTOMERS), technician).contentType(MediaType.APPLICATION_JSON).content(json(validCustomer(UUID.randomUUID()))))
//                .andExpect(status().isForbidden());
//    }

    /**
     * update customer test fields
     **/

   /* @Test
    void updateCustomerStoresCustomerAndDefaultAddress() throws Exception {
        Map<String, String> body = validCustomer(UUID.randomUUID());
        // create a new customer
        MvcResult result = mockMvc.perform(authed(post(CUSTOMERS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerId").exists())
                .andReturn();
        UUID customerId = UUID.fromString(jsonOf(result).get("customerId").asText());

        //update the customer and address
        body.put("customerId", customerId.toString());
        String uuidStr = UUID.randomUUID().toString();
        body.put("notes", "update test " + uuidStr);
        body.put("addressLine1", "update address line1 " + uuidStr);
        mockMvc.perform(authed(put(CUSTOMERS + "/" + customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        String notes = jdbc.queryForObject("select notes from customers where customer_id= ?", String.class, customerId);
        assertEquals("update test " + uuidStr, notes);

        String addressLine1 = jdbc.queryForObject("select address_line1 from service_addresses where customer_id = ? and is_default = true", String.class, customerId);
        assertEquals("update address line1 " + uuidStr, addressLine1);
    }

    */
    /**
     * find customer tests
     **/
    /*
    @Test
    void findCustomerAndDefaultAddress() throws Exception {
        UUID uuid = UUID.randomUUID();
        Map<String, String> body = validCustomer(uuid);
        // create a new customer
        MvcResult result = mockMvc.perform(authed(post(CUSTOMERS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerId").exists())
                .andReturn();
        UUID customerId = UUID.fromString(jsonOf(result).get("customerId").asText());

        mockMvc.perform(authed(get(CUSTOMERS + "/" + customerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.email").value("it." + uuid.toString() + "@example.com"))
                .andExpect(jsonPath("$.addressLine1").value("1 Test St"));
        ;
    }*/

//    @Test
//    void findCustomerReturns404WhenNotFound() throws Exception {
//        mockMvc.perform(authed(get(CUSTOMERS + "/list" + UUID.randomUUID())))
//                .andExpect(status().isNotFound())
//                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
//        ;
//    }

    /**
     * test if email has already exists
     **/
//    @Test
//    void existsEmailReturnsTrueWhenEmailIsTaken() throws Exception {
//        UUID uuid = UUID.randomUUID();
//        Map<String, String> body = validCustomer(uuid);
//        // create a new customer
//        MvcResult result = mockMvc.perform(authed(post(CUSTOMERS))
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(json(body)))
//                .andExpect(status().isCreated())
//                .andExpect(header().exists("Location"))
//                .andExpect(jsonPath("$.customerId").exists())
//                .andReturn();
//
//        mockMvc.perform(authed(get(CUSTOMERS + "/existsEmail")).param("email", "it." + uuid + "@example.com"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.exists").value(true));
//    }
//
//    @Test
//    void existsEmailReturnsFalseWhenEmailIsFree() throws Exception {
//
//        mockMvc.perform(authed(get(CUSTOMERS + "/existsEmail")).param("email", "nobody." + UUID.randomUUID() + "@example.com"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.exists").value(false));
//    }

}
