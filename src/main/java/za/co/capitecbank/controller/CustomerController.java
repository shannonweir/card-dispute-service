package za.co.capitecbank.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import za.co.carddisputeportalservice.generated.api.CustomerApi;
import za.co.carddisputeportalservice.generated.model.CustomerProfile;
import za.co.capitecbank.service.CustomerService;

@RestController
@RequiredArgsConstructor
public class CustomerController implements CustomerApi {

    private final CustomerService customers;

    @Override
    public ResponseEntity<CustomerProfile> getCurrentCustomer() {
        return ResponseEntity.ok(customers.getCurrentCustomer());
    }
}
