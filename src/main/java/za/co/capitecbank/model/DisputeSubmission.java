package za.co.capitecbank.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "dispute_submissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor
public class DisputeSubmission {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    @NonNull
    private UUID customerId;

    @Column(nullable = false, length = 128)
    @NonNull
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    @NonNull
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    @NonNull
    private Dispute dispute;
}
