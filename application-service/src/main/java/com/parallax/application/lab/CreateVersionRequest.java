package com.parallax.application.lab;

import com.parallax.engine.model.RuleConfig;

/**
 * Body of {@code POST /api/v1/lab/versions} (SPEC §15). Both fields are optional: a null {@code config}
 * defaults to a copy of the LIVE config, and a null {@code note} defaults to "Candidate drafted from <live>.".
 */
public record CreateVersionRequest(RuleConfig config, String note) {
}
