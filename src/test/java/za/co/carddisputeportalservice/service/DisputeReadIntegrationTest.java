package za.co.carddisputeportalservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import za.co.capitecbank.CardDisputePortalServiceApplication;
import za.co.capitecbank.exception.CardDisputeException;
import za.co.capitecbank.exception.ErrorCode;
import za.co.capitecbank.model.Account;
import za.co.capitecbank.model.Customer;
import za.co.capitecbank.model.Dispute;
import za.co.capitecbank.model.DisputeEvent;
import za.co.capitecbank.service.DisputeService;
import za.co.carddisputeportalservice.generated.model.DisputeReason;
import za.co.carddisputeportalservice.generated.model.DisputeStatus;
import za.co.carddisputeportalservice.security.TestJwtConfig;

import java.util.ArrayList;
import java.util.UUID;

@SpringBootTest(
        classes = CardDisputePortalServiceApplication.class,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:disputeReads;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "spring.jpa.properties.hibernate.generate_statistics=true"
        })
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
@Transactional
class DisputeReadIntegrationTest {
    @DynamicPropertySource
    static void postgresWhenRequested(final DynamicPropertyRegistry registry) {
        if (Boolean.getBoolean("postgres.tests")) {
            registry.add("spring.datasource.url", () -> System.getProperty("test.postgres.url"));
            registry.add(
                    "spring.datasource.username",
                    () -> System.getProperty("test.postgres.username", "dispute_tests"));
            registry.add(
                    "spring.datasource.password",
                    () -> System.getProperty("test.postgres.password", "dispute_tests"));
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }
    }

    @Autowired DisputeService service;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    private DisputeFixtures fixture;
    private final ArrayList<UUID> cases = new ArrayList<>();
    private Statistics statistics;

    @BeforeEach
    void seed() {
        fixture = DisputeFixtures.create(em, 15);
        var statuses = DisputeStatus.values();
        for (int i = 0; i < 14; i++) {
            var dispute =
                    new Dispute(
                            fixture.transactions().get(i),
                            DisputeReason.OTHER,
                            "This purchase is incorrect.",
                            DisputeFixtures.NOW);
            dispute.addEvent(
                    statuses[i % statuses.length],
                    "Recorded update",
                    "Case update",
                    DisputeFixtures.NOW.plusSeconds(1));
            em.persist(dispute);
            cases.add(dispute.getId());
        }
        em.flush();
        em.clear();
        fixture.authenticate();
        statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void paginatedMappingHasConstantQueryCountAndDoesNotLoadEvents() {
        var result = service.getDisputes(fixture.account().getId(), 0, 10, null);
        assertThat(result.getTotalElements()).isEqualTo(14);
        assertThat(result.getContent()).hasSize(10);
        assertThat(result.getContent())
                .allSatisfy(
                        item -> {
                            assertThat(item.getCustomerId()).isEqualTo(fixture.customer().getId());
                            assertThat(item.getAccountId()).isEqualTo(fixture.account().getId());
                        });
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
        assertThat(statistics.getCollectionLoadCount()).isZero();
        assertThat(statistics.getEntityStatistics(DisputeEvent.class.getName()).getLoadCount())
                .isZero();
    }

    @Test
    void summaryCountsAllStatesInOneAggregateQuery() {
        var result = service.getDisputeSummary(null);
        assertThat(result.getTotalTransactions()).isEqualTo(15);
        assertThat(result.getDisputesSubmitted()).isEqualTo(14);
        assertThat(result.getInProgress()).isEqualTo(8);
        assertThat(result.getResolved()).isEqualTo(4);
        assertThat(result.getRejected()).isEqualTo(2);
        assertThat(result.getInProgress() + result.getResolved() + result.getRejected())
                .isEqualTo(result.getDisputesSubmitted());
        // One customer lookup and one aggregate statement; no per-status count queries.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        statistics.clear();
        assertThat(service.getDisputeSummary(fixture.account().getId()).getRejected()).isEqualTo(2);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    @Test
    void emptyOwnedAccountsHaveZeroCounts() {
        var empty =
                new Account(
                        UUID.randomUUID(),
                        za.co.carddisputeportalservice.generated.model.AccountType.SAVINGS,
                        em.getReference(Customer.class, fixture.customer().getId()),
                        "Empty savings",
                        "•••• 9999",
                        "ZAR");
        em.persist(empty);
        em.flush();
        var result = service.getDisputeSummary(empty.getId());
        assertThat(result.getTotalTransactions()).isZero();
        assertThat(result.getDisputesSubmitted()).isZero();
        assertThat(result.getInProgress()).isZero();
        assertThat(result.getResolved()).isZero();
        assertThat(result.getRejected()).isZero();
    }

    @Test
    void reasonsRequireALoginMappedToACustomer() {
        new DisputeFixtures(
                        fixture.customer(),
                        fixture.account(),
                        fixture.transactions(),
                        "auth0|unmapped-review-user")
                .authenticate();
        assertThatThrownBy(() -> service.getDisputeReasons())
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void detailAndDocumentOwnershipReadsDoNotFetchTimelineEvents() {
        assertThat(service.getDisputeById(cases.getFirst()).getId()).isEqualTo(cases.getFirst());
        assertThat(statistics.getCollectionLoadCount()).isZero();
        em.clear();
        service.requireOwnedDispute(cases.getFirst());
        var entity = service.getOwnedDispute(cases.getFirst());
        assertThat(emf.getPersistenceUnitUtil().isLoaded(entity, "events")).isFalse();
        assertThat(statistics.getCollectionLoadCount()).isZero();
    }

    @Test
    void timelineLoadsOnlyOwnedEventsInStableOrder() {
        var result = service.getDisputeTimeline(cases.getFirst());
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getOccurredAt()).isBefore(result.getLast().getOccurredAt());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        assertThat(statistics.getCollectionLoadCount()).isZero();
        assertThatThrownBy(() -> service.getDisputeTimeline(UUID.randomUUID()))
                .isInstanceOfSatisfying(
                        CardDisputeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
