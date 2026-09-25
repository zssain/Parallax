package com.parallax.application.query;

import com.parallax.application.ledger.VerifyResult;
import com.parallax.application.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ledger read + demo endpoints for the Decision-ledger screen (SPEC §15). */
@RestController
public class LedgerController {

    private final LedgerQueryService ledgerQueryService;
    private final CurrentUser currentUser;

    public LedgerController(LedgerQueryService ledgerQueryService, CurrentUser currentUser) {
        this.ledgerQueryService = ledgerQueryService;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/ledger")
    public LedgerViews.ListResponse list(
            @RequestParam(required = false, defaultValue = "ALL") String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ledgerQueryService.list(currentUser.role(), source, page, size);
    }

    @GetMapping("/api/v1/ledger/stats")
    public LedgerViews.Stats stats() {
        return ledgerQueryService.stats();
    }

    @GetMapping("/api/v1/ledger/verify")
    public VerifyResult verify() {
        return ledgerQueryService.verify();
    }

    @PostMapping("/api/v1/ledger/demo/attempt-update")
    public LedgerViews.DemoUpdate attemptUpdate() {
        return ledgerQueryService.attemptUpdate();
    }

    @PostMapping("/api/v1/ledger/demo/tamper-simulation")
    public LedgerViews.TamperSimulation tamperSimulation() {
        return ledgerQueryService.tamperSimulation();
    }
}
