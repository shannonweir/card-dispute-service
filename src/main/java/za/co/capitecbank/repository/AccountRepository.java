package za.co.capitecbank.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import za.co.capitecbank.model.Account;

import java.util.List;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByIdAndCustomerId(final UUID id, final UUID customerId);

    List<Account> findByCustomerIdOrderByDisplayNameAsc(final UUID customerId);
}
