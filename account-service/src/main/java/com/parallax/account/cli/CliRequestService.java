package com.parallax.account.cli;

import com.parallax.account.account.AccountService;
import com.parallax.account.api.AccountViews.CliRequestResult;
import com.parallax.account.domain.AccountEntity;
import com.parallax.account.domain.StatementEntity;
import com.parallax.account.domain.StatementRepository;
import com.parallax.engine.cli.CliDecision;
import com.parallax.engine.cli.CliInput;
import com.parallax.engine.cli.CliOutcome;
import com.parallax.engine.cli.CliPolicy;
import com.parallax.engine.model.RuleConfig;
import com.parallax.engine.scoring.Affordability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Decides a credit-line increase with the pure {@link CliPolicy} (SPEC §13). It builds the CliInput from
 * the account's statements and the LIVE config's affordability, records a cli_request row, and — if the
 * outcome is applied (APPROVED, or COUNTER_OFFER the caller accepts) — raises the credit limit.
 */
@Service
public class CliRequestService {

    /** The last 12 statements excluding the newest (its payment is not yet due) — SPEC §13. */
    private static final int WINDOW = 12;

    private final AccountService accountService;
    private final StatementRepository statementRepository;
    private final RuleConfigClient ruleConfigClient;
    private final CliRequestStore cliRequestStore;

    public CliRequestService(AccountService accountService, StatementRepository statementRepository,
                             RuleConfigClient ruleConfigClient, CliRequestStore cliRequestStore) {
        this.accountService = accountService;
        this.statementRepository = statementRepository;
        this.ruleConfigClient = ruleConfigClient;
        this.cliRequestStore = cliRequestStore;
    }

    @Transactional
    public CliRequestResult request(String publicId, int requestedLimit, boolean acceptCounterOffer,
                                    String username) {
        AccountEntity account = accountService.require(publicId);

        List<StatementEntity> statements =
                statementRepository.findByAccountIdOrderByPeriodEndDescIdDesc(account.getId());
        List<StatementEntity> window = statements.size() <= 1
                ? List.of()
                : statements.subList(1, Math.min(statements.size(), 1 + WINDOW));
        int paymentsDueLast12 = window.size();
        int onTimePaymentsLast12 = (int) window.stream()
                .filter(s -> Boolean.TRUE.equals(s.getPaidOnTime())).count();

        double utilization = account.getCreditLimit() <= 0
                ? 0.0
                : account.getBalanceCents() / (account.getCreditLimit() * 100.0);

        RuleConfig config = ruleConfigClient.liveConfig();
        int atpMax = Affordability.atpMax(orZero(account.getAnnualIncome()), orZero(account.getMonthlyHousing()),
                orZero(account.getMonthlyDebt()), config);

        CliInput input = new CliInput(account.getCreditLimit(), requestedLimit,
                (int) statementRepository.countByAccountId(account.getId()),
                onTimePaymentsLast12, paymentsDueLast12, utilization, atpMax, account.getDaysPastDue() > 0);
        CliDecision decision = CliPolicy.evaluate(input);

        boolean applied = decision.outcome() == CliOutcome.APPROVED
                || (decision.outcome() == CliOutcome.COUNTER_OFFER && acceptCounterOffer);
        if (applied) {
            account.setCreditLimit(decision.newLimit());
            accountService.save(account);
        }

        cliRequestStore.insert(account.getId(), requestedLimit, decision.outcome().name(), decision.newLimit(),
                decision.reasons(), applied, username, Instant.now());

        return new CliRequestResult(decision.outcome().name(), decision.newLimit(), decision.reasons(), applied);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
