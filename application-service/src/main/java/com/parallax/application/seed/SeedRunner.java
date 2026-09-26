package com.parallax.application.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the one-shot seed (SPEC §12, Prompt 12): history first, then the demo applications,
 * then exits. Active only under the {@code seed} profile; {@code parallax.seed.run=false} disables it
 * so integration tests can invoke the seeders directly. Exits non-zero when a demo result mismatches.
 */
@Component
@Profile("seed")
@ConditionalOnProperty(name = "parallax.seed.run", matchIfMissing = true)
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final HistorySeeder historySeeder;
    private final DemoApplicationsSeeder demoApplicationsSeeder;
    private final ConfigurableApplicationContext context;

    public SeedRunner(HistorySeeder historySeeder, DemoApplicationsSeeder demoApplicationsSeeder,
                      ConfigurableApplicationContext context) {
        this.historySeeder = historySeeder;
        this.demoApplicationsSeeder = demoApplicationsSeeder;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        HistorySeeder.SeedSummary history = historySeeder.seed();
        if (history.refused()) {
            System.out.println(history.message());
            exit(0); // graceful no-op guard, not a failure
            return;
        }
        DemoApplicationsSeeder.DemoSummary demo = demoApplicationsSeeder.seed();
        if (demo.mismatches() > 0) {
            log.error("Demo seeding had {} mismatch(es); exiting non-zero.", demo.mismatches());
            exit(1);
        } else {
            exit(0);
        }
    }

    private void exit(int code) {
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
