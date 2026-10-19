package za.co.capitecbank.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import za.co.capitecbank.model.CustomerIdentity;

import java.util.Optional;
import java.util.UUID;

public interface CustomerIdentityRepository extends JpaRepository<CustomerIdentity, UUID> {

    @EntityGraph(attributePaths = "customer")
    Optional<CustomerIdentity> findByIssuerAndSubject(final String issuer, final String subject);
}
