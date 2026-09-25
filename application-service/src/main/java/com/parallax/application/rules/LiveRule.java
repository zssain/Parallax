package com.parallax.application.rules;

import com.parallax.engine.model.RuleConfig;

import java.time.Instant;

/** The current LIVE rule version: its id, parsed config and when it went live (SPEC §4, §10). */
public record LiveRule(String version, RuleConfig config, Instant since) {
}
