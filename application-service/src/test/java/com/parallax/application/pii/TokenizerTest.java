package com.parallax.application.pii;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TokenizerTest {

    private final Tokenizer tokenizer =
            new Tokenizer(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void ssnTokenIsDeterministicHex() {
        String token = tokenizer.ssnToken("912345678");
        assertThat(token).matches("[0-9a-f]{64}");
        assertThat(token).isEqualTo(tokenizer.ssnToken("912345678"));
    }

    @Test
    void emailHashIsCaseAndWhitespaceInsensitive() {
        assertThat(tokenizer.emailHash("  Priya.Sharma@Example.COM "))
                .isEqualTo(tokenizer.emailHash("priya.sharma@example.com"));
    }

    @Test
    void phoneHashIgnoresFormatting() {
        assertThat(tokenizer.phoneHash("(555) 010-0001"))
                .isEqualTo(tokenizer.phoneHash("5550100001"));
    }
}
