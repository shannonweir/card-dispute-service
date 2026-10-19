package za.co.capitecbank.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import za.co.carddisputeportalservice.generated.model.DisputeReason;
import za.co.carddisputeportalservice.generated.model.DisputeStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "disputes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dispute {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionRecord transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeReason reason;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant resolvedAt;

    @Column(length = 2000)
    private String resolutionNote;

    @Version
    @Getter(AccessLevel.NONE)
    private long version;

    @OneToMany(mappedBy = "dispute", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt ASC")
    private List<DisputeEvent> events = new ArrayList<>();

    public Dispute(final TransactionRecord transaction, final DisputeReason reason, final String description, final Instant now) {
        this.id = UUID.randomUUID();
        this.reference = "DSP-" + now.atOffset(java.time.ZoneOffset.UTC).getYear() + "-" + id;
        this.transaction = transaction;
        this.reason = reason;
        this.description = description;
        this.status = DisputeStatus.SUBMITTED;
        this.createdAt = now;
        this.updatedAt = now;
        addEvent(
                DisputeStatus.SUBMITTED,
                "Dispute submitted",
                "We received your dispute and assigned a case reference.",
                now);
    }

    public void addEvent(final DisputeStatus newStatus, final String title, final String detail, final Instant occurredAt) {
        this.status = newStatus;
        this.updatedAt = occurredAt;
        this.events.add(new DisputeEvent(this, newStatus, title, detail, occurredAt));
    }

    public List<DisputeEvent> getEvents() {
        return List.copyOf(events);
    }
}
