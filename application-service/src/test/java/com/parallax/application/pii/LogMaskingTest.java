package com.parallax.application.pii;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the logback masking wired via {@link PiiMaskingConverter} (loaded from logback-test.xml):
 * a log line carrying an SSN, email and ISO date leaves none of those values in the output.
 */
@ExtendWith(OutputCaptureExtension.class)
class LogMaskingTest {

    private static final Logger log = LoggerFactory.getLogger(LogMaskingTest.class);

    @Test
    void masksSsnEmailAndDate(CapturedOutput output) {
        log.info("ssn 912345678 mail a@b.com dob 1996-04-18");

        assertThat(output.getOut()).doesNotContain("912345678");
        assertThat(output.getOut()).doesNotContain("a@b.com");
        assertThat(output.getOut()).doesNotContain("1996-04-18");
        // The masks themselves are present, proving the line was logged (and scrubbed).
        assertThat(output.getOut()).contains("*********", "***@***", "****-**-**");
    }
}
