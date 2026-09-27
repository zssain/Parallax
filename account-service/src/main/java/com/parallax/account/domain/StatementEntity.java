package com.parallax.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** The {@code statement} table (SPEC §14): one closed billing cycle. */
@Entity
@Table(name = "statement")
public class StatementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "closing_balance_cents", nullable = false)
    private long closingBalanceCents;

    @Column(name = "interest_cents", nullable = false)
    private long interestCents;

    @Column(name = "minimum_due_cents", nullable = false)
    private long minimumDueCents;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_cents", nullable = false)
    private long paidCents;

    @Column(name = "paid_on_time")
    private Boolean paidOnTime;

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public long getClosingBalanceCents() {
        return closingBalanceCents;
    }

    public void setClosingBalanceCents(long closingBalanceCents) {
        this.closingBalanceCents = closingBalanceCents;
    }

    public long getInterestCents() {
        return interestCents;
    }

    public void setInterestCents(long interestCents) {
        this.interestCents = interestCents;
    }

    public long getMinimumDueCents() {
        return minimumDueCents;
    }

    public void setMinimumDueCents(long minimumDueCents) {
        this.minimumDueCents = minimumDueCents;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public long getPaidCents() {
        return paidCents;
    }

    public void setPaidCents(long paidCents) {
        this.paidCents = paidCents;
    }

    public Boolean getPaidOnTime() {
        return paidOnTime;
    }

    public void setPaidOnTime(Boolean paidOnTime) {
        this.paidOnTime = paidOnTime;
    }
}
