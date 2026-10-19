package za.co.capitecbank.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import za.co.capitecbank.dto.DisputeSummaryCounts;
import za.co.capitecbank.model.Dispute;
import za.co.carddisputeportalservice.generated.model.DisputeStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    boolean existsByTransactionIdAndStatusNot(final UUID transactionId, final DisputeStatus status);

    @EntityGraph(
            attributePaths = {"transaction", "transaction.account", "transaction.account.customer"})
    @Query(
            "select d from Dispute d where d.transaction.account.customer.id = :customerId and"
                + " (:accountId is null or d.transaction.account.id = :accountId) and (:status is"
                + " null or d.status = :status)")
    Page<Dispute> findScoped(final UUID customerId, final UUID accountId, final DisputeStatus status, final Pageable pageable);

    List<Dispute> findByTransactionAccountCustomerIdAndTransactionIdIn(final UUID customerId, final Collection<UUID> transactionIds);

    @Query(
            """
            select count(distinct t.id) as totalTransactions,
                   count(d) as disputesSubmitted,
                   coalesce(sum(case when d.status in :activeStatuses then 1 else 0 end), 0) as inProgress,
                   coalesce(sum(case when d.status in :resolvedStatuses then 1 else 0 end), 0) as resolved,
                   coalesce(sum(case when d.status = :rejectedStatus then 1 else 0 end), 0) as rejected
            from TransactionRecord t left join Dispute d on d.transaction = t
            where t.account.customer.id = :customerId
              and (:accountId is null or t.account.id = :accountId)
            """)
    DisputeSummaryCounts summarize(final UUID customerId, final UUID accountId, final Collection<DisputeStatus> activeStatuses,
                                   final Collection<DisputeStatus> resolvedStatuses, final DisputeStatus rejectedStatus);

    boolean existsByIdAndTransactionAccountCustomerId(final UUID id, final UUID customerId);

    Optional<Dispute> findByIdAndTransactionAccountCustomerId(final UUID id, final UUID customerId);

    @EntityGraph(attributePaths = {"transaction", "transaction.account", "transaction.account.customer"})
    Optional<Dispute> findDetailsByIdAndTransactionAccountCustomerId(final UUID id, final UUID customerId);
}
