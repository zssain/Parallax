package com.parallax.application.query;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Deterministic adverse-action notice, template AAN-v2 (SPEC §15). No LLM, no randomness. */
@Component
public class AdverseActionNotice {

    public String render(String displayName, String product, List<String> reasonDescriptions,
                         LocalDate decisionDate, long baseSeq, String ruleVersion) {
        StringBuilder reasons = new StringBuilder();
        for (int i = 0; i < reasonDescriptions.size(); i++) {
            if (i > 0) {
                reasons.append("\n");
            }
            reasons.append(i + 1).append(". ").append(reasonDescriptions.get(i));
        }

        return """
                Notice of action taken · %s

                Dear %s,

                Thank you for applying for the Parallax %s. After careful review, we are unable to approve your application at this time. The principal reasons for our decision are:

                %s

                Our decision was based in part on information from a consumer reporting agency: Parallax Mock Bureau (synthetic). The agency did not make this decision and cannot explain why it was made. You have the right to a free copy of your report within 60 days and to dispute its accuracy.

                The federal Equal Credit Opportunity Act prohibits creditors from discriminating against credit applicants on a prohibited basis.

                Template AAN-v2 · ledger #%d · rules %s
                """.formatted(decisionDate, displayName, productLabel(product), reasons, baseSeq, ruleVersion);
    }

    private static String productLabel(String product) {
        return switch (product) {
            case "REWARDS_CARD" -> "Rewards Card";
            case "STORE_CARD" -> "Store Card";
            case "HEALTHCARE_CARD" -> "Healthcare Card";
            default -> product;
        };
    }
}
