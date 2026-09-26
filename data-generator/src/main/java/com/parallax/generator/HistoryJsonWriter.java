package com.parallax.generator;

import com.parallax.engine.model.EngineInput;

/**
 * A tiny hand-written JSON line writer for history records (no Jackson in this module, Prompt 12).
 * The {@code input} object uses the exact {@link EngineInput} field names so application-service can
 * parse a history file back into an {@code EngineInput}.
 */
public final class HistoryJsonWriter {

    private HistoryJsonWriter() {
    }

    public static String line(HistoryGenerator.HistoryRecord record) {
        EngineInput in = record.applicant().input();
        StringBuilder sb = new StringBuilder(320);
        sb.append("{\"i\":").append(record.i());
        sb.append(",\"createdAt\":\"").append(record.createdAt()).append('"');
        sb.append(",\"input\":{");
        sb.append("\"age\":").append(in.age());
        sb.append(",\"birthYear\":").append(in.birthYear());
        sb.append(",\"annualIncome\":").append(in.annualIncome());
        sb.append(",\"monthlyHousing\":").append(in.monthlyHousing());
        sb.append(",\"monthlyDebt\":").append(in.monthlyDebt());
        sb.append(",\"revolvingUtilization\":").append(num(in.revolvingUtilization()));
        sb.append(",\"inquiries6m\":").append(in.inquiries6m());
        sb.append(",\"delinquencies24m\":").append(in.delinquencies24m());
        sb.append(",\"openTradelines\":").append(in.openTradelines());
        sb.append(",\"fileAgeMonths\":").append(in.fileAgeMonths());
        sb.append(",\"independentIncome\":").append(in.independentIncome());
        sb.append(",\"bureauConsent\":").append(in.bureauConsent());
        sb.append(",\"addressMismatch\":").append(in.addressMismatch());
        sb.append(",\"ssnIssuanceYear\":").append(in.ssnIssuanceYear());
        sb.append(",\"deceased\":").append(in.deceased());
        sb.append(",\"velocity24h\":").append(in.velocity24h());
        sb.append(",\"pullType\":\"").append(in.pullType()).append('"');
        sb.append('}');
        sb.append(",\"pd\":").append(num(record.applicant().pd()));
        sb.append(",\"defaulted\":").append(record.applicant().defaulted());
        sb.append('}');
        return sb.toString();
    }

    private static String num(double value) {
        return Double.toString(value);
    }
}
