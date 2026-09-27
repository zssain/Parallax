package com.parallax.account.statement;

import com.parallax.account.api.AccountViews.AccountDetail;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dev-only simulated-month endpoint (SPEC §15). Present only under the {@code dev} profile; it drives
 * an account forward one billing cycle so the demo can produce statements, delinquency and CLI history.
 */
@RestController
@Profile("dev")
public class DevSimulationController {

    private final StatementService statementService;

    public DevSimulationController(StatementService statementService) {
        this.statementService = statementService;
    }

    @PostMapping("/api/v1/accounts/{id}/simulate-month")
    public AccountDetail simulateMonth(@PathVariable String id, @Valid @RequestBody SimulateMonthRequest body) {
        return statementService.simulateMonth(id, body.purchasesCents(), body.paymentCents(), body.payOnTime());
    }
}
