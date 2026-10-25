package com.hugo.huds.appliance.dto;

import com.hugo.huds.address.dto.ServiceAddressResponse;

import java.util.UUID;

/**
 * @author Meiyan Wang
 * @date 2026-10-24 9:42 a.m.
 * appliance_service_address_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 *                                              appliance_id uuid NOT NULL REFERENCES appliances(appliance_id) ON DELETE RESTRICT,
 *                                              service_address_id uuid NOT NULL REFERENCES service_addresses(service_address_id) ON DELETE RESTRICT,
 *                                              note text,
 */
public record ApplianceServiceAddressDetailResponse(
        UUID applianceId,
        UUID applianceServiceAddressId,
        String node,
        Long rowVersion,
        ServiceAddressResponse serviceAddressResponse

) {
}
