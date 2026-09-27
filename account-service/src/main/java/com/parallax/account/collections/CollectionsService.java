package com.parallax.account.collections;

import com.parallax.account.account.AccountService;
import com.parallax.account.collections.CollectionsViews.BucketSummary;
import com.parallax.account.collections.CollectionsViews.CollectionItem;
import com.parallax.account.collections.CollectionsViews.CollectionsSummary;
import com.parallax.account.domain.AccountEntity;
import com.parallax.account.domain.AccountRepository;
import com.parallax.account.domain.CollectionActionEntity;
import com.parallax.account.domain.CollectionActionRepository;
import com.parallax.account.domain.StatementEntity;
import com.parallax.account.domain.StatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The collections work queue (SPEC §13, §15): buckets past-due accounts, computes the amount due and a
 * priority, and records collection actions. Amount due = the newest statement's minimum plus, for older
 * statements not paid on time, the shortfall against their minimum.
 */
@Service
public class CollectionsService {

    private final AccountRepository accountRepository;
    private final StatementRepository statementRepository;
    private final CollectionActionRepository actionRepository;
    private final AccountService accountService;

    public CollectionsService(AccountRepository accountRepository, StatementRepository statementRepository,
                              CollectionActionRepository actionRepository, AccountService accountService) {
        this.accountRepository = accountRepository;
        this.statementRepository = statementRepository;
        this.actionRepository = actionRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public CollectionsSummary summary() {
        Map<String, long[]> byBucket = new LinkedHashMap<>();
        for (String bucket : Buckets.ALL) {
            byBucket.put(bucket, new long[]{0, 0}); // {count, amountDueCents}
        }
        for (AccountEntity account : accountRepository.findByDaysPastDueGreaterThanOrderByDaysPastDueDesc(0)) {
            String bucket = Buckets.of(account.getDaysPastDue());
            long[] cell = byBucket.get(bucket);
            cell[0] += 1;
            cell[1] += amountDueCents(account.getId());
        }
        List<BucketSummary> buckets = new ArrayList<>();
        for (String bucket : Buckets.ALL) {
            long[] cell = byBucket.get(bucket);
            buckets.add(new BucketSummary(bucket, cell[0], cell[1]));
        }
        return new CollectionsSummary(buckets);
    }

    @Transactional(readOnly = true)
    public List<CollectionItem> queue(String bucketFilter) {
        List<CollectionItem> items = new ArrayList<>();
        for (AccountEntity account : accountRepository.findByDaysPastDueGreaterThanOrderByDaysPastDueDesc(0)) {
            String bucket = Buckets.of(account.getDaysPastDue());
            if (bucketFilter != null && !bucketFilter.isBlank() && !bucketFilter.equals(bucket)) {
                continue;
            }
            Instant lastContactAt = actionRepository
                    .findFirstByAccountIdOrderByCreatedAtDescIdDesc(account.getId())
                    .map(CollectionActionEntity::getCreatedAt).orElse(null);
            items.add(new CollectionItem(account.getPublicId(), account.getDisplayName(),
                    account.getDaysPastDue(), bucket, amountDueCents(account.getId()),
                    account.getBalanceCents(), lastContactAt,
                    Buckets.priority(account.getDaysPastDue(), account.getBalanceCents())));
        }
        return items;
    }

    @Transactional
    public void recordAction(String publicId, String type, String note, String username) {
        AccountEntity account = accountService.require(publicId);
        CollectionActionEntity action = new CollectionActionEntity();
        action.setAccountId(account.getId());
        action.setType(type);
        action.setNote(note);
        action.setCreatedBy(username);
        action.setCreatedAt(Instant.now());
        actionRepository.save(action);
    }

    /** newest.minimum + Σ over older statements not paid on time of max(0, minimum − paid) (SPEC §13). */
    private long amountDueCents(Long accountId) {
        List<StatementEntity> statements =
                statementRepository.findByAccountIdOrderByPeriodEndDescIdDesc(accountId);
        if (statements.isEmpty()) {
            return 0;
        }
        long total = statements.get(0).getMinimumDueCents();
        for (StatementEntity older : statements.subList(1, statements.size())) {
            if (!Boolean.TRUE.equals(older.getPaidOnTime())) {
                total += Math.max(0, older.getMinimumDueCents() - older.getPaidCents());
            }
        }
        return total;
    }
}
