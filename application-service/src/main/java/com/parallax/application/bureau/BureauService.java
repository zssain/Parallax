package com.parallax.application.bureau;

import com.parallax.application.domain.ApplicationEntity;
import com.parallax.application.domain.BureauPullEntity;
import com.parallax.application.domain.BureauPullRepository;
import com.parallax.application.intake.ApplicationRequest;
import com.parallax.application.json.CanonicalJson;
import com.parallax.bureau.contract.CreditReportRequest;
import com.parallax.bureau.contract.CreditReportResponse;
import com.parallax.bureau.contract.PullTypeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Fetches a credit report for an application (SPEC §3 step 4, §8). Reuses a HARD pull for the same
 * ssn_token within the configured window; otherwise calls SOAP and persists the pull. A transport
 * failure surfaces as {@link BureauUnavailableException}.
 */
@Service
public class BureauService {

    private static final String HARD = PullTypeEnum.HARD.value();

    private final BureauClient bureauClient;
    private final BureauPullRepository pullRepository;
    private final CanonicalJson canonicalJson;
    private final int reuseDays;

    public BureauService(BureauClient bureauClient, BureauPullRepository pullRepository,
                         CanonicalJson canonicalJson,
                         @Value("${parallax.bureau.reuse-days:30}") int reuseDays) {
        this.bureauClient = bureauClient;
        this.pullRepository = pullRepository;
        this.canonicalJson = canonicalJson;
        this.reuseDays = reuseDays;
    }

    public int reuseDays() {
        return reuseDays;
    }

    public BureauReport pullFor(ApplicationEntity application, ApplicationRequest form) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(reuseDays)).truncatedTo(ChronoUnit.MICROS);
        return pullRepository
                .findFirstBySsnTokenAndPullTypeAndPulledAtGreaterThanEqualOrderByPulledAtDesc(
                        application.getSsnToken(), HARD, cutoff)
                .map(this::reuse)
                .orElseGet(() -> freshPull(application, form));
    }

    private BureauReport reuse(BureauPullEntity pull) {
        BureauAttributes a = canonicalJson.read(pull.getAttributes(), BureauAttributes.class);
        return new BureauReport(pull.getId(), pull.getPullType(), true, pull.getProfile(),
                a.fileAddress(), a.ssnIssuanceYear(), a.deceased(), a.openTradelines(), a.inquiries6m(),
                a.delinquencies24m(), a.revolvingUtilization(), a.fileAgeMonths(), pull.getRawXml());
    }

    private BureauReport freshPull(ApplicationEntity application, ApplicationRequest form) {
        CreditReportResponse response = bureauClient.pull(buildRequest(form));
        String rawXml = bureauClient.marshalToXml(response);

        BureauAttributes attributes = new BureauAttributes(
                response.getFileAddress(), response.getSsnIssuanceYear(), response.isDeceasedIndicator(),
                response.getOpenTradelines(), response.getInquiries6M(), response.getDelinquencies24M(),
                response.getRevolvingUtilization().doubleValue(), response.getFileAgeMonths());

        pullRepository.save(new BureauPullEntity(
                response.getPullId(), application.getSsnToken(), HARD, response.getProfileLabel(),
                Instant.now().truncatedTo(ChronoUnit.MICROS), canonicalJson.write(attributes), rawXml));

        return new BureauReport(response.getPullId(), HARD, false, response.getProfileLabel(),
                attributes.fileAddress(), attributes.ssnIssuanceYear(), attributes.deceased(),
                attributes.openTradelines(), attributes.inquiries6m(), attributes.delinquencies24m(),
                attributes.revolvingUtilization(), attributes.fileAgeMonths(), rawXml);
    }

    private CreditReportRequest buildRequest(ApplicationRequest form) {
        CreditReportRequest request = new CreditReportRequest();
        request.setSsn(form.ssn());
        request.setFirstName(form.firstName());
        request.setLastName(form.lastName());
        request.setDateOfBirth(toXmlDate(form.dateOfBirth()));
        request.setAddress(form.address());
        request.setPullType(PullTypeEnum.HARD);
        return request;
    }

    private static XMLGregorianCalendar toXmlDate(LocalDate date) {
        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendarDate(
                    date.getYear(), date.getMonthValue(), date.getDayOfMonth(), DatatypeConstants.FIELD_UNDEFINED);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build XML date", e);
        }
    }
}
