package za.co.carddisputeportalservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import za.co.capitecbank.CardDisputePortalServiceApplication;
import za.co.capitecbank.exception.CardDisputeException;
import za.co.capitecbank.exception.ErrorCode;
import za.co.capitecbank.model.Account;
import za.co.capitecbank.model.TransactionRecord;
import za.co.capitecbank.service.TransactionService;
import za.co.carddisputeportalservice.generated.model.TransactionResponse;
import za.co.carddisputeportalservice.generated.model.TransactionStatus;
import za.co.carddisputeportalservice.generated.model.TransactionType;
import za.co.carddisputeportalservice.security.TestJwtConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@SpringBootTest(classes = CardDisputePortalServiceApplication.class)
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
@Transactional
class TransactionServiceIntegrationTest {

    @Autowired EntityManager em;
    @Autowired TransactionService service;
    private Account owned;
    private Account foreign;

    @BeforeEach
    void seedAccounts() {
        var fixture = DisputeFixtures.create(em, 0);
        owned = fixture.account();
        foreign = DisputeFixtures.create(em, 0).account();
        fixture.authenticate();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void scopesListsAccountFiltersAndIndividualReadsToTheAuthenticatedCustomer() {
        var ownTransaction = transaction(owned, "Shop", "2026-10-18T10:00:00Z");
        var foreignTransaction = transaction(foreign, "Shop", "2026-10-18T10:00:00Z");
        em.flush();
        em.clear();

        var result = service.getTransactions(null, 0, 20, null, null, null, null);
        assertThat(result.getContent())
                .extracting(TransactionResponse::getId)
                .containsExactly(ownTransaction.getId());
        assertThat(service.getTransactionById(ownTransaction.getId()).getId())
                .isEqualTo(ownTransaction.getId());
        assertThatThrownBy(() -> service.getTransactionById(foreignTransaction.getId()))
                .isInstanceOfSatisfying(
                        CardDisputeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(
                        () ->
                                service.getTransactions(
                                        foreign.getId(), 0, 20, null, null, null, null))
                .isInstanceOfSatisfying(
                        CardDisputeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void usesInclusiveUtcDateBoundariesAndRetainsPageTotals() {
        transaction(owned, "Previous day", "2026-10-17T23:59:59Z");
        var start = transaction(owned, "Start of day", "2026-10-18T00:00:00Z");
        var end = transaction(owned, "End of day", "2026-10-18T23:59:59.999999Z");
        transaction(owned, "Next day", "2026-10-19T00:00:00Z");
        em.flush();
        var day = LocalDate.of(2026, 10, 18);

        var first = service.getTransactions(owned.getId(), 0, 1, day, day, null, null);
        var second = service.getTransactions(owned.getId(), 1, 1, day, day, null, null);
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent())
                .extracting(TransactionResponse::getId)
                .containsExactly(end.getId());
        assertThat(second.getContent())
                .extracting(TransactionResponse::getId)
                .containsExactly(start.getId());
    }

    @Test
    void treatsSearchWildcardsAsLiteralCharactersAndIgnoresCase() {
        transaction(owned, "ShopA", "2026-10-18T10:00:00Z");
        for (String symbol : new String[] {"%", "_", "!"}) {
            var expected = transaction(owned, "Shop" + symbol, "2026-10-18T10:00:00Z");
            transaction(foreign, "Shop" + symbol, "2026-10-18T10:00:00Z");
            em.flush();
            var result =
                    service.getTransactions(
                            null, 0, 20, null, null, "  SHOP" + symbol + "  ", null);
            assertThat(result.getContent())
                    .extracting(TransactionResponse::getId)
                    .containsExactly(expected.getId());
        }
    }

    private TransactionRecord transaction(
            final Account account, final String merchant, final String time) {
        UUID id = UUID.randomUUID();
        var record =
                new TransactionRecord(
                        id,
                        account,
                        merchant,
                        "TX-" + id,
                        "Cape Town",
                        TransactionType.SHOPPING,
                        "Retail",
                        new BigDecimal("100.00"),
                        "ZAR",
                        Instant.parse(time),
                        "1234",
                        TransactionStatus.POSTED);
        em.persist(record);
        return record;
    }
}
