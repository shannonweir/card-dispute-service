package za.co.capitecbank.security;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import za.co.capitecbank.exception.CardDisputeException;
import za.co.capitecbank.exception.ErrorCode;
import za.co.capitecbank.model.Customer;
import za.co.capitecbank.repository.AccountRepository;
import za.co.capitecbank.repository.CustomerIdentityRepository;

import java.util.UUID;

/** Resolves exactly one customer from the validated login, never from request input. */
@Component
@RequiredArgsConstructor
public class CurrentCustomer {

    private final CustomerIdentityRepository identities;
    private final AccountRepository accounts;

    public Customer customer() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated())
            throw new AuthenticationCredentialsNotFoundException("Customer authentication required");

        var issuer = token.getToken().getIssuer();
        var subject = token.getToken().getSubject();

        if (issuer == null || subject == null || subject.isBlank())
            throw new AuthenticationCredentialsNotFoundException("Customer identity missing");

        return identities
                .findByIssuerAndSubject(issuer.toString(), subject)
                .map(identity -> identity.getCustomer())
                .orElseThrow(() -> new AccessDeniedException("Login is not linked to a customer"));
    }

    public UUID id() {
        return customer().getId();
    }

    public void requireAccount(final UUID accountId) {
        if (accountId != null && !accounts.existsByIdAndCustomerId(accountId, id()))
            throw new CardDisputeException(ErrorCode.NOT_FOUND, "Account was not found");
    }
}
