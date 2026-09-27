package com.parallax.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** The {@code account} table (SPEC §14). Money is in cents ({@code balanceCents}); the limit is dollars. */
@Entity
@Table(name = "account")
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private String publicId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private String applicationId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "product", nullable = false)
    private String product;

    @Column(name = "credit_limit", nullable = false)
    private int creditLimit;

    @Column(name = "balance_cents", nullable = false)
    private long balanceCents;

    @Column(name = "apr_bps", nullable = false)
    private int aprBps;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "statement_clock", nullable = false)
    private LocalDate statementClock;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AccountStatus status;

    @Column(name = "days_past_due", nullable = false)
    private int daysPastDue;

    @Column(name = "annual_income")
    private Integer annualIncome;

    @Column(name = "monthly_housing")
    private Integer monthlyHousing;

    @Column(name = "monthly_debt")
    private Integer monthlyDebt;

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public int getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(int creditLimit) {
        this.creditLimit = creditLimit;
    }

    public long getBalanceCents() {
        return balanceCents;
    }

    public void setBalanceCents(long balanceCents) {
        this.balanceCents = balanceCents;
    }

    public int getAprBps() {
        return aprBps;
    }

    public void setAprBps(int aprBps) {
        this.aprBps = aprBps;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public LocalDate getStatementClock() {
        return statementClock;
    }

    public void setStatementClock(LocalDate statementClock) {
        this.statementClock = statementClock;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public int getDaysPastDue() {
        return daysPastDue;
    }

    public void setDaysPastDue(int daysPastDue) {
        this.daysPastDue = daysPastDue;
    }

    public Integer getAnnualIncome() {
        return annualIncome;
    }

    public void setAnnualIncome(Integer annualIncome) {
        this.annualIncome = annualIncome;
    }

    public Integer getMonthlyHousing() {
        return monthlyHousing;
    }

    public void setMonthlyHousing(Integer monthlyHousing) {
        this.monthlyHousing = monthlyHousing;
    }

    public Integer getMonthlyDebt() {
        return monthlyDebt;
    }

    public void setMonthlyDebt(Integer monthlyDebt) {
        this.monthlyDebt = monthlyDebt;
    }
}
