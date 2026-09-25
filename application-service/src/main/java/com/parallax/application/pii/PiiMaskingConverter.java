package com.parallax.application.pii;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

import java.util.regex.Pattern;

/**
 * Logback converter that scrubs PII from log output (SPEC §9, invariant 4): 9-digit runs (SSNs),
 * email addresses and ISO dates are replaced with fixed masks. Wired into every appender's pattern
 * in logback-spring.xml, so a stray PII value in a log message never reaches disk or console.
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
