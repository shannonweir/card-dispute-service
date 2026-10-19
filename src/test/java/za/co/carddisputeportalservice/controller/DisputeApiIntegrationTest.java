package za.co.carddisputeportalservice.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import za.co.capitecbank.CardDisputePortalServiceApplication;
import za.co.capitecbank.model.TransactionRecord;
import za.co.capitecbank.repository.AccountRepository;
import za.co.capitecbank.repository.TransactionRepository;
import za.co.carddisputeportalservice.generated.model.TransactionStatus;
import za.co.carddisputeportalservice.generated.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@org.springframework.context.annotation.Import(
        za.co.carddisputeportalservice.security.TestJwtConfig.class)
@SpringBootTest(classes = CardDisputePortalServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisputeApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @Autowired TransactionRepository transactions;
    @Autowired AccountRepository accounts;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    String issuer;

    @Value("${spring.security.oauth2.resourceserver.jwt.audiences}")
    String audience;

    private static final String SHANNON_SUBJECT = "auth0|shannon-test";
    private static final String ALEX_SUBJECT = "auth0|alex-test";
    private static final String CASE = "452f0c41-44d6-4c07-acb1-a2d0f3db9933";
    private static final String SPOTIFY = "67d61690-877d-4fb5-a09c-becaedd5ada1";
    private static final String BODY =
            """
            {"transactionId":"67d61690-877d-4fb5-a09c-becaedd5ada1","reason":"UNAUTHORISED","details":"I do not recognise this card purchase."}
            """;

    @Autowired za.co.capitecbank.repository.DisputeRepository disputes;

    @Test
    @Transactional
    void closedDisputeAllowsANewCaseButNotASecondNonClosedCase() throws Exception {
        var account =
                accounts.findAll().stream()
                        .filter(a -> a.getCustomer().getDisplayName().equals("Shannon Weir"))
                        .findFirst()
                        .orElseThrow();
        var transaction =
                transactions.saveAndFlush(
                        new TransactionRecord(
                                UUID.randomUUID(),
                                account,
                                "Closure test merchant",
                                "C-" + UUID.randomUUID(),
                                "Cape Town",
                                TransactionType.SHOPPING,
                                "Retail",
                                new BigDecimal("99.00"),
                                "ZAR",
                                Instant.now(),
                                "1234",
                                TransactionStatus.POSTED));
        var old =
                disputes.saveAndFlush(
                        new za.co.capitecbank.model.Dispute(
                                transaction,
                                za.co.carddisputeportalservice.generated.model.DisputeReason.OTHER,
                                "Original case comments",
                                Instant.now()));
        old.addEvent(
                za.co.carddisputeportalservice.generated.model.DisputeStatus.CLOSED,
                "Closed",
                "Original case is closed",
                Instant.now());
        disputes.saveAndFlush(old);
        String body =
                "{\"transactionId\":\""
                        + transaction.getId()
                        + "\",\"reason\":\"OTHER\",\"details\":\"New case after closure\"}";
        mvc.perform(
                        get("/api/v1/transactions/" + transaction.getId())
                                .with(jwt().jwt(j -> j.subject(SHANNON_SUBJECT).issuer(issuer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputeEligible").value(true))
                .andExpect(jsonPath("$.disputeId").doesNotExist());
        var result =
                mvc.perform(
                                post("/api/v1/disputes")
                                        .with(
                                                jwt().jwt(
                                                                j ->
                                                                        j.subject(SHANNON_SUBJECT)
                                                                                .issuer(issuer)))
                                        .header("Idempotency-Key", "closure-new-case")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn();
        String newId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        org.assertj.core.api.Assertions.assertThat(newId).isNotEqualTo(old.getId().toString());
        org.assertj.core.api.Assertions.assertThat(disputes.findById(old.getId())).isPresent();
        mvc.perform(
                        get("/api/v1/transactions")
                                .with(jwt().jwt(j -> j.subject(SHANNON_SUBJECT).issuer(issuer)))
                                .param("search", transaction.getReference()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].disputeId").value(newId));
        mvc.perform(
                        get("/api/v1/disputes/" + old.getId())
                                .with(jwt().jwt(j -> j.subject(SHANNON_SUBJECT).issuer(issuer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.details").value("Original case comments"));

        mvc.perform(
                        post("/api/v1/disputes")
                                .with(jwt().jwt(j -> j.subject(SHANNON_SUBJECT).issuer(issuer)))
                                .header("Idempotency-Key", "closure-third-case")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isConflict());
        mvc.perform(
                        get("/api/v1/transactions/" + transaction.getId())
                                .with(jwt().jwt(j -> j.subject(SHANNON_SUBJECT).issuer(issuer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputeEligible").value(false))
                .andExpect(jsonPath("$.disputeId").value(newId));
    }

    @Test
    void requiresAnExplicitMappingWithoutAuth0RolesOrPermissions() throws Exception {
        mvc.perform(
                        get("/api/v1/transactions")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + signedToken(
                                                        "auth0|unmapped",
                                                        issuer,
                                                        audience,
                                                        Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(
                        get("/api/v1/transactions")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + signedToken(
                                                        SHANNON_SUBJECT,
                                                        issuer,
                                                        audience,
                                                        Instant.now().plusSeconds(300))))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/transactions")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsExpiredTokensAndIncorrectIssuersOrAudiences() throws Exception {
        mvc.perform(
                        get("/api/v1/transactions")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + signedToken(
                                                        SHANNON_SUBJECT,
                                                        "https://wrong-issuer.example/",
                                                        audience,
                                                        Instant.now().plusSeconds(300))))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/transactions")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + signedToken(
                                                        SHANNON_SUBJECT,
                                                        issuer,
                                                        "https://wrong-audience.example/",
                                                        Instant.now().plusSeconds(300))))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/transactions")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + signedToken(
                                                        SHANNON_SUBJECT,
                                                        issuer,
                                                        audience,
                                                        Instant.now().minusSeconds(300))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingExpiryOrSubjectAndTokensForAnOidcClient() throws Exception {
        var missingExpiry =
                org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                        .subject(SHANNON_SUBJECT)
                        .issuer(issuer)
                        .audience(java.util.List.of(audience))
                        .issuedAt(Instant.now())
                        .build();
        var missingSubject =
                org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                        .issuer(issuer)
                        .audience(java.util.List.of(audience))
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
        for (var claims : java.util.List.of(missingExpiry, missingSubject)) {
            var token =
                    encoder.encode(
                                    org.springframework.security.oauth2.jwt.JwtEncoderParameters
                                            .from(
                                                    org.springframework.security.oauth2.jwt
                                                            .JwsHeader.with(
                                                                    org.springframework.security
                                                                            .oauth2.jose.jws
                                                                            .SignatureAlgorithm
                                                                            .RS256)
                                                            .build(),
                                                    claims))
                            .getTokenValue();
            mvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(
                        get("/api/v1/transactions")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + signedToken(
                                                        SHANNON_SUBJECT,
                                                        issuer,
                                                        "oidc-client-id",
                                                        Instant.now().plusSeconds(300))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private String signedToken(
            final String subject,
            final String tokenIssuer,
            final String tokenAudience,
            final Instant expiration) {
        var claims =
                org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                        .subject(subject)
                        .issuer(tokenIssuer)
                        .audience(java.util.List.of(tokenAudience))
                        .issuedAt(expiration.minusSeconds(600))
                        .expiresAt(expiration)
                        .build();
        return encoder.encode(
                        org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                                org.springframework.security.oauth2.jwt.JwsHeader.with(
                                                org.springframework.security.oauth2.jose.jws
                                                        .SignatureAlgorithm.RS256)
                                        .build(),
                                claims))
                .getTokenValue();
    }

    @Test
    void returnsContractErrorsForAnonymousAndInvalidTokens() throws Exception {
        mvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/v1/transactions"))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
        mvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void acceptsAValidAccessTokenAndRejectsTampering() throws Exception {
        String token =
                signedToken(SHANNON_SUBJECT, issuer, audience, Instant.now().plusSeconds(300));
        mvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(10));
        String tampered =
                token.substring(0, token.lastIndexOf('.') + 1)
                        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        mvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void paginatesFiltersAndNeverLeaksOtherCustomersTransactions() throws Exception {
        mvc.perform(
                        get("/api/v1/transactions")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT)))
                                .param("page", "0")
                                .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].merchant").value("Checkers Somerset West"))
                .andExpect(jsonPath("$.content[0].amount.amount").value(450))
                .andExpect(jsonPath("$.content[0].amount.currency").value("ZAR"))
                .andExpect(jsonPath("$.content[0].disputeEligible").value(false))
                .andExpect(jsonPath("$.totalElements").value(10))
                .andExpect(jsonPath("$.totalPages").value(5));
        mvc.perform(
                        get("/api/v1/transactions")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT)))
                                .param("from", "2026-08-24")
                                .param("to", "2026-08-25")
                                .param("search", "spotify")
                                .param("type", "ENTERTAINMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].disputeEligible").value(true));
        mvc.perform(
                        get("/api/v1/transactions")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(ALEX_SUBJECT))))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(
                        get("/api/v1/transactions/" + SPOTIFY)
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(ALEX_SUBJECT))))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesPageBoundsDatesAndSearchLength() throws Exception {
        for (String size : new String[] {"0", "101"}) {
            mvc.perform(
                            get("/api/v1/transactions")
                                    .with(
                                            jwt().jwt(
                                                            t ->
                                                                    t.issuer(
                                                                                    "https://issuer.example.test/")
                                                                            .subject(
                                                                                    SHANNON_SUBJECT)))
                                    .param("size", size))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(
                        get("/api/v1/transactions")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT)))
                                .param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/v1/transactions")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT)))
                                .param("from", "2026-08-30")
                                .param("to", "2026-08-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/v1/transactions")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT)))
                                .param("search", "x".repeat(121)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void persistsAtomicallyReplaysIdempotentlyAndRejectsChangedPayload() throws Exception {
        var created =
                mvc.perform(
                                post("/api/v1/disputes")
                                        .with(
                                                jwt().jwt(
                                                                t ->
                                                                        t.issuer(
                                                                                        "https://issuer.example.test/")
                                                                                .subject(
                                                                                        SHANNON_SUBJECT)))
                                        .header("Idempotency-Key", "test-submission-001")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(BODY))
                        .andExpect(status().isCreated())
                        .andExpect(header().string("Location", startsWith("/api/v1/disputes/")))
                        .andExpect(jsonPath("$.status").value("SUBMITTED"))
                        .andExpect(jsonPath("$.reference", startsWith("DSP-")))
                        .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/disputes")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT)))
                                .header("Idempotency-Key", "test-submission-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(
                        post("/api/v1/disputes")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT)))
                                .header("Idempotency-Key", "test-submission-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY.replace("UNAUTHORISED", "OTHER")))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/api/v1/disputes")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT)))
                                .header("Idempotency-Key", "test-submission-002")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isConflict());
        mvc.perform(
                        get("/api/v1/disputes/" + id + "/timeline")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].description").exists());
    }

    @Test
    @Transactional
    void rejectsIneligibleTransactions() throws Exception {
        var id = UUID.randomUUID();
        transactions.saveAndFlush(
                new TransactionRecord(
                        id,
                        accounts.findById(UUID.fromString("98ae225d-446d-4b4a-b860-67ea49abafaf"))
                                .orElseThrow(),
                        "Pending merchant",
                        "P-" + id,
                        "Cape Town",
                        TransactionType.OTHER,
                        "Other",
                        new BigDecimal("10.00"),
                        "ZAR",
                        Instant.now(),
                        "1234",
                        TransactionStatus.PENDING));
        mvc.perform(
                        post("/api/v1/disputes")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT)))
                                .header("Idempotency-Key", "pending-transaction-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY.replace(SPOTIFY, id.toString())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void enforcesDisputeOwnershipAndReturnsSummaryAndTimeline() throws Exception {
        mvc.perform(
                        get("/api/v1/disputes/" + CASE)
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(ALEX_SUBJECT))))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/v1/disputes")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(ALEX_SUBJECT))))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(
                        get("/api/v1/disputes/summary")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT))))
                .andExpect(jsonPath("$.totalTransactions").value(10))
                .andExpect(jsonPath("$.disputesSubmitted").value(3))
                .andExpect(jsonPath("$.inProgress").value(2))
                .andExpect(jsonPath("$.resolved").value(1));
        mvc.perform(
                        get("/api/v1/disputes/reasons")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT))))
                .andExpect(jsonPath("$", hasSize(6)));
        mvc.perform(
                        get("/api/v1/disputes/" + CASE + "/timeline")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT))))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[2].status").value("INFORMATION_REQUESTED"))
                .andExpect(jsonPath("$[2].actionRequired").value(true));
    }

    @Test
    @Transactional
    void storesUploadsValidatesMimeAndEnforcesOwnership() throws Exception {
        var pdf =
                new MockMultipartFile(
                        "file",
                        "../../receipt.pdf",
                        "application/pdf",
                        "%PDF-1.7\nassessment".getBytes());
        mvc.perform(
                        multipart("/api/v1/disputes/" + CASE + "/documents")
                                .file(pdf)
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("receipt.pdf"))
                .andExpect(jsonPath("$.scanStatus").doesNotExist());
        mvc.perform(
                        get("/api/v1/disputes/" + CASE + "/documents")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT))))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(
                        multipart("/api/v1/disputes/" + CASE + "/documents")
                                .file(pdf)
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(ALEX_SUBJECT))))
                .andExpect(status().isNotFound());
        var spoofed =
                new MockMultipartFile(
                        "file", "receipt.pdf", "application/pdf", "not a PDF".getBytes());
        mvc.perform(
                        multipart("/api/v1/disputes/" + CASE + "/documents")
                                .file(spoofed)
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT))))
                .andExpect(status().isUnsupportedMediaType());
        var huge =
                new MockMultipartFile("file", "receipt.pdf", "application/pdf", new byte[5242881]);
        mvc.perform(
                        multipart("/api/v1/disputes/" + CASE + "/documents")
                                .file(huge)
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT))))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void validatesRequiredIdempotencyKeyDetailsAndMalformedInput() throws Exception {
        mvc.perform(
                        post("/api/v1/disputes")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/disputes")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT)))
                                .header("Idempotency-Key", "valid-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        BODY.replace(
                                                "I do not recognise this card purchase.",
                                                "          ")))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/disputes")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT)))
                                .header("Idempotency-Key", "valid-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void preservesNotBlankValidationForUnicodeWhitespace() throws Exception {
        mvc.perform(
                        post("/api/v1/disputes")
                                .with(
                                        jwt().jwt(
                                                        t ->
                                                                t.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(SHANNON_SUBJECT)))
                                .header("Idempotency-Key", "\u2003".repeat(8))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsBrowserPreflightWithTheRequiredIdempotencyHeader() throws Exception {
        mvc.perform(
                        options("/api/v1/disputes")
                                .header("Origin", "http://localhost:4200")
                                .header("Access-Control-Request-Method", "POST")
                                .header(
                                        "Access-Control-Request-Headers",
                                        "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(
                        header().string(
                                        "Access-Control-Allow-Headers",
                                        containsString("idempotency-key")));
    }

    @Test
    void servesContractEndpointsAndSwaggerUi() throws Exception {
        mvc.perform(get("/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("openapi: 3.0.")));
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
