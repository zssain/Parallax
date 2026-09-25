package com.parallax.application.ledger;

import java.util.List;
import java.util.Map;

/**
 * Input to {@link LedgerWriter#append}: everything about a row except the assigned seq, prevHash and
 * hash (SPEC §5). {@code engineInput} is either an {@code EngineInput}, a {@link PartialEngineInput},
 * or null (GOVERNANCE). A {@link Builder} keeps construction readable.
 */
public record LedgerEntry(
        LedgerKind kind,
        LedgerSource source,
        Long applicationDbId,
        String applicationPublicId,
        String ruleVersion,
        String scorecardVersion,
        String engineVersion,
        String bureauPullId,
        Boolean bureauReused,
        Object engineInput,
        String outcome,
        Integer score,
        Integer creditLimit,
        List<String> reasonCodes,
        List<String> fraudFlags,
        Integer atpMax,
        Long linkedSeq,
        Map<String, Object> overrideDetail,
        Map<String, Object> governanceDetail) {

    public static Builder builder(LedgerKind kind, LedgerSource source) {
        return new Builder(kind, source);
    }

    public static final class Builder {
        private final LedgerKind kind;
        private final LedgerSource source;
        private Long applicationDbId;
        private String applicationPublicId;
        private String ruleVersion;
        private String scorecardVersion;
        private String engineVersion;
        private String bureauPullId;
        private Boolean bureauReused;
        private Object engineInput;
        private String outcome;
        private Integer score;
        private Integer creditLimit;
        private List<String> reasonCodes;
        private List<String> fraudFlags;
        private Integer atpMax;
        private Long linkedSeq;
        private Map<String, Object> overrideDetail;
        private Map<String, Object> governanceDetail;

        private Builder(LedgerKind kind, LedgerSource source) {
            this.kind = kind;
            this.source = source;
        }

        public Builder application(Long dbId, String publicId) {
            this.applicationDbId = dbId;
            this.applicationPublicId = publicId;
            return this;
        }

        public Builder ruleVersion(String v) {
            this.ruleVersion = v;
            return this;
        }

        public Builder scorecardVersion(String v) {
            this.scorecardVersion = v;
            return this;
        }

        public Builder engineVersion(String v) {
            this.engineVersion = v;
            return this;
        }

        public Builder bureau(String pullId, Boolean reused) {
            this.bureauPullId = pullId;
            this.bureauReused = reused;
            return this;
        }

        public Builder engineInput(Object engineInput) {
            this.engineInput = engineInput;
            return this;
        }

        public Builder outcome(String outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder score(Integer score) {
            this.score = score;
            return this;
        }

        public Builder creditLimit(Integer creditLimit) {
            this.creditLimit = creditLimit;
            return this;
        }

        public Builder reasonCodes(List<String> reasonCodes) {
            this.reasonCodes = reasonCodes;
            return this;
        }

        public Builder fraudFlags(List<String> fraudFlags) {
            this.fraudFlags = fraudFlags;
            return this;
        }

        public Builder atpMax(Integer atpMax) {
            this.atpMax = atpMax;
            return this;
        }

        public Builder linkedSeq(Long linkedSeq) {
            this.linkedSeq = linkedSeq;
            return this;
        }

        public Builder overrideDetail(Map<String, Object> overrideDetail) {
            this.overrideDetail = overrideDetail;
            return this;
        }

        public Builder governanceDetail(Map<String, Object> governanceDetail) {
            this.governanceDetail = governanceDetail;
            return this;
        }

        public LedgerEntry build() {
            return new LedgerEntry(kind, source, applicationDbId, applicationPublicId, ruleVersion,
                    scorecardVersion, engineVersion, bureauPullId, bureauReused, engineInput, outcome,
                    score, creditLimit, reasonCodes, fraudFlags, atpMax, linkedSeq, overrideDetail,
                    governanceDetail);
        }
    }
}
