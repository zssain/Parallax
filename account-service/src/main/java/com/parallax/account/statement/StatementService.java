package com.parallax.account.statement;

import com.parallax.account.account.AccountService;
import com.parallax.account.api.AccountViews.AccountDetail;
import com.parallax.account.domain.AccountEntity;
import com.parallax.account.domain.AccountStatus;
import com.parallax.account.domain.CardTransactionEntity;
import com.parallax.account.domain.CardTransactionRepository;
import com.parallax.account.domain.StatementEntity;
import com.parallax.account.domain.StatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Simulates one billing month (SPEC §13), a dev tool that advances an account: post purchases and a
 * payment, apply interest, close a statement, roll {@code statement_clock} forward and recompute
 * delinquency — all in one transaction, in the exact SPEC §13 order.
 */
@Service
public class StatementService {

    private static final int DELINQUENT_THRESHOLD_DAYS = 30;

    private final AccountService accountService;
    private final StatementRepository statementRepository;
    private final CardTransactionRepository transactionRepository;

    public StatementService(AccountService accountService, StatementRepository statementRepository,
                            CardTransactionRepository transactionRepository) {
        this.accountService = accountService;
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public AccountDetail simulateMonth(String publicId, long purchasesCents, long paymentCents, boolean payOnTime) {
        AccountEntity account = accountService.require(publicId);
        LocalDate clock = account.getStatementClock();
        long balance = account.getBalanceCents();

        // 1. Purchases dated statement_clock + 5 days.
        if (purchasesCents > 0) {
            transactionRepository.save(new CardTransactionEntity(account.getId(), clock.plusDays(5),
                    CardTransactionEntity.PURCHASE, purchasesCents, "Purchase"));
            balance += purchasesCents;
        }

        Optional<StatementEntity> previous =
                statementRepository.findFirstByAccountIdOrderByPeriodEndDescIdDesc(account.getId());

        // 2. Payment dated statement_clock + 20 days; it settles the previous statement.
        if (paymentCents > 0) {
            transactionRepository.save(new CardTransactionEntity(account.getId(), clock.plusDays(20),
                    CardTransactionEntity.PAYMENT, paymentCents, "Payment"));
            balance -= paymentCents;
            if (previous.isPresent()) {
                StatementEntity prev = previous.get();
                prev.setPaidCents(prev.getPaidCents() + paymentCents);
                prev.setPaidOnTime(payOnTime && prev.getPaidCents() >= prev.getMinimumDueCents());
                statementRepository.save(prev);
            }
        }

        // 3. Delinquency: +30 days if the previous statement was not paid on time, else reset.
        if (previous.isPresent() && !Boolean.TRUE.equals(previous.get().getPaidOnTime())) {
            account.setDaysPastDue(account.getDaysPastDue() + 30);
        } else {
            account.setDaysPastDue(0);
        }

        // 4. Close the period and apply interest.
        LocalDate periodEnd = clock.plusMonths(1);
        long interest = StatementMath.interest(balance, account.getAprBps());
        if (interest > 0) {
            transactionRepository.save(new CardTransactionEntity(account.getId(), periodEnd,
                    CardTransactionEntity.INTEREST, interest, "Interest"));
            balance += interest;
        }

        // 5. Minimum due and the statement row.
        long minimumDue = StatementMath.minimumDue(balance, interest);
        StatementEntity statement = new StatementEntity();
        statement.setAccountId(account.getId());
        statement.setPeriodEnd(periodEnd);
        statement.setClosingBalanceCents(balance);
        statement.setInterestCents(interest);
        statement.setMinimumDueCents(minimumDue);
        statement.setDueDate(periodEnd.plusDays(25));
        statement.setPaidCents(0);
        statement.setPaidOnTime(null);
        statementRepository.save(statement);

        // 6. Status and the advanced clock.
        account.setBalanceCents(balance);
        account.setStatus(account.getDaysPastDue() >= DELINQUENT_THRESHOLD_DAYS
                ? AccountStatus.DELINQUENT : AccountStatus.CURRENT);
        account.setStatementClock(periodEnd);
        accountService.save(account);

        return accountService.detail(account);
    }
}
