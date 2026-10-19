package za.co.carddisputeportalservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import za.co.capitecbank.exception.CardDisputeException;
import za.co.capitecbank.exception.ErrorCode;
import za.co.capitecbank.mapper.CardDisputeMapper;
import za.co.capitecbank.model.Account;
import za.co.capitecbank.model.Customer;
import za.co.capitecbank.model.Dispute;
import za.co.capitecbank.model.TransactionRecord;
import za.co.capitecbank.repository.CustomerRepository;
import za.co.capitecbank.repository.DisputeEventRepository;
import za.co.capitecbank.repository.DisputeRepository;
import za.co.capitecbank.repository.DisputeSubmissionRepository;
import za.co.capitecbank.repository.TransactionRepository;
import za.co.capitecbank.security.CurrentCustomer;
import za.co.capitecbank.service.DisputeService;
import za.co.carddisputeportalservice.generated.model.CreateDisputeRequest;
import za.co.carddisputeportalservice.generated.model.DisputeReason;
import za.co.carddisputeportalservice.generated.model.DisputeStatus;
import za.co.carddisputeportalservice.generated.model.TransactionStatus;
import za.co.carddisputeportalservice.generated.model.TransactionType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {
    private static final UUID CUSTOMER = UUID.fromString("f19f1d66-263f-4fd4-a3d5-1638b308e5f3");
    private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");

    @Mock TransactionRepository transactions;
    @Mock DisputeSubmissionRepository submissions;
    @Mock CustomerRepository customers;
    @Mock DisputeEventRepository events;
    @Mock DisputeRepository disputes;
    @Mock CurrentCustomer customer;
    private DisputeService service;

    @BeforeEach
    void setUp() {
        service =
                new DisputeService(
                        transactions,
                        disputes,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Mappers.getMapper(CardDisputeMapper.class),
                        customer,
                        submissions,
                        customers,
                        events);
        when(customer.id()).thenReturn(CUSTOMER);
        when(customers.findByIdForUpdate(CUSTOMER))
                .thenReturn(
                        Optional.of(
                                new Customer(
                                        CUSTOMER,
                                        za.co.carddisputeportalservice.generated.model.CustomerType
                                                .INDIVIDUAL,
                                        "Test customer",
                                        "test@example.com")));
    }

    @Test
    void createsDisputeForOwnedTransaction() {
        UUID transactionId = UUID.randomUUID();
        var transaction = transaction(transactionId);
        when(transactions.findOwnedForUpdate(transactionId, CUSTOMER))
                .thenReturn(Optional.of(transaction));
        when(disputes.existsByTransactionIdAndStatusNot(transactionId, DisputeStatus.CLOSED)).thenReturn(false);
        when(disputes.save(org.mockito.ArgumentMatchers.any(Dispute.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createDispute(request(transactionId), "unit-test-key");

        assertThat(result.getTransactionId()).isEqualTo(transactionId);
        assertThat(result.getStatus()).isEqualTo(DisputeStatus.SUBMITTED);
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
        assertThat(result.getDetails()).isEqualTo("This transaction was not authorised by me.");
        assertThat(result.getReference()).startsWith("DSP-2026-");
        verify(transactions).findOwnedForUpdate(transactionId, CUSTOMER);
    }

    @Test
    void hidesTransactionsOwnedByAnotherCustomer() {
        UUID transactionId = UUID.randomUUID();
        when(transactions.findOwnedForUpdate(transactionId, CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDispute(request(transactionId), "unit-test-key"))
                .isInstanceOfSatisfying(
                        CardDisputeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void rejectsASecondDisputeForTheSameTransaction() {
        UUID transactionId = UUID.randomUUID();
        when(transactions.findOwnedForUpdate(transactionId, CUSTOMER))
                .thenReturn(Optional.of(transaction(transactionId)));
        when(disputes.existsByTransactionIdAndStatusNot(transactionId, DisputeStatus.CLOSED)).thenReturn(true);

        assertThatThrownBy(() -> service.createDispute(request(transactionId), "unit-test-key"))
                .isInstanceOfSatisfying(
                        CardDisputeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("already exists");
    }

    private CreateDisputeRequest request(final UUID transactionId) {
        return new CreateDisputeRequest()
                .transactionId(transactionId)
                .reason(DisputeReason.OTHER)
                .details("This transaction was not authorised by me.");
    }

    private TransactionRecord transaction(final UUID id) {
        return new TransactionRecord(
                id,
                new Account(
                        UUID.randomUUID(),
                        za.co.carddisputeportalservice.generated.model.AccountType.CURRENT,
                        new Customer(
                                CUSTOMER,
                                za.co.carddisputeportalservice.generated.model.CustomerType
                                        .INDIVIDUAL,
                                "Alex",
                                "alex@example.com"),
                        "Personal card",
                        "•••• 1234",
                        "ZAR"),
                "Test Merchant",
                "TEST-" + id,
                "Cape Town",
                TransactionType.SHOPPING,
                "Retail",
                new BigDecimal("100.00"),
                "ZAR",
                NOW.minusSeconds(3600),
                "4821",
                TransactionStatus.POSTED);
    }
}
