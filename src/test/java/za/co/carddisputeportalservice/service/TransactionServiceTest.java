package za.co.carddisputeportalservice.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import za.co.capitecbank.exception.CardDisputeException;
import za.co.capitecbank.exception.ErrorCode;
import za.co.capitecbank.mapper.PortalMapper;
import za.co.capitecbank.model.Account;
import za.co.capitecbank.model.Customer;
import za.co.capitecbank.model.Dispute;
import za.co.capitecbank.model.TransactionRecord;
import za.co.capitecbank.repository.DisputeRepository;
import za.co.capitecbank.repository.TransactionRepository;
import za.co.capitecbank.security.CurrentCustomer;
import za.co.capitecbank.service.TransactionService;
import za.co.carddisputeportalservice.generated.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock TransactionRepository transactions;
    @Mock DisputeRepository disputes;
    @Mock CurrentCustomer current;
    private TransactionService service;
    private final Customer owner =
            new Customer(UUID.randomUUID(), CustomerType.INDIVIDUAL, "Customer", null);
    private final Account account =
            new Account(UUID.randomUUID(), AccountType.CURRENT, owner, "Current", "1234", "ZAR");

    @BeforeEach
    void setup() {
        when(current.id()).thenReturn(owner.getId());
        service =
                new TransactionService(
                        transactions, disputes, Mappers.getMapper(PortalMapper.class), current);
    }

    @Test
    void ownedPostedTransactionsWithoutCasesAreEligible() {
        var item = transaction(TransactionStatus.POSTED);
        when(transactions.findByIdAndAccountCustomerId(item.getId(), owner.getId()))
                .thenReturn(Optional.of(item));
        when(disputes.findByTransactionAccountCustomerIdAndTransactionIdIn(
                        owner.getId(), List.of(item.getId())))
                .thenReturn(List.of());
        var result = service.getTransactionById(item.getId());
        assertThat(result.getDisputeEligible()).isTrue();
        assertThat(result.getDisputeIneligibilityReason()).isNull();
        assertThat(result.getAccountId()).isEqualTo(account.getId());
    }

    @ParameterizedTest
    @EnumSource(
            value = TransactionStatus.class,
            names = {"PENDING", "REVERSED"})
    void unpostedTransactionsAreIneligible(final TransactionStatus status) {
        var item = transaction(status);
        when(transactions.findByIdAndAccountCustomerId(item.getId(), owner.getId()))
                .thenReturn(Optional.of(item));
        var result = service.getTransactionById(item.getId());
        assertThat(result.getDisputeEligible()).isFalse();
        assertThat(result.getDisputeIneligibilityReason()).contains("Only posted");
    }

    @Test
    void existingCasesPreventAnotherDispute() {
        var item = transaction(TransactionStatus.POSTED);
        var dispute =
                new Dispute(item, DisputeReason.OTHER, "This purchase is incorrect", Instant.now());
        when(transactions.findByIdAndAccountCustomerId(item.getId(), owner.getId()))
                .thenReturn(Optional.of(item));
        when(disputes.findByTransactionAccountCustomerIdAndTransactionIdIn(
                        owner.getId(), List.of(item.getId())))
                .thenReturn(List.of(dispute));
        var result = service.getTransactionById(item.getId());
        assertThat(result.getDisputeId()).isEqualTo(dispute.getId());
        assertThat(result.getDisputeEligible()).isFalse();
    }

    @Test
    void inaccessibleTransactionsRemainNotFound() {
        UUID id = UUID.randomUUID();
        when(transactions.findByIdAndAccountCustomerId(id, owner.getId()))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTransactionById(id))
                .isInstanceOfSatisfying(
                        CardDisputeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verifyNoInteractions(disputes);
    }

    @Test
    void invertedDateFiltersFailBeforeQueryingTransactions() {
        assertThatThrownBy(
                        () ->
                                service.getTransactions(
                                        account.getId(),
                                        0,
                                        20,
                                        LocalDate.of(2026, 10, 2),
                                        LocalDate.of(2026, 10, 1),
                                        null,
                                        null))
                .isInstanceOfSatisfying(
                        CardDisputeException.class,
                        error ->
                                assertThat(error.getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(transactions, disputes);
    }

    @Test
    void pagesRetainCountsAndAttachExistingCaseIds() {
        var first = transaction(TransactionStatus.POSTED);
        var second = transaction(TransactionStatus.PENDING);
        var dispute =
                new Dispute(
                        first, DisputeReason.OTHER, "This purchase is incorrect", Instant.now());
        when(transactions.findAll(
                        ArgumentMatchers.<Specification<TransactionRecord>>any(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 42));
        when(disputes.findByTransactionAccountCustomerIdAndTransactionIdIn(
                        owner.getId(), List.of(first.getId(), second.getId())))
                .thenReturn(List.of(dispute));
        var result = service.getTransactions(account.getId(), 0, 20, null, null, null, null);
        assertThat(result.getTotalElements()).isEqualTo(42);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getContent().getFirst().getDisputeId()).isEqualTo(dispute.getId());
        assertThat(result.getContent().getLast().getDisputeEligible()).isFalse();
        verify(current).requireAccount(account.getId());
    }

    @Test
    void emptyPagesDoNotExecuteAnEmptyDisputeLookup() {
        when(transactions.findAll(
                        ArgumentMatchers.<Specification<TransactionRecord>>any(),
                        any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));
        assertThat(service.getTransactions(null, 0, 20, null, null, null, null).getContent())
                .isEmpty();
        verifyNoInteractions(disputes);
    }

    @Test
    void rejectsAnInaccessibleAccountBeforeQueryingTransactions() {
        doThrow(new CardDisputeException(ErrorCode.NOT_FOUND, "Account was not found"))
                .when(current)
                .requireAccount(account.getId());
        assertThatThrownBy(
                        () ->
                                service.getTransactions(
                                        account.getId(), 0, 20, null, null, null, null))
                .isInstanceOfSatisfying(
                        CardDisputeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verifyNoInteractions(transactions, disputes);
    }

    private TransactionRecord transaction(final TransactionStatus status) {
        return new TransactionRecord(
                UUID.randomUUID(),
                account,
                "Merchant",
                "Reference",
                "Cape Town",
                TransactionType.SHOPPING,
                "Retail",
                new BigDecimal("100.00"),
                "ZAR",
                Instant.parse("2026-10-18T10:00:00Z"),
                "1234",
                status);
    }
}
