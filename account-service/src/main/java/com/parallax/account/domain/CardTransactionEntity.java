package com.parallax.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** The {@code card_transaction} table (SPEC §14): PURCHASE, PAYMENT or INTEREST. Append-only. */
@Entity
@Table(name = "card_transaction")
public class CardTransactionEntity {

    /** Transaction types (SPEC §13). */
    public static final String PURCHASE = "PURCHASE";
    public static final String PAYMENT = "PAYMENT";
    public static final String INTEREST = "INTEREST";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "posted_at", nullable = false)
    private LocalDate postedAt;

    @Column(name = "type", nullable = false, length = 12)
    private String type;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "description", length = 120)
    private String description;

    public CardTransactionEntity() {
    }

    public CardTransactionEntity(Long accountId, LocalDate postedAt, String type, long amountCents,
                                 String description) {
        this.accountId = accountId;
        this.postedAt = postedAt;
        this.type = type;
        this.amountCents = amountCents;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public LocalDate getPostedAt() {
        return postedAt;
    }

    public String getType() {
        return type;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getDescription() {
        return description;
    }
}
