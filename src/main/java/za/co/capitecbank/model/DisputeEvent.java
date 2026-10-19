package za.co.capitecbank.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import za.co.carddisputeportalservice.generated.model.DisputeStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispute_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class DisputeEvent {

    @Id private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    @Getter(AccessLevel.NONE)
    @NonNull
    private Dispute dispute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NonNull
    private DisputeStatus status;

    @Column(nullable = false, length = 160)
    @NonNull
    private String title;

    @Column(nullable = false, length = 500)
    @NonNull
    private String detail;

    @Column(nullable = false)
    @NonNull
    private Instant occurredAt;

    @Column(nullable = false)
    private boolean actionRequired;
}
