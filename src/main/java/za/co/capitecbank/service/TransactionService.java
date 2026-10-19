package za.co.capitecbank.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.capitecbank.exception.CardDisputeException;
import za.co.capitecbank.exception.ErrorCode;
import za.co.capitecbank.mapper.CardDisputeMapper;
import za.co.capitecbank.model.Dispute;
import za.co.capitecbank.model.TransactionRecord;
import za.co.capitecbank.repository.DisputeRepository;
import za.co.capitecbank.repository.TransactionRepository;
import za.co.capitecbank.security.CurrentCustomer;
import za.co.carddisputeportalservice.generated.model.DisputeStatus;
import za.co.carddisputeportalservice.generated.model.TransactionPage;
import za.co.carddisputeportalservice.generated.model.TransactionResponse;
import za.co.carddisputeportalservice.generated.model.TransactionStatus;
import za.co.carddisputeportalservice.generated.model.TransactionType;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final DisputeRepository disputeRepository;
    private final CardDisputeMapper cardDisputeMapper;
    private final CurrentCustomer currentCustomer;

    public TransactionPage getTransactions(final UUID accountId, final int page, final int size, final LocalDate from,
                                           final LocalDate to, final String search, final TransactionType type) {
        UUID owner = currentCustomer.id();
        currentCustomer.requireAccount(accountId);

        if (from != null && to != null && from.isAfter(to))
            throw new CardDisputeException(ErrorCode.VALIDATION_ERROR, "from must not be after to");

        var results = transactionRepository.findAll(
                        transactionFilter(owner, accountId, from, to, search, type),
                        PageRequest.of(page, size, Sort.by(Sort.Order.desc("transactionTime"), Sort.Order.desc("id"))));

        var ids = results.getContent().stream().map(TransactionRecord::getId).toList();
        Map<UUID, UUID> existing = ids.isEmpty() ? Map.of() : disputeRepository
                                .findByTransactionAccountCustomerIdAndTransactionIdIn(owner, ids)
                                .stream()
                                .filter(d -> d.getStatus() != DisputeStatus.CLOSED)
                                .collect(Collectors.toMap(d -> d.getTransaction().getId(), Dispute::getId));

        return cardDisputeMapper.transactionPage(results.map(t -> response(t, existing.get(t.getId()))));
    }

    public TransactionResponse getTransactionById(final UUID id) {
        UUID owner = currentCustomer.id();

        var transaction = transactionRepository
                        .findByIdAndAccountCustomerId(id, owner)
                        .orElseThrow(() -> new CardDisputeException(ErrorCode.NOT_FOUND, "Transaction was not found"));

        var existing = disputeRepository.findByTransactionAccountCustomerIdAndTransactionIdIn(owner, List.of(id));

        return response(transaction, existing.stream()
                .filter(d -> d.getStatus() != DisputeStatus.CLOSED)
                .map(Dispute::getId).findFirst().orElse(null));
    }

    private Specification<TransactionRecord> transactionFilter(final UUID owner, final UUID accountId, final LocalDate from,
                                                               final LocalDate to, final String search, final TransactionType type) {

        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("account").get("customer").get("id"), owner));

            if (accountId != null)
                predicates.add(cb.equal(root.get("account").get("id"), accountId));

            if (from != null)
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("transactionTime"),
                                from.atStartOfDay(ZoneOffset.UTC).toInstant()));

            if (to != null)
                predicates.add(cb.lessThan(
                                root.get("transactionTime"),
                                to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));

            if (type != null)
                predicates.add(cb.equal(root.get("type"), type));

            if (search != null && !search.isBlank()) {
                String term =
                        "%"
                                + search.strip()
                                        .toLowerCase(Locale.ROOT)
                                        .replace("!", "!!")
                                        .replace("%", "!%")
                                        .replace("_", "!_")
                                + "%";
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("merchantName")), term, '!'),
                                cb.like(cb.lower(root.get("reference")), term, '!')));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private TransactionResponse response(
            final TransactionRecord transaction, final UUID disputeId) {
        String reason = disputeId != null
                        ? "A non-closed dispute already exists for this transaction"
                        : transaction.getStatus() != TransactionStatus.POSTED
                                ? "Only posted transactions can be disputed"
                                : null;
        return cardDisputeMapper.map(transaction, reason == null, reason, disputeId);
    }
}
