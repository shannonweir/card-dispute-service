package za.co.carddisputeportalservice.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import za.co.capitecbank.model.Customer;
import za.co.capitecbank.model.CustomerIdentity;
import za.co.capitecbank.repository.AccountRepository;
import za.co.capitecbank.repository.CustomerIdentityRepository;
import za.co.capitecbank.security.CurrentCustomer;
import za.co.carddisputeportalservice.generated.model.CustomerType;

import java.util.*;

class CurrentCustomerTest {
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesOneCustomerFromIssuerAndSubjectAndNeverFromEmail() {
        var identities = mock(CustomerIdentityRepository.class);
        var current = new CurrentCustomer(identities, mock(AccountRepository.class));
        assertThatThrownBy(current::id)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        authenticate("https://issuer.example.test/", "person");
        var customer =
                new Customer(
                        UUID.randomUUID(), CustomerType.INDIVIDUAL, "Alex", "contact@example.test");
        when(identities.findByIssuerAndSubject("https://issuer.example.test/", "person"))
                .thenReturn(
                        Optional.of(
                                new CustomerIdentity(
                                        UUID.randomUUID(),
                                        "https://issuer.example.test/",
                                        "person",
                                        customer)));
        assertThat(current.id()).isEqualTo(customer.getId());
        authenticate("https://other-issuer.example/", "person");
        assertThatThrownBy(current::id).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unmappedLoginIsNotAssignedToACustomer() {
        var current =
                new CurrentCustomer(
                        mock(CustomerIdentityRepository.class), mock(AccountRepository.class));
        authenticate("https://issuer.example.test/", "unmapped");
        assertThatThrownBy(current::id).isInstanceOf(AccessDeniedException.class);
    }

    private void authenticate(final String issuer, final String subject) {
        var jwt =
                Jwt.withTokenValue("validated")
                        .header("alg", "RS256")
                        .issuer(issuer)
                        .subject(subject)
                        .claim("email", "alex@example.com")
                        .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
