package za.co.capitecbank.repository;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import za.co.capitecbank.model.TransactionRecord;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionRecord, UUID>, JpaSpecificationExecutor<TransactionRecord> {

    Optional<TransactionRecord> findByIdAndAccountCustomerId(final UUID id, final UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select t from TransactionRecord t where t.id = :id and t.account.customer.id ="
                    + " :customerId")
    Optional<TransactionRecord> findOwnedForUpdate(final UUID id, final UUID customerId);
}
