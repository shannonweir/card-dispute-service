package za.co.capitecbank.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.capitecbank.exception.CardDisputeException;
import za.co.capitecbank.exception.ErrorCode;
import za.co.capitecbank.mapper.PortalMapper;
import za.co.capitecbank.model.Dispute;
import za.co.capitecbank.model.DisputeSubmission;
import za.co.capitecbank.repository.CustomerRepository;
import za.co.capitecbank.repository.DisputeEventRepository;
import za.co.capitecbank.repository.DisputeRepository;
import za.co.capitecbank.repository.DisputeSubmissionRepository;
import za.co.capitecbank.repository.TransactionRepository;
import za.co.capitecbank.security.CurrentCustomer;
import za.co.carddisputeportalservice.generated.model.CreateDisputeRequest;
import za.co.carddisputeportalservice.generated.model.DisputePage;
import za.co.carddisputeportalservice.generated.model.DisputeReason;
import za.co.carddisputeportalservice.generated.model.DisputeReasonOption;
import za.co.carddisputeportalservice.generated.model.DisputeResponse;
import za.co.carddisputeportalservice.generated.model.DisputeStatus;
import za.co.carddisputeportalservice.generated.model.DisputeSummary;
import za.co.carddisputeportalservice.generated.model.TimelineEventResponse;
import za.co.carddisputeportalservice.generated.model.TransactionStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisputeService {

    private final TransactionRepository transactionRepository;
    private final DisputeRepository disputeRepository;
    private final Clock clock;
    private final PortalMapper portalMapper;
    private final CurrentCustomer currentCustomer;
    private final DisputeSubmissionRepository submissions;
    private final CustomerRepository customers;
    private final DisputeEventRepository events;
    private static final List<DisputeStatus> ACTIVE_STATUSES = List.of(DisputeStatus.SUBMITTED, DisputeStatus.UNDER_REVIEW,
            DisputeStatus.INFORMATION_REQUESTED, DisputeStatus.INVESTIGATING);
    private static final List<DisputeStatus> RESOLVED_STATUSES = List.of(DisputeStatus.RESOLVED, DisputeStatus.CLOSED);

    public DisputePage getDisputes(final UUID accountId, final int page, final int size, final DisputeStatus status) {
        UUID owner = currentCustomer.id();
        currentCustomer.requireAccount(accountId);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        return portalMapper.disputePage(disputeRepository.findScoped(owner, accountId, status, pageable).map(portalMapper::map));
    }

    @Transactional
    public DisputeResponse createDispute(final CreateDisputeRequest request, final String key) {
        UUID owner = currentCustomer.id();
        String details = request.getDetails().strip();
        if (details.length() < 10)
            throw new CardDisputeException(ErrorCode.VALIDATION_ERROR, "Provide at least 10 characters of dispute details");
        String hash = hash(request.getTransactionId(), request.getReason(), details);
        // A customer owns the key namespace. Always take this lock before the transaction lock.
        // This deliberately serializes that customer's short dispute-creation transactions.
        customers.findByIdForUpdate(owner)
                .orElseThrow(() -> new CardDisputeException(ErrorCode.NOT_FOUND, "Customer was not found"));

        var transaction = transactionRepository.findOwnedForUpdate(request.getTransactionId(), owner)
                        .orElseThrow(() -> new CardDisputeException(ErrorCode.NOT_FOUND, "Transaction was not found"));

        var previous = submissions.findByCustomerIdAndIdempotencyKey(owner, key);
        if (previous.isPresent()) {
            if (!previous.get().getRequestHash().equals(hash))
                throw new CardDisputeException(ErrorCode.CONFLICT, "Idempotency-Key was already used for a different request");
            return portalMapper.map(previous.get().getDispute());
        }

        if (disputeRepository.existsByTransactionIdAndStatusNot(transaction.getId(), DisputeStatus.CLOSED))
            throw new CardDisputeException(ErrorCode.CONFLICT, "A non-closed dispute already exists for this transaction");

        if (transaction.getStatus() != TransactionStatus.POSTED)
            throw new CardDisputeException(ErrorCode.TRANSACTION_INELIGIBLE);

        var dispute = disputeRepository.save(new Dispute(transaction, request.getReason(), details, clock.instant()));
        submissions.save(new DisputeSubmission(owner, key, hash, dispute));

        return portalMapper.map(dispute);
    }

    public Dispute getOwnedDispute(final UUID id) {
        UUID owner = currentCustomer.id();
        return disputeRepository.findByIdAndTransactionAccountCustomerId(id, owner)
                .orElseThrow(() -> new CardDisputeException(ErrorCode.NOT_FOUND, "Dispute was not found"));
    }

    public void requireOwnedDispute(final UUID id) {
        if (!disputeRepository.existsByIdAndTransactionAccountCustomerId(id, currentCustomer.id()))
            throw new CardDisputeException(ErrorCode.NOT_FOUND, "Dispute was not found");
    }

    public DisputeResponse getDisputeById(final UUID id) {
        return disputeRepository
                .findDetailsByIdAndTransactionAccountCustomerId(id, currentCustomer.id())
                .map(portalMapper::map)
                .orElseThrow(() -> new CardDisputeException(ErrorCode.NOT_FOUND, "Dispute was not found"));
    }

    public List<TimelineEventResponse> getDisputeTimeline(final UUID id) {
        requireOwnedDispute(id);
        return events.findByDisputeIdOrderByOccurredAtAscIdAsc(id).stream()
                .map(portalMapper::map)
                .toList();
    }

    public DisputeSummary getDisputeSummary(final UUID accountId) {
        UUID owner = currentCustomer.id();
        currentCustomer.requireAccount(accountId);
        return portalMapper.map(
                disputeRepository.summarize(owner, accountId, ACTIVE_STATUSES, RESOLVED_STATUSES, DisputeStatus.REJECTED));
    }

    public List<DisputeReasonOption> getDisputeReasons() {
        currentCustomer.id();
        return List.of(
                new DisputeReasonOption()
                        .code(DisputeReason.UNAUTHORISED)
                        .label("Unauthorised transaction"),
                new DisputeReasonOption()
                        .code(DisputeReason.DUPLICATE_CHARGE)
                        .label("Duplicate charge"),
                new DisputeReasonOption()
                        .code(DisputeReason.INCORRECT_AMOUNT)
                        .label("Incorrect amount"),
                new DisputeReasonOption()
                        .code(DisputeReason.GOODS_NOT_RECEIVED)
                        .label("Goods or services not received"),
                new DisputeReasonOption()
                        .code(DisputeReason.REFUND_NOT_RECEIVED)
                        .label("Refund not received"),
                new DisputeReasonOption().code(DisputeReason.OTHER).label("Other"));
    }

    private String hash(
            final UUID transactionId, final DisputeReason reason, final String details) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (transactionId + "\n" + reason + "\n" + details)
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
