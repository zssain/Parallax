package com.parallax.application.intake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class IntakePiiIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";

    @Test
    void noPiiInLogsAndSsnStoredEncrypted(CapturedOutput output) throws Exception {
        String ssn = "912345678";
        String email = "ishaan.kapoor@example.com";
        String dob = "1996-04-18";
        stubBureau(ssn, primeResponse("BP-PII", "48 Elm Street, Columbus OH"));

        Map<String, Object> body = defaultRequest();
        body.put("email", email);
        MvcResult result = submit(USER, newKey(), body).andExpect(status().isAccepted()).andReturn();
        String applicationId = read(result).get("applicationId").asText();

        // Logs never carry PII (masking + never logging it in the first place).
        assertThat(output.getAll()).doesNotContain(ssn).doesNotContain(email).doesNotContain(dob);

        // The stored ssn_enc bytes are ciphertext — they do not contain the SSN digits.
        byte[] ssnEnc = jdbc.queryForObject(
                "SELECT ssn_enc FROM application WHERE public_id = ?", byte[].class, applicationId);
        assertThat(ssnEnc).isNotNull();
        assertThat(new String(ssnEnc, StandardCharsets.ISO_8859_1)).doesNotContain(ssn);
    }
}
