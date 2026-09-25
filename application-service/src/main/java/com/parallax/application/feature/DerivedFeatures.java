package com.parallax.application.feature;

/** Fraud/identity features derived from the application and its bureau report (SPEC §3 step 5). */
public record DerivedFeatures(int age, int birthYear, boolean addressMismatch, int velocity24h) {
}
