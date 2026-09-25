package com.parallax.application.domain;

/** Custom repository operations backed by JdbcTemplate (SPEC §14 public id sequence). */
public interface ApplicationRepositoryCustom {

    /** {@code "APP-" + nextval('application_public_seq')}. */
    String nextPublicId();
}
