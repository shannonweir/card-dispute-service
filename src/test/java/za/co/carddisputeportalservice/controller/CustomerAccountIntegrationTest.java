package za.co.carddisputeportalservice.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import za.co.capitecbank.CardDisputePortalServiceApplication;
import za.co.carddisputeportalservice.security.TestJwtConfig;

@SpringBootTest(classes = CardDisputePortalServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class CustomerAccountIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private static final String CURRENT = "98ae225d-446d-4b4a-b860-67ea49abafaf";
    private static final String CREDIT = "14facb0b-5f73-490d-9915-9a72d67ef943";
    private static final String OTHER_ACCOUNT = "b4ddaf8b-2cc0-48d2-9e5b-cd382cb55aaa";

    private org.springframework.test.web.servlet.request.RequestPostProcessor shannon() {
        return jwt().jwt(
                        t ->
                                t.issuer("https://issuer.example.test/")
                                        .subject("auth0|shannon-test"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor alex() {
        return jwt().jwt(t -> t.issuer("https://issuer.example.test/").subject("auth0|alex-test"));
    }

    @Test
    void loginResolvesOneIndividualCustomerWithMultipleAccounts() throws Exception {
        mvc.perform(get("/api/v1/customer").with(shannon()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("f19f1d66-263f-4fd4-a3d5-1638b308e5f3"))
                .andExpect(jsonPath("$.displayName").value("Shannon Weir"))
                .andExpect(jsonPath("$.type").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.accounts", hasSize(3)))
                .andExpect(
                        jsonPath(
                                "$.accounts[*].type",
                                containsInAnyOrder("CURRENT", "CREDIT_CARD", "SAVINGS")));
        mvc.perform(get("/api/v1/transactions").with(shannon()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(10));
    }

    @Test
    void secondLoginResolvesOnlyItsOwnCustomerAndAccounts() throws Exception {
        mvc.perform(get("/api/v1/customer").with(alex()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alex Morgan"))
                .andExpect(jsonPath("$.type").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.accounts", hasSize(2)));
        mvc.perform(get("/api/v1/transactions").with(alex()))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.content[*].customerId",
                                everyItem(is("bb7f592a-b8dc-44f4-a176-59b405d3dfb7"))));
        mvc.perform(get("/api/v1/transactions/87e3ad69-be2b-483b-b1e5-82fa193ef13d").with(alex()))
                .andExpect(status().isNotFound());
    }

    @Test
    void filtersTransactionsCasesAndSummaryByAnOwnedAccount() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("accountId", CURRENT).with(shannon()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[*].accountId", everyItem(is(CURRENT))));
        mvc.perform(get("/api/v1/transactions").param("accountId", CREDIT).with(shannon()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get("/api/v1/disputes").param("accountId", CURRENT).with(shannon()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/disputes/summary").param("accountId", CURRENT).with(shannon()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(5))
                .andExpect(jsonPath("$.disputesSubmitted").value(2));
    }

    @Test
    void cannotSelectAnotherCustomersAccount() throws Exception {
        for (String endpoint : java.util.List.of("transactions", "disputes", "disputes/summary")) {
            mvc.perform(
                            get("/api/v1/" + endpoint)
                                    .param("accountId", OTHER_ACCOUNT)
                                    .with(shannon()))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/transactions").param("accountId", "not-a-uuid").with(shannon()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fixtureCustomersHaveTheCorrectContactEmails() {
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT contact_email FROM customers WHERE display_name='Shannon"
                                        + " Weir'",
                                String.class))
                .isEqualTo("shannonweir@capitecbank.co.za");
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT contact_email FROM customers WHERE display_name='Alex"
                                        + " Morgan'",
                                String.class))
                .isEqualTo("alexmorgan@capitecbank.co.za");
    }

    @Test
    @Transactional
    void emailChangesDoNotChangeOwnership() throws Exception {
        jdbc.update(
                "UPDATE customers SET contact_email='changed@example.test' WHERE id=?",
                java.util.UUID.fromString("f19f1d66-263f-4fd4-a3d5-1638b308e5f3"));
        mvc.perform(get("/api/v1/transactions").with(shannon()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(10));
    }
}
