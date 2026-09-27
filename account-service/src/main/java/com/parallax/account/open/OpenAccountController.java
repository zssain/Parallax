package com.parallax.account.open;

import com.parallax.account.account.AccountService;
import com.parallax.account.account.AccountService.OpenResult;
import com.parallax.account.api.AccountViews.AccountSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opens accounts from application-service's outbox (SPEC §15). Gated by the InternalTokenFilter, not
 * HTTP Basic. Idempotent by applicationId: a new account is 201, an existing one is 200.
 */
@RestController
public class OpenAccountController {

    private final AccountService accountService;

    public OpenAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/internal/v1/accounts")
    public ResponseEntity<AccountSummary> open(@Valid @RequestBody OpenAccountRequest request) {
        OpenResult result = accountService.open(request.applicationId(), request.displayName(),
                request.product(), request.creditLimit(), request.annualIncome(), request.monthlyHousing(),
                request.monthlyDebt());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(accountService.toSummary(result.account()));
    }
}
