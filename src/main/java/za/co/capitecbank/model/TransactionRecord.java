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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import za.co.carddisputeportalservice.generated.model.TransactionStatus;
import za.co.carddisputeportalservice.generated.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "card_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TransactionRecord {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 160)
    private String merchantName;

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @Column(length = 160)
    private String merchantLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Column(nullable = false, length = 100)
    private String merchantCategory;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    private String currency;

    @Column(nullable = false)
    private Instant transactionTime;

    @Column(length = 4, nullable = false)
    private String cardLastFour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;
}
