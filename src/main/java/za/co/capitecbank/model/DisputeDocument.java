package za.co.capitecbank.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispute_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DisputeDocument {
    @Id private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private Instant uploadedAt;

    @Getter(AccessLevel.NONE)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    public DisputeDocument(final Dispute dispute, final String fileName, final String contentType,
                           final byte[] content, final Instant now) {
        this(
                UUID.randomUUID(),
                dispute,
                fileName,
                contentType,
                content.length,
                now,
                content.clone());
    }
}
