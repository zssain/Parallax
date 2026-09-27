package com.parallax.account.domain;

/** Lifecycle status of an account (SPEC §13, §14). */
public enum AccountStatus {
    CURRENT,
    DELINQUENT,
    CHARGED_OFF
}
