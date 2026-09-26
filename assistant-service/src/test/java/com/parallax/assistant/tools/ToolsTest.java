package com.parallax.assistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parallax.assistant.client.ParallaxClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/** Each tool maps application-service fields correctly; getDecision flags the address as untrusted (SPEC §12). */
class ToolsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private ParallaxClient client;
    private ParallaxTools tools;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(ParallaxClient.class);
        tools = new ParallaxTools(client);
    }

    private JsonNode json(String text) {
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDecisionMapsFieldsAndMarksAddressUntrusted() {
        Mockito.when(client.getApplication("APP-1041")).thenReturn(json("""
                {"applicationId":"APP-1041","displayName":"P•••• S•••••","product":"REWARDS_CARD",
                 "status":"DECIDED","address":"221B Lake View Rd, Columbus OH",
                 "current":{"outcome":"DECLINED","creditLimit":0,"override":null},
                 "base":{"ruleVersion":"v1.3","score":445,
                         "reasonCodes":[{"code":"R22","description":"Delinquency"}],
                         "fraudFlags":[]}}
                """));

        ToolResult result = tools.getDecision("APP-1041");

        assertThat(result.type()).isEqualTo("tool_result");
        assertThat(result.untrustedTextFields()).containsExactly("address");
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) mapper.convertValue(result.data(),
                java.util.Map.class);
        assertThat(data.get("outcome")).isEqualTo("DECLINED");
        assertThat(data.get("score")).isEqualTo(445);
        assertThat(data.get("ruleVersion")).isEqualTo("v1.3");
        assertThat(data.get("maskedName")).isEqualTo("P•••• S•••••");
        assertThat(data.get("address")).isEqualTo("221B Lake View Rd, Columbus OH");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getReasonCodesAttachesPointsLostForRCodesAndCutoffs() {
        Mockito.when(client.getApplication("APP-1041")).thenReturn(json("""
                {"base":{"reasonCodes":[
                    {"code":"R22","description":"Delinquency","applicantFacing":true},
                    {"code":"P01","description":"Ability to pay","applicantFacing":true}]},
                 "breakdown":{"approveCutoff":680,"referCutoff":620,
                    "scoreParts":[{"code":"R22","pointsLost":130},{"code":"R31","pointsLost":45}]}}
                """));

        ToolResult result = tools.getReasonCodes("APP-1041");
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) mapper.convertValue(result.data(),
                java.util.Map.class);

        assertThat(data.get("approveCutoff")).isEqualTo(680);
        assertThat(data.get("referCutoff")).isEqualTo(620);
        java.util.List<java.util.Map<String, Object>> codes =
                (java.util.List<java.util.Map<String, Object>>) data.get("reasonCodes");
        assertThat(codes).hasSize(2);
        assertThat(codes.get(0).get("code")).isEqualTo("R22");
        assertThat(codes.get(0).get("pointsLost")).isEqualTo(130);   // R-code → points lost
        assertThat(codes.get(1).get("code")).isEqualTo("P01");
        assertThat(codes.get(1).get("pointsLost")).isNull();          // policy code → null
    }

    @Test
    void runReplayReportsWhenNoneExists() {
        Mockito.when(client.lookupReplay("v1.9")).thenReturn(null);

        ToolResult result = tools.runReplay("v1.9");
        java.util.Map<?, ?> data = mapper.convertValue(result.data(), java.util.Map.class);
        assertThat(data.get("available")).isEqualTo(false);
        assertThat(data.get("message").toString()).contains("v1.9");
    }

    @Test
    void getDriftReportSurfacesLargestContributionBin() {
        Mockito.when(client.getDriftLatest()).thenReturn(json("""
                {"psi":0.1234,"status":"watch","asOf":"2026-09-20","bins":[
                    {"from":300,"to":579,"contribution":0.02},
                    {"from":700,"to":739,"contribution":0.09},
                    {"from":780,"to":850,"contribution":0.01}]}
                """));

        ToolResult result = tools.getDriftReport();
        java.util.Map<?, ?> data = mapper.convertValue(result.data(), java.util.Map.class);
        assertThat(data.get("status")).isEqualTo("watch");
        java.util.Map<?, ?> bin = (java.util.Map<?, ?>) data.get("largestContributionBin");
        assertThat(bin.get("from")).isEqualTo(700);
    }
}
