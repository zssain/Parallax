package com.parallax.application.decide;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import com.parallax.application.query.DecisionQueryService;
import com.parallax.application.seed.HistorySeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoldenReproduceIT extends AbstractIntakeIT {

    private static final String USER = "priya.menon@parallax.dev";
    private static final String[] SSNS = {"912345678", "961234567", "937123456", "912845678", "912945678", "981234567"};
    private static final int[] BIRTH_YEARS = {1996, 1995, 1998, 1990, 1992, 1994};

    @Autowired
    HistorySeeder historySeeder;
    @Autowired
    DecisionQueryService decisionQueryService;

    @DynamicPropertySource
    static void seedProps(DynamicPropertyRegistry registry) {
        registry.add("parallax.seed.generate-count", () -> "5000");
    }

    @Test
    void goldenSetOfFiveThousandSeedsReproducesEveryTenthRow() {
        long count = historySeeder.seed().count();
        assertThat(count).isEqualTo(5000);

        int checked = 0;
        for (long seq = 10; seq <= 5000; seq += 10) {
            assertThat(decisionQueryService.reproduce(seq).identical())
                    .as("seq %d reproduces identically", seq).isEqualTo(Boolean.TRUE);
            checked++;
        }
        assertThat(checked).isEqualTo(500);
    }

    @Test
    void fiftyDecisionsAcrossAllReferenceSsnsReproduceIdentically() throws Exception {
        List<Long> seqs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            int idx = i % SSNS.length;
            String ssn = SSNS[idx];
            int birthYear = BIRTH_YEARS[idx];
            stubBureau(ssn, referenceResponse("BP-G" + i, ssn, "48 Elm Street, Columbus OH", birthYear));

            Map<String, Object> body = defaultRequest();
            body.put("ssn", ssn);
            body.put("dateOfBirth", birthYear + "-04-18");
            body.put("annualIncome", 30000 + i * 700);
            JsonNode created = read(submit(USER, newKey(), body).andExpect(status().isCreated()).andReturn());
            seqs.add(created.get("ledgerSeq").asLong());
        }

        for (long seq : seqs) {
            JsonNode reproduce = read(getAs(USER, "/api/v1/decisions/" + seq + "/reproduce").andReturn());
            assertThat(reproduce.get("identical").asBoolean()).as("seq %d reproduces", seq).isTrue();
        }
    }
}
