package com.parallax.application.feature;

import com.parallax.application.bureau.BureauReport;
import com.parallax.application.domain.ApplicationEntity;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.PullType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Assembles the pure {@link EngineInput} (SPEC §4) from the application, bureau report and features. */
@Component
public class EngineInputMapper {

    public EngineInput toEngineInput(ApplicationEntity application, BureauReport report, DerivedFeatures features) {
        double utilization = BigDecimal.valueOf(report.revolvingUtilization())
                .setScale(3, RoundingMode.HALF_UP)
                .doubleValue();
        return new EngineInput(
                features.age(),
                features.birthYear(),
                application.getAnnualIncome(),
                application.getMonthlyHousing(),
                application.getMonthlyDebt(),
                utilization,
                report.inquiries6m(),
                report.delinquencies24m(),
                report.openTradelines(),
                report.fileAgeMonths(),
                application.isIndependentIncome(),
                application.isBureauConsent(),
                features.addressMismatch(),
                report.ssnIssuanceYear(),
                report.deceased(),
                features.velocity24h(),
                PullType.valueOf(report.pullType()));
    }
}
