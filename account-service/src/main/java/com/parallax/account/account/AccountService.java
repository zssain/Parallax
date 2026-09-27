package com.parallax.account.account;

import com.parallax.account.api.AccountViews.AccountDetail;
import com.parallax.account.api.AccountViews.AccountPage;
import com.parallax.account.api.AccountViews.AccountSummary;
import com.parallax.account.api.AccountViews.StatementView;
import com.parallax.account.api.AccountViews.TransactionView;
import com.parallax.account.cli.CliRequestStore;
import com.parallax.account.domain.AccountEntity;
import com.parallax.account.domain.AccountRepository;
import com.parallax.account.domain.AccountStatus;
import com.parallax.account.domain.CardTransactionRepository;
import com.parallax.account.domain.StatementRepository;
import com.parallax.account.error.ApiProblem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Opening accounts and reading them (SPEC §13, §15). Opening is idempotent by applicationId; reads build
 * the list and detail shapes. Money is in cents; utilization = balance / limit to four decimals.
 */
@Service
public class AccountService {

    private static final int DEFAULT_APR_BPS = 2499;
    private static final int STATEMENTS_IN_DETAIL = 12;
    private static final int TRANSACTIONS_IN_DETAIL = 20;

    private final AccountRepository accountRepository;
    private final StatementRepository statementRepository;
    private final CardTransactionRepository transactionRepository;
    private final CliRequestStore cliRequestStore;
    private final JdbcTemplate jdbc;

    public AccountService(AccountRepository accountRepository, StatementRepository statementRepository,
                          CardTransactionRepository transactionRepository, CliRequestStore cliRequestStore,
                          JdbcTemplate jdbc) {
        this.accountRepository = accountRepository;
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
        this.cliRequestStore = cliRequestStore;
        this.jdbc = jdbc;
    }

    /** Open an account for an application, or return the existing one (idempotent by applicationId). */
    @Transactional
    public OpenResult open(String applicationId, String displayName, String product, int creditLimit,
                           Integer annualIncome, Integer monthlyHousing, Integer monthlyDebt) {
        return accountRepository.findByApplicationId(applicationId)
                .map(existing -> new OpenResult(existing, false))
                .orElseGet(() -> new OpenResult(create(applicationId, displayName, product, creditLimit,
                        annualIncome, monthlyHousing, monthlyDebt), true));
    }

    private AccountEntity create(String applicationId, String displayName, String product, int creditLimit,
                                 Integer annualIncome, Integer monthlyHousing, Integer monthlyDebt) {
        long seq = jdbc.queryForObject("SELECT nextval('account_public_seq')", Long.class);
        AccountEntity account = new AccountEntity();
        account.setPublicId("ACC-" + seq);
        account.setApplicationId(applicationId);
        account.setDisplayName(displayName);
        account.setProduct(product);
        account.setCreditLimit(creditLimit);
        account.setBalanceCents(0);
        account.setAprBps(DEFAULT_APR_BPS);
        account.setOpenedAt(Instant.now());
        account.setStatementClock(LocalDate.now(ZoneOffset.UTC));
        account.setStatus(AccountStatus.CURRENT);
        account.setDaysPastDue(0);
        account.setAnnualIncome(annualIncome);
        account.setMonthlyHousing(monthlyHousing);
        account.setMonthlyDebt(monthlyDebt);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public AccountPage list(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "openedAt"));
        Page<AccountEntity> found = (status == null || status.isBlank())
                ? accountRepository.findAll(pageable)
                : accountRepository.findByStatus(AccountStatus.valueOf(status), pageable);
        List<AccountSummary> items = found.getContent().stream().map(this::toSummary).toList();
        return new AccountPage(items, page, size, found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AccountDetail detail(String publicId) {
        return detail(require(publicId));
    }

    public AccountDetail detail(AccountEntity account) {
        List<StatementView> statements = statementRepository
                .findByAccountIdOrderByPeriodEndDescIdDesc(account.getId()).stream()
                .limit(STATEMENTS_IN_DETAIL)
                .map(s -> new StatementView(s.getPeriodEnd(), s.getClosingBalanceCents(), s.getMinimumDueCents(),
                        s.getDueDate(), s.getPaidCents(), s.getPaidOnTime()))
                .toList();
        List<TransactionView> transactions = transactionRepository
                .findByAccountIdOrderByPostedAtDescIdDesc(account.getId()).stream()
                .limit(TRANSACTIONS_IN_DETAIL)
                .map(t -> new TransactionView(t.getPostedAt(), t.getType(), t.getAmountCents(), t.getDescription()))
                .toList();
        return new AccountDetail(toSummary(account), account.getAprBps(), statements, transactions,
                cliRequestStore.listByAccount(account.getId()));
    }

    public AccountSummary toSummary(AccountEntity account) {
        return new AccountSummary(account.getPublicId(), account.getApplicationId(), account.getDisplayName(),
                account.getProduct(), account.getCreditLimit(), account.getBalanceCents(),
                utilization(account), account.getStatus().name(), account.getDaysPastDue(), account.getOpenedAt());
    }

    /** utilization = balance / limit, four decimals (SPEC §15); zero when the limit is zero. */
    public static double utilization(AccountEntity account) {
        if (account.getCreditLimit() <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(account.getBalanceCents())
                .divide(BigDecimal.valueOf(account.getCreditLimit() * 100L), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public AccountEntity require(String publicId) {
        return accountRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiProblem(HttpStatus.NOT_FOUND, "Not Found", "No account " + publicId));
    }

    public AccountEntity save(AccountEntity account) {
        return accountRepository.save(account);
    }

    /** Whether the account was newly created (201) or already existed (200). */
    public record OpenResult(AccountEntity account, boolean created) {
    }
}
