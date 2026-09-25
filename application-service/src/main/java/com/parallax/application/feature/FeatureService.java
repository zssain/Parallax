package com.parallax.application.feature;

import com.parallax.application.bureau.BureauReport;
import com.parallax.application.domain.ApplicationEntity;
import com.parallax.application.intake.ApplicationRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;

/** Derives velocity, address mismatch and age (SPEC §3 step 5). */
@Service
public class FeatureService {

    // :createdAt is cast to timestamptz so Postgres does not infer the untyped parameter as an interval.
    private static final String VELOCITY_SQL = """
            SELECT count(*) FROM application
            WHERE created_at > (:createdAt)::timestamptz - interval '24 hours'
              AND created_at <= (:createdAt)::timestamptz
              AND (ssn_token = :t
                   OR (:e IS NOT NULL AND email_hash = :e)
                   OR (:p IS NOT NULL AND phone_hash = :p))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public FeatureService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DerivedFeatures derive(ApplicationEntity application, ApplicationRequest form, BureauReport report) {
        LocalDate dob = form.dateOfBirth();
        LocalDate asOf = application.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        int age = Period.between(dob, asOf).getYears();
        boolean addressMismatch = !normalize(form.address()).equals(normalize(report.fileAddress()));
        int velocity24h = velocity(application);
        return new DerivedFeatures(age, dob.getYear(), addressMismatch, velocity24h);
    }

    private int velocity(ApplicationEntity application) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("createdAt", application.getCreatedAt().atOffset(ZoneOffset.UTC))
                .addValue("t", application.getSsnToken())
                // Typed so a null email/phone hash still has a SQL type for the comparison.
                .addValue("e", application.getEmailHash(), Types.VARCHAR)
                .addValue("p", application.getPhoneHash(), Types.VARCHAR);
        Integer count = jdbc.queryForObject(VELOCITY_SQL, params, Integer.class);
        return count == null ? 0 : count;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
