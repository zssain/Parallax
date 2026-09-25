package com.parallax.bureau;

import com.parallax.bureau.SyntheticProfiles.Profile;
import com.parallax.bureau.SyntheticProfiles.Scenario;
import com.parallax.bureau.contract.CreditReportRequest;
import com.parallax.bureau.contract.CreditReportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;

/** The SOAP endpoint: turns a synthetic SSN into a deterministic-but-unique credit report (SPEC §8). */
@Endpoint
public class BureauEndpoint {

    private static final Logger log = LoggerFactory.getLogger(BureauEndpoint.class);

    private final AtomicLong counter = new AtomicLong();

    @PayloadRoot(namespace = WebServiceConfig.NAMESPACE, localPart = "CreditReportRequest")
    @ResponsePayload
    public CreditReportResponse handle(@RequestPayload CreditReportRequest request) {
        String ssn = request.getSsn();
        Profile profile = SyntheticProfiles.profileFor(ssn);
        Scenario scenario = SyntheticProfiles.scenarioFor(ssn);
        int birthYear = request.getDateOfBirth().getYear();

        String pullId = "BP-" + sha256First8Upper(ssn + request.getPullType().value() + counter.incrementAndGet());

        CreditReportResponse response = new CreditReportResponse();
        response.setPullId(pullId);
        response.setPullType(request.getPullType());
        response.setFileAddress(scenario == Scenario.ADDRESS_MISMATCH
                ? "14 Old Mill Rd, Dayton OH"
                : request.getAddress());
        response.setSsnIssuanceYear(scenario == Scenario.SSN_BEFORE_DOB ? birthYear - 3 : birthYear + 1);
        response.setDeceasedIndicator(scenario == Scenario.DECEASED);
        response.setOpenTradelines(profile.openTradelines());
        response.setInquiries6M(profile.inquiries6m());
        response.setDelinquencies24M(profile.delinquencies24m());
        response.setRevolvingUtilization(profile.utilization());
        response.setFileAgeMonths(profile.fileAgeMonths());
        response.setProfileLabel(profile.name());

        // Never log PII (SSN, name, DOB, address) — only the pull id, profile and scenario.
        log.info("Bureau pull {} profile={} scenario={}", pullId, profile, scenario);
        return response;
    }

    private static String sha256First8Upper(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02X", b));
                if (hex.length() >= 8) {
                    break;
                }
            }
            return hex.substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
