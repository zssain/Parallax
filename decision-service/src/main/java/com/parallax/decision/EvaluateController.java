package com.parallax.decision;

import com.parallax.engine.api.EvaluateRequest;
import com.parallax.engine.api.EvaluateResponse;
import com.parallax.engine.config.RuleConfigValidator;
import com.parallax.engine.model.Decision;
import com.parallax.engine.model.EngineVersion;
import com.parallax.engine.model.ScorecardVersion;
import com.parallax.engine.scoring.DecisionEngine;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The stateless decision endpoint (SPEC §15). Validates the config, runs the pure engine and returns
 * the decision with the engine and scorecard versions. No database, no clock, no randomness.
 */
@RestController
public class EvaluateController {

    @PostMapping("/internal/v1/evaluate")
    public EvaluateResponse evaluate(@RequestBody EvaluateRequest request) {
        List<String> errors = RuleConfigValidator.validate(request.config());
        if (!errors.isEmpty()) {
            throw new InvalidConfigException(errors);
        }
        Decision decision = DecisionEngine.evaluate(request.input(), request.config());
        return new EvaluateResponse(request.ruleVersion(), EngineVersion.VALUE, ScorecardVersion.VALUE, decision);
    }
}
