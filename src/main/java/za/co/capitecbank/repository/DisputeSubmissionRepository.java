package za.co.capitecbank.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import za.co.capitecbank.model.DisputeSubmission;

import java.util.Optional;
import java.util.UUID;

public interface DisputeSubmissionRepository extends JpaRepository<DisputeSubmission, UUID> {

    Optional<DisputeSubmission> findByCustomerIdAndIdempotencyKey(final UUID customerId, final String key);
}
