package com.parallax.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** The {@code bureau_pull} table (SPEC §8, §14). The id is the bureau PullId ("BP-…"). */
@Entity
@Table(name = "bureau_pull")
public class BureauPullEntity {

    @Id
    @Column(name = "id")
    private String id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ssn_token", nullable = false, length = 64)
    private String ssnToken;

    @Column(name = "pull_type", nullable = false, length = 8)
    private String pullType;

    @Column(name = "profile", nullable = false, length = 16)
    private String profile;

    @Column(name = "pulled_at", nullable = false)
    private Instant pulledAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", nullable = false)
    private String attributes;

    @Column(name = "raw_xml", nullable = false)
    private String rawXml;

    protected BureauPullEntity() {
    }

    public BureauPullEntity(String id, String ssnToken, String pullType, String profile,
                            Instant pulledAt, String attributes, String rawXml) {
        this.id = id;
        this.ssnToken = ssnToken;
        this.pullType = pullType;
        this.profile = profile;
        this.pulledAt = pulledAt;
        this.attributes = attributes;
        this.rawXml = rawXml;
    }

    public String getId() {
        return id;
    }

    public String getSsnToken() {
        return ssnToken;
    }

    public String getPullType() {
        return pullType;
    }

    public String getProfile() {
        return profile;
    }

    public Instant getPulledAt() {
        return pulledAt;
    }

    public String getAttributes() {
        return attributes;
    }

    public String getRawXml() {
        return rawXml;
    }
}
