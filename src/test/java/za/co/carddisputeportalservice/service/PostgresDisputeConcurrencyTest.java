package za.co.carddisputeportalservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import za.co.capitecbank.CardDisputePortalServiceApplication;
import za.co.capitecbank.model.Dispute;
import za.co.capitecbank.repository.CustomerRepository;
import za.co.capitecbank.service.DisputeService;
import za.co.carddisputeportalservice.generated.model.DisputeStatus;
import za.co.carddisputeportalservice.security.TestJwtConfig;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Opt-in tests: point only at a disposable PostgreSQL database, never the application database. */
@SpringBootTest(classes = CardDisputePortalServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
@EnabledIfSystemProperty(named = "postgres.tests", matches = "true")
class PostgresDisputeConcurrencyTest {
    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("test.postgres.url"));
        registry.add(
                "spring.datasource.username",
                () -> System.getProperty("test.postgres.username", "dispute_tests"));
        registry.add(
                "spring.datasource.password",
                () -> System.getProperty("test.postgres.password", "dispute_tests"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired DisputeService service;
    @Autowired CustomerRepository customers;
    private DisputeFixtures fixture;

    @BeforeEach
    void seed() {
        fixture =
                new TransactionTemplate(transactionManager)
                        .execute(status -> DisputeFixtures.create(em, 2));
    }

    @Test
    void sameKeyForDifferentTransactionsReturnsCreatedAndConflictWithoutOrphanWrites()
            throws Exception {
        String key = UUID.randomUUID().toString();
        var results =
                concurrently(new Submission(fixture, 0, key), new Submission(fixture, 1, key));
        assertThat(results.stream().map(Result::status).toList())
                .containsExactlyInAnyOrder(201, 409);
        var conflict =
                results.stream().filter(result -> result.status() == 409).findFirst().orElseThrow();
        assertThat(conflict.contentType()).startsWith("application/problem+json");
        assertThat(JsonPath.<String>read(conflict.body(), "$.code")).isEqualTo("CONFLICT");
        assertSingleSubmission();
    }

    @Test
    void identicalConcurrentRetriesReturnTheSameCaseAndOneInitialEvent() throws Exception {
        String key = UUID.randomUUID().toString();
        var results =
                concurrently(new Submission(fixture, 0, key), new Submission(fixture, 0, key));
        assertThat(results.stream().map(Result::status).toList()).containsExactly(201, 201);
        assertThat(JsonPath.<String>read(results.getFirst().body(), "$.id"))
                .isEqualTo(JsonPath.<String>read(results.getLast().body(), "$.id"));
        assertSingleSubmission();
    }

    @Test
    void differentKeysCannotCreateTwoCasesForOneTransaction() throws Exception {
        var results =
                concurrently(
                        new Submission(fixture, 0, UUID.randomUUID().toString()),
                        new Submission(fixture, 0, UUID.randomUUID().toString()));
        assertThat(results.stream().map(Result::status).toList())
                .containsExactlyInAnyOrder(201, 409);
        assertSingleSubmission();
    }

    @Test
    void customersHaveIndependentIdempotencyNamespaces() throws Exception {
        var other =
                new TransactionTemplate(transactionManager)
                        .execute(status -> DisputeFixtures.create(em, 1));
        String key = UUID.randomUUID().toString();
        var results = concurrently(new Submission(fixture, 0, key), new Submission(other, 0, key));
        assertThat(results.stream().map(Result::status).toList()).containsExactly(201, 201);
        assertThat(JsonPath.<String>read(results.getFirst().body(), "$.id"))
                .isNotEqualTo(JsonPath.<String>read(results.getLast().body(), "$.id"));
    }

    @Test
    void aggregateCountsRunOnPostgresAndKeepRejectedCasesSeparate() throws Exception {
        var created = submit(new Submission(fixture, 0, UUID.randomUUID().toString()));
        assertThat(created.status()).isEqualTo(201);
        UUID id = UUID.fromString(JsonPath.read(created.body(), "$.id"));
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                em.find(Dispute.class, id)
                                        .addEvent(
                                                DisputeStatus.REJECTED,
                                                "Rejected",
                                                "Case rejected",
                                                DisputeFixtures.NOW.plusSeconds(1)));
        fixture.authenticate();
        try {
            var summary = service.getDisputeSummary(fixture.account().getId());
            assertThat(summary.getTotalTransactions()).isEqualTo(2);
            assertThat(summary.getDisputesSubmitted()).isEqualTo(1);
            assertThat(summary.getRejected()).isEqualTo(1);
            assertThat(summary.getResolved()).isZero();
            assertThat(summary.getInProgress()).isZero();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void creationWaitsForTheCustomerRowLock() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder =
                    executor.submit(
                            () ->
                                    new TransactionTemplate(transactionManager)
                                            .executeWithoutResult(
                                                    status -> {
                                                        customers
                                                                .findByIdForUpdate(
                                                                        fixture.customer().getId())
                                                                .orElseThrow();
                                                        locked.countDown();
                                                        try {
                                                            assertThat(
                                                                            release.await(
                                                                                    10,
                                                                                    TimeUnit
                                                                                            .SECONDS))
                                                                    .isTrue();
                                                        } catch (InterruptedException interrupted) {
                                                            Thread.currentThread().interrupt();
                                                            throw new IllegalStateException(
                                                                    interrupted);
                                                        }
                                                    }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var request =
                    executor.submit(
                            () -> submit(new Submission(fixture, 0, UUID.randomUUID().toString())));
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                long waiting = 0;
                while (System.nanoTime() < deadline && waiting == 0) {
                    waiting =
                            jdbc.queryForObject(
                                    "select count(*) from pg_stat_activity where datname ="
                                            + " current_database() and wait_event_type = 'Lock'",
                                    Long.class);
                    if (waiting == 0) Thread.sleep(25);
                }
                assertThat(waiting).isGreaterThan(0);
                assertThat(request.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            holder.get(10, TimeUnit.SECONDS);
            assertThat(request.get(10, TimeUnit.SECONDS).status()).isEqualTo(201);
        } finally {
            release.countDown();
        }
    }

    private List<Result> concurrently(final Submission first, final Submission second)
            throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                start.await();
                                return submit(first);
                            });
            var b =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                start.await();
                                return submit(second);
                            });
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }

    private Result submit(final Submission submission) throws Exception {
        var response =
                mvc.perform(
                                post("/api/v1/disputes")
                                        .with(
                                                jwt().jwt(
                                                                token ->
                                                                        token.issuer(
                                                                                        "https://issuer.example.test/")
                                                                                .subject(
                                                                                        submission
                                                                                                .fixture()
                                                                                                .subject())))
                                        .header("Idempotency-Key", submission.key())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"transactionId":"%s","reason":"OTHER","details":"This purchase is incorrect."}
                                                """
                                                        .formatted(
                                                                submission
                                                                        .fixture()
                                                                        .transactions()
                                                                        .get(
                                                                                submission
                                                                                        .transaction())
                                                                        .getId())))
                        .andReturn()
                        .getResponse();
        return new Result(
                response.getStatus(), response.getContentAsString(), response.getContentType());
    }

    private void assertSingleSubmission() {
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from dispute_submissions where customer_id = ?",
                                Long.class,
                                fixture.customer().getId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from disputes d join card_transactions t on t.id ="
                                        + " d.transaction_id where t.account_id = ?",
                                Long.class,
                                fixture.account().getId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from dispute_events e join disputes d on d.id ="
                                        + " e.dispute_id join card_transactions t on t.id ="
                                        + " d.transaction_id where t.account_id = ?",
                                Long.class,
                                fixture.account().getId()))
                .isEqualTo(1);
    }

    record Submission(DisputeFixtures fixture, int transaction, String key) {}

    record Result(int status, String body, String contentType) {}
}
