package com.parallax.application.internal;

import com.parallax.application.rules.LiveRule;
import com.parallax.application.rules.LiveRuleService;
import com.parallax.engine.model.RuleConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoint exposing the LIVE rule version and config (SPEC §15). account-service reads
 * it (with the internal token) to compute the ability-to-pay maximum for credit-line increases. Guarded
 * by {@link InternalTokenFilter}, not HTTP Basic.
 */
@RestController
public class InternalRuleConfigController {

    private final LiveRuleService liveRuleService;

    public InternalRuleConfigController(LiveRuleService liveRuleService) {
        this.liveRuleService = liveRuleService;
    }

    @GetMapping("/internal/v1/rule-config/live")
    public LiveConfig live() {
        LiveRule live = liveRuleService.current();
        return new LiveConfig(live.version(), live.config());
    }

    /** {version, config} — the shape SPEC §15 specifies for /internal/v1/rule-config/live. */
    public record LiveConfig(String version, RuleConfig config) {
    }
}
