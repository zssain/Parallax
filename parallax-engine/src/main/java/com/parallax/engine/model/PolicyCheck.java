package com.parallax.engine.model;

/** The result of one policy check (SPEC §4), e.g. legal capacity or ability to pay. */
public record PolicyCheck(ReasonCode code, String name, boolean passed, String detail) {
}
