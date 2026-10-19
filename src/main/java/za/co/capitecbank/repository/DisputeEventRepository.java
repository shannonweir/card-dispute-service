package za.co.capitecbank.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import za.co.capitecbank.model.DisputeEvent;

import java.util.List;
import java.util.UUID;

public interface DisputeEventRepository extends JpaRepository<DisputeEvent, UUID> {

    List<DisputeEvent> findByDisputeIdOrderByOccurredAtAscIdAsc(final UUID disputeId);
}
