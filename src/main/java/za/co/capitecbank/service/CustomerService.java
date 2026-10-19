package za.co.capitecbank.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.capitecbank.mapper.CardDisputeMapper;
import za.co.carddisputeportalservice.generated.model.CustomerProfile;
import za.co.capitecbank.repository.AccountRepository;
import za.co.capitecbank.security.CurrentCustomer;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CurrentCustomer currentCustomer;
    private final AccountRepository accountRepository;
    private final CardDisputeMapper cardDisputeMapper;

    public CustomerProfile getCurrentCustomer() {
        var customer = currentCustomer.customer();
        var accounts = accountRepository.findByCustomerIdOrderByDisplayNameAsc(customer.getId());
        return cardDisputeMapper.map(customer, accounts);
    }
}
