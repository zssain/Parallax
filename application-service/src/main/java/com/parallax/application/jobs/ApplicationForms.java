package com.parallax.application.jobs;

import com.parallax.application.domain.ApplicationEntity;
import com.parallax.application.intake.ApplicationRequest;
import com.parallax.application.intake.Product;

import java.time.LocalDate;

/** Rebuilds an {@link ApplicationRequest} from a stored application, for the recovery jobs (SPEC §6). */
final class ApplicationForms {

    private ApplicationForms() {
    }

    /** Decrypts the stored fields the bureau call and feature derivation need. */
    static ApplicationRequest formOf(ApplicationEntity app) {
        String[] parts = app.getName().split(" ", 2);
        String firstName = parts[0];
        String lastName = parts.length > 1 ? parts[1] : parts[0];
        return new ApplicationRequest(firstName, lastName, LocalDate.parse(app.getDob()), app.getSsn(),
                null, null, app.getAddress(), app.getAnnualIncome(), app.getMonthlyHousing(),
                app.getMonthlyDebt(), app.isIndependentIncome(), app.isBureauConsent(),
                Product.valueOf(app.getProduct()));
    }
}
