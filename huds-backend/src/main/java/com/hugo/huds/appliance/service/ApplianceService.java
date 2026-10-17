package com.hugo.huds.appliance.service;

import com.hugo.huds.address.repository.ServiceAddressRepository;
import com.hugo.huds.appliance.repository.ApplianceLocationHistoryRepository;
import com.hugo.huds.appliance.repository.ApplianceRepository;
import com.hugo.huds.common.response.CommonResponse;
import com.hugo.huds.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;

/**
 * @author Meiyan Wang
 * @since 2024-06-19
 * @description Service class for managing appliances. This class will contain business logic related to appliance operations, such as adding, updating, deleting, and retrieving appliance information.
 */
@Service
public class ApplianceService {

    private  final CustomerRepository customerRepository;
    private final ServiceAddressRepository addressRepository;
    private final ApplianceRepository applianceRepository;
    private final ApplianceLocationHistoryRepository locationHistoryRepository;

    public ApplianceService(CustomerRepository customerRepository, ServiceAddressRepository addressRepository, ApplianceRepository applianceRepository, ApplianceLocationHistoryRepository locationHistoryRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.applianceRepository = applianceRepository;
        this.locationHistoryRepository = locationHistoryRepository;
    }

    public CommonResponse addAppliance() {
        // Implement the logic to add an appliance
        // Validate the input, check for duplicates, and save to the database
        return CommonResponse.buildSuccessResponse();
    }


}
