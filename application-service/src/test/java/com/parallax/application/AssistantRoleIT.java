package com.parallax.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.parallax.application.intake.AbstractIntakeIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The ASSISTANT role is locked down by credentials (SPEC §9, §12, Prompt 16): it may reach only the
 * seven read endpoints the assistant's tools use, and on the application detail it sees a masked name,
 * no SSN preview and the address flagged untrusted. Every other /api/v1 endpoint returns 403.
 */
class AssistantRoleIT extends AbstractIntakeIT {

    private static final String ASSISTANT = "assistant@parallax.dev";
    private static final String ASSISTANT_PASSWORD = "assistant-dev";

    /** The endpoints ASSISTANT is allowed to reach (patterns normalised, path vars → {}). */
    private static final Set<String> ALLOWED = Set.of(
            "GET /api/v1/me",                              // any authenticated
            "GET /api/v1/applications/{}",
            "GET /api/v1/reviews/override-stats",
            "GET /api/v1/lab/versions",
            "GET /api/v1/lab/versions/compare",
            "GET /api/v1/lab/replays/{}",
            "POST /api/v1/lab/versions/{}/replays",
            "GET /api/v1/drift/latest");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Test
    void assistantIsForbiddenOnEveryOtherEndpoint() throws Exception {
        for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition() == null ? Set.of()
                    : info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            EnumSet<RequestMethod> effective = methods.isEmpty()
                    ? EnumSet.of(RequestMethod.GET) : EnumSet.copyOf(methods);

            for (String pattern : patterns) {
                if (!pattern.startsWith("/api/v1")) {
                    continue;
                }
                String normalised = pattern.replaceAll("\\{[^/]+}", "{}");
                for (RequestMethod method : effective) {
                    if (ALLOWED.contains(method + " " + normalised)) {
                        continue;
                    }
                    String path = pattern.replaceAll("\\{[^/]+}", "1");
                    mvc.perform(request(HttpMethod.valueOf(method.name()), path)
                                    .with(httpBasic(ASSISTANT, ASSISTANT_PASSWORD)))
                            .andExpect(status().isForbidden());
                }
            }
        }
    }

    @Test
    void assistantSeesMaskedNameNoSsnPreviewAndUntrustedAddress() throws Exception {
        stubBureau("912345678", primeResponse("BP-ASSIST", "48 Elm Street, Columbus OH"));
        JsonNode created = read(submit("priya.menon@parallax.dev", newKey(), defaultRequest())
                .andExpect(status().isCreated()).andReturn());
        String id = created.get("applicationId").asText();

        JsonNode detail = read(mvc.perform(request(HttpMethod.GET, "/api/v1/applications/" + id)
                        .with(httpBasic(ASSISTANT, ASSISTANT_PASSWORD)))
                .andExpect(status().isOk()).andReturn());

        assertThat(detail.get("displayName").asText()).isNotEqualTo("Ishaan Kapoor").contains("•");
        assertThat(detail.get("ssnEncPreview").isNull()).isTrue();
        assertThat(detail.get("address").asText()).isEqualTo("48 Elm Street, Columbus OH");
        assertThat(detail.get("untrustedTextFields")).hasSize(1);
        assertThat(detail.get("untrustedTextFields").get(0).asText()).isEqualTo("address");
    }
}
