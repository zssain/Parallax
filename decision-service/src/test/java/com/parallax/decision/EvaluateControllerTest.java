package com.parallax.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.parallax.engine.api.EvaluateRequest;
import com.parallax.engine.config.RuleConfigs;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.PullType;
import com.parallax.engine.scoring.DecisionEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EvaluateControllerTest {

    private static final String TOKEN = "internal-dev";

    private static final EngineInput PRIYA = new EngineInput(
            31, 1995, 38000, 1300, 520, 0.82, 5, 2, 4, 30,
            true, true, false, 1996, false, 1, PullType.HARD);
    private static final EngineInput ISHAAN = new EngineInput(
            30, 1996, 64000, 1350, 280, 0.08, 0, 0, 12, 156,
            true, true, false, 1997, false, 1, PullType.HARD);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void priyaMatchesTheEngineExactly() throws Exception {
        Decision expected = DecisionEngine.evaluate(PRIYA, RuleConfigs.v1_3());
        List<String> reasons = expected.reasonCodes().stream().map(Enum::name).toList();

        mvc.perform(evaluate(new EvaluateRequest("v1.3", RuleConfigs.v1_3(), PRIYA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleVersion").value("v1.3"))
                .andExpect(jsonPath("$.engineVersion").value("engine-1.0.0"))
                .andExpect(jsonPath("$.scorecardVersion").value("sc-2.1"))
                .andExpect(jsonPath("$.decision.outcome").value(expected.outcome().name()))
                .andExpect(jsonPath("$.decision.score").value(expected.score()))
                .andExpect(jsonPath("$.decision.creditLimit").value(expected.creditLimit()))
                .andExpect(jsonPath("$.decision.atpMax").value(expected.atpMax()))
                .andExpect(jsonPath("$.decision.reasonCodes", contains(reasons.toArray())));
    }

    @Test
    void ishaanIsApprovedMatchingTheEngine() throws Exception {
        Decision expected = DecisionEngine.evaluate(ISHAAN, RuleConfigs.v1_3());

        mvc.perform(evaluate(new EvaluateRequest("v1.3", RuleConfigs.v1_3(), ISHAAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.outcome").value(expected.outcome().name()))
                .andExpect(jsonPath("$.decision.score").value(expected.score()))
                .andExpect(jsonPath("$.decision.creditLimit").value(expected.creditLimit()))
                .andExpect(jsonPath("$.decision.atpMax").value(expected.atpMax()))
                .andExpect(jsonPath("$.decision.reasonCodes", hasSize(0)));
    }

    @Test
    void invalidConfigIsUnprocessable() throws Exception {
        ObjectNode body = objectMapper.valueToTree(new EvaluateRequest("v1.3", RuleConfigs.v1_3(), ISHAAN));
        ((ObjectNode) body.get("config")).put("approveCutoff", 900); // out of 300..850

        mvc.perform(post("/internal/v1/evaluate")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0]").exists());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/internal/v1/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EvaluateRequest("v1.3", RuleConfigs.v1_3(), ISHAAN))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void outOfRangeUtilizationIsBadRequest() throws Exception {
        ObjectNode body = objectMapper.valueToTree(new EvaluateRequest("v1.3", RuleConfigs.v1_3(), ISHAAN));
        ((ObjectNode) body.get("input")).put("revolvingUtilization", 1.5);

        mvc.perform(post("/internal/v1/evaluate")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder evaluate(
            EvaluateRequest request) throws Exception {
        return post("/internal/v1/evaluate")
                .header("X-Internal-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }
}
