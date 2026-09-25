package com.parallax.application.domain;

import com.parallax.application.pii.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * The {@code application} table (SPEC §14). PII columns (name, ssn, dob, email, address) are stored
 * encrypted via {@link EncryptedStringConverter}; the token/hash/masked columns are the searchable,
 * non-reversible projections. Nothing here is ever logged.
 */
@Entity
@Table(name = "application")
public class ApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private String publicId;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "name_enc", nullable = false)
    private String name;

    @Column(name = "name_masked", nullable = false)
    private String nameMasked;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "ssn_enc", nullable = false)
    private String ssn;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ssn_token", nullable = false, length = 64)
    private String ssnToken;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ssn_last4", nullable = false, length = 4)
    private String ssnLast4;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "dob_enc", nullable = false)
    private String dob;

    @Column(name = "birth_year", nullable = false)
    private int birthYear;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email_enc")
    private String email;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address_enc", nullable = false)
    private String address;

    @Column(name = "annual_income", nullable = false)
    private int annualIncome;

    @Column(name = "monthly_housing", nullable = false)
    private int monthlyHousing;

    @Column(name = "monthly_debt", nullable = false)
    private int monthlyDebt;

    @Column(name = "independent_income", nullable = false)
    private boolean independentIncome;

    @Column(name = "bureau_consent", nullable = false)
    private boolean bureauConsent;

    @Column(name = "product", nullable = false)
    private String product;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ApplicationStatus status;

    @Column(name = "source", nullable = false, length = 8)
    private String source = "LIVE";

    @Column(name = "engine_attempts", nullable = false)
    private int engineAttempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ApplicationEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameMasked() {
        return nameMasked;
    }

    public void setNameMasked(String nameMasked) {
        this.nameMasked = nameMasked;
    }

    public String getSsn() {
        return ssn;
    }

    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    public String getSsnToken() {
        return ssnToken;
    }

    public void setSsnToken(String ssnToken) {
        this.ssnToken = ssnToken;
    }

    public String getSsnLast4() {
        return ssnLast4;
    }

    public void setSsnLast4(String ssnLast4) {
        this.ssnLast4 = ssnLast4;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public int getBirthYear() {
        return birthYear;
    }

    public void setBirthYear(int birthYear) {
        this.birthYear = birthYear;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmailHash() {
        return emailHash;
    }

    public void setEmailHash(String emailHash) {
        this.emailHash = emailHash;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getAnnualIncome() {
        return annualIncome;
    }

    public void setAnnualIncome(int annualIncome) {
        this.annualIncome = annualIncome;
    }

    public int getMonthlyHousing() {
        return monthlyHousing;
    }

    public void setMonthlyHousing(int monthlyHousing) {
        this.monthlyHousing = monthlyHousing;
    }

    public int getMonthlyDebt() {
        return monthlyDebt;
    }

    public void setMonthlyDebt(int monthlyDebt) {
        this.monthlyDebt = monthlyDebt;
    }

    public boolean isIndependentIncome() {
        return independentIncome;
    }

    public void setIndependentIncome(boolean independentIncome) {
        this.independentIncome = independentIncome;
    }

    public boolean isBureauConsent() {
        return bureauConsent;
    }

    public void setBureauConsent(boolean bureauConsent) {
        this.bureauConsent = bureauConsent;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    /** Change status through the SPEC §6 state machine (illegal transitions throw). */
    public void changeStatus(ApplicationStatus to) {
        this.status = ApplicationStateMachine.transition(this.status, to);
    }

    /** Set the initial status directly (used only when the row is first created as RECEIVED). */
    public void setInitialStatus(ApplicationStatus status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getEngineAttempts() {
        return engineAttempts;
    }

    public void setEngineAttempts(int engineAttempts) {
        this.engineAttempts = engineAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
