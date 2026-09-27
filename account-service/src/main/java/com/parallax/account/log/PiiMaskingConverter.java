package com.parallax.account.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

import java.util.regex.Pattern;

/**
 * Logback converter that scrubs PII from log output (SPEC §9, invariant 4): 9-digit runs, email
 * addresses and ISO dates are replaced with fixed masks. Wired into logback-spring.xml. Copied from
 * application-service rather than sharing a module (SPEC §13 build note).
 */
public class PiiMaskingConverter extends CompositeConverter<ILoggingEvent> {

    private static final Pattern SSN = Pattern.compile("\\d{9}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Override
    protected String transform(ILoggingEvent event, String in) {
        String out = EMAIL.matcher(in).replaceAll("***@***");
        out = SSN.matcher(out).replaceAll("*********");
        out = ISO_DATE.matcher(out).replaceAll("****-**-**");
        return out;
    }
}
