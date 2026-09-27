package com.parallax.account.account;

import com.parallax.account.api.AccountViews.AccountDetail;
import com.parallax.account.api.AccountViews.AccountPage;
import com.parallax.account.api.AccountViews.CliRequestResult;
import com.parallax.account.cli.CliRequestBody;
import com.parallax.account.cli.CliRequestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** Account reads and credit-line-increase requests (SPEC §15). */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final CliRequestService cliRequestService;

    public AccountController(AccountService accountService, CliRequestService cliRequestService) {
        this.accountService = accountService;
        this.cliRequestService = cliRequestService;
    }

    @GetMapping
    public AccountPage list(@RequestParam(required = false) String status,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return accountService.list(status, page, size);
    }

    @GetMapping("/{id}")
    public AccountDetail detail(@PathVariable String id) {
        return accountService.detail(id);
    }

    @PostMapping("/{id}/cli-requests")
    public CliRequestResult requestCli(@PathVariable String id, @Valid @RequestBody CliRequestBody body,
                                       Principal principal) {
        return cliRequestService.request(id, body.requestedLimit(), body.acceptCounterOffer(),
                principal.getName());
    }
}
