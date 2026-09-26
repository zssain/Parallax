package com.parallax.application.seed;

import com.parallax.engine.model.EngineInput;

import java.time.Instant;

/** One record to ingest as SEED history: its index, instant, engine input and known default outcome. */
record SeedRecord(int i, Instant createdAt, EngineInput input, boolean defaulted) {
}
