package za.co.carddisputeportalservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import za.co.capitecbank.service.CustomerService;
import za.co.carddisputeportalservice.generated.model.AccountType;
import za.co.carddisputeportalservice.generated.model.CustomerType;
import za.co.capitecbank.mapper.PortalMapper;
import za.co.capitecbank.model.Account;
import za.co.capitecbank.model.Customer;
import za.co.capitecbank.repository.AccountRepository;
import za.co.capitecbank.security.CurrentCustomer;

import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {
    @Mock CurrentCustomer current;
    @Mock AccountRepository accounts;

    @Test
    void returnsTheAuthenticatedCustomersProfileAndOwnedAccountSummaries() {
        var customer =
                new Customer(
                        UUID.randomUUID(),
                        CustomerType.INDIVIDUAL,
                        "Shannon Weir",
                        "contact@example.com");
        var account =
                new Account(
                        UUID.randomUUID(),
                        AccountType.CREDIT_CARD,
                        customer,
                        "Credit account",
                        "•••• 1234",
                        "ZAR");
        when(current.customer()).thenReturn(customer);
        when(accounts.findByCustomerIdOrderByDisplayNameAsc(customer.getId()))
                .thenReturn(List.of(account));
        var result = service().getCurrentCustomer();
        assertThat(result.getId()).isEqualTo(customer.getId());
        assertThat(result.getDisplayName()).isEqualTo("Shannon Weir");
        assertThat(result.getAccounts())
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.getId()).isEqualTo(account.getId());
                            assertThat(item.getType()).isEqualTo(AccountType.CREDIT_CARD);
                            assertThat(item.getMaskedNumber()).isEqualTo("•••• 1234");
                        });
    }

    @Test
    void customersWithoutAccountsReceiveAnEmptyAccountList() {
        var customer =
                new Customer(UUID.randomUUID(), CustomerType.BUSINESS, "Example Business", null);
        when(current.customer()).thenReturn(customer);
        when(accounts.findByCustomerIdOrderByDisplayNameAsc(customer.getId()))
                .thenReturn(List.of());
        assertThat(service().getCurrentCustomer().getAccounts()).isEmpty();
    }

    private CustomerService service() {
        return new CustomerService(current, accounts, Mappers.getMapper(PortalMapper.class));
    }
}
