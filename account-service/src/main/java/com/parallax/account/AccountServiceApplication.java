package com.parallax.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * account-service (SPEC §13, port 8084): opens accounts from application-service's outbox, runs
 * statements, decides credit-line increases with the pure {@code CliPolicy}, and tracks delinquency and
 * collections. Scheduling is enabled for the nightly delinquency job.
 */
@SpringBootApplication
@EnableScheduling
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
