package za.co.capitecbank.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import za.co.capitecbank.model.DisputeDocument;

import java.util.List;
import java.util.UUID;

public interface DisputeDocumentRepository extends JpaRepository<DisputeDocument, UUID> {

    List<DisputeDocument> findByDisputeIdOrderByUploadedAtAsc(final UUID disputeId);
}
