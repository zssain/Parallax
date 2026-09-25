package com.parallax.application.domain;

import org.springframework.jdbc.core.JdbcTemplate;

/** Implements {@link ApplicationRepositoryCustom} using the {@code application_public_seq} sequence. */
public class ApplicationRepositoryImpl implements ApplicationRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

    public ApplicationRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String nextPublicId() {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('application_public_seq')", Long.class);
        return "APP-" + seq;
    }
}
