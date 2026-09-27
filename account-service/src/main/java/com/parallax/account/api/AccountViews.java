package com.parallax.account.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response shapes for account-service (SPEC §15). Money in cents; camelCase JSON. */
public final class AccountViews {

    private AccountViews() {
    }

    /** A row of GET /api/v1/accounts and the head of the detail. */
    public record AccountSummary(
            String accountId,
            String applicationId,
            String displayName,
            String product,
            int creditLimit,
            long balanceCents,
            double utilization,
            String status,
            int daysPastDue,
            Instant openedAt) {
    }

    /** GET /api/v1/accounts/{id}: the summary plus APR, recent statements, transactions and CLI history. */
    public record AccountDetail(
            AccountSummary account,
            int aprBps,
            List<StatementView> statements,
            List<TransactionView> transactions,
            List<CliRequestView> cliRequests) {
    }

    public record StatementView(
            LocalDate periodEnd,
            long closingBalanceCents,
            long minimumDueCents,
            LocalDate dueDate,
            long paidCents,
            Boolean paidOnTime) {
    }

    public record TransactionView(
            LocalDate postedAt,
            String type,
            long amountCents,
            String description) {
    }

    public record CliRequestView(
            int requestedLimit,
            String outcome,
            int newLimit,
            List<String> reasons,
            boolean applied,
            String createdBy,
            Instant createdAt) {
    }

    /** POST /api/v1/accounts/{id}/cli-requests result. */
    public record CliRequestResult(String outcome, int newLimit, List<String> reasons, boolean applied) {
    }

    /** A page of accounts: {items, page, size, total}. */
    public record AccountPage(List<AccountSummary> items, int page, int size, long total) {
    }
}
