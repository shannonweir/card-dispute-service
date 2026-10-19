package za.co.carddisputeportalservice.service;

import jakarta.persistence.EntityManager;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import za.co.capitecbank.model.Account;
import za.co.capitecbank.model.Customer;
import za.co.capitecbank.model.CustomerIdentity;
import za.co.capitecbank.model.TransactionRecord;
import za.co.carddisputeportalservice.generated.model.AccountType;
import za.co.carddisputeportalservice.generated.model.CustomerType;
import za.co.carddisputeportalservice.generated.model.TransactionStatus;
import za.co.carddisputeportalservice.generated.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

record DisputeFixtures(
        Customer customer, Account account, List<TransactionRecord> transactions, String subject) {
    static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    static DisputeFixtures create(final EntityManager em, final int count) {
        var customer =
                new Customer(
                        UUID.randomUUID(),
                        CustomerType.INDIVIDUAL,
                        "Review test",
                        "review@example.com");
        var account =
                new Account(
                        UUID.randomUUID(),
                        AccountType.CURRENT,
                        customer,
                        "Review account",
                        "•••• 1234",
                        "ZAR");
        String subject = "auth0|review-" + customer.getId();
        em.persist(customer);
        em.persist(
                new CustomerIdentity(
                        UUID.randomUUID(), "https://issuer.example.test/", subject, customer));
        em.persist(account);
        var transactions = new ArrayList<TransactionRecord>();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            var transaction =
                    new TransactionRecord(
                            id,
                            account,
                            "Merchant " + i,
                            "TX-" + id,
                            "Cape Town",
                            TransactionType.SHOPPING,
                            "Retail",
                            new BigDecimal("100.00"),
                            "ZAR",
                            NOW.minusSeconds(i),
                            "1234",
                            TransactionStatus.POSTED);
            em.persist(transaction);
            transactions.add(transaction);
        }
        return new DisputeFixtures(customer, account, transactions, subject);
    }

    void authenticate() {
        var jwt =
                Jwt.withTokenValue("test-access-token")
                        .header("alg", "RS256")
                        .issuer("https://issuer.example.test/")
                        .subject(subject)
                        .build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        SecurityContextHolder.setContext(context);
    }
}
