package com.hugo.huds.diagnosticcase.service;

import com.hugo.huds.address.entity.ServiceAddress;
import com.hugo.huds.address.repository.ServiceAddressRepository;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the address snapshot stored on a case: a copy of the service address as it is when the case is opened.
 * The client never sends it; the server reads the address itself.
 *
 * @author Meiyan Wang
 * @date 2026-10-28 7:16 p.m.
 */
@Service
public class AddressSnapshotFactory {
    private final ServiceAddressRepository serviceAddressRepository;
    private final ObjectMapper objectMapper;

    public AddressSnapshotFactory(ServiceAddressRepository serviceAddressRepository, ObjectMapper objectMapper) {
        this.serviceAddressRepository = serviceAddressRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the snapshot as JSON text.
     *
     * @throws ApplicationException INVALID_PAYLOAD when the address does not exist or belongs to another customer
     */
    public String create(UUID serviceAddressId, UUID customerId) {
        ServiceAddress address = serviceAddressRepository.findById(serviceAddressId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The service address does not exist."));
        if (!address.getCustomerId().equals(customerId)) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The service address does not belong to this customer.");
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("addressLine1", address.getAddressLine1());
        snapshot.put("addressLine2", address.getAddressLine2());
        snapshot.put("city", address.getCity());
        snapshot.put("province", address.getProvince());
        snapshot.put("postalCode", address.getPostalCode());
        snapshot.put("countryCode", address.getCountryCode());
        return objectMapper.writeValueAsString(snapshot);
    }
}
