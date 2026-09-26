package com.parallax.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Synthetic history CLI (SPEC §12 data, Prompt 12). Writes one JSON line per generated applicant to
 * {@code --out} and prints a summary. Example:
 *
 * <pre>
 * java -jar data-generator.jar --count 100000 --seed 20260925 --out history.jsonl \
 *      --days 365 --drift 0.35 --drift-days 60 --as-of 2026-09-25
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> opts = parse(args);
        int count = intOpt(opts, "count", 100000);
        long seed = longOpt(opts, "seed", 20260925L);
        String out = opts.getOrDefault("out", "history.jsonl");
        int days = intOpt(opts, "days", 365);
        double drift = doubleOpt(opts, "drift", 0.35);
        int driftDays = intOpt(opts, "drift-days", 60);
        LocalDate asOf = LocalDate.parse(opts.getOrDefault("as-of", "2026-09-25"));

        HistoryGenerator generator = new HistoryGenerator();
        HistoryGenerator.HistoryParams params =
                new HistoryGenerator.HistoryParams(count, seed, days, drift, driftDays, asOf);

        long startNanos = System.nanoTime();
        long defaults = 0;
        long written = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(out), StandardCharsets.UTF_8)) {
            Iterator<HistoryGenerator.HistoryRecord> it = generator.generate(params).iterator();
            while (it.hasNext()) {
                HistoryGenerator.HistoryRecord record = it.next();
                writer.write(HistoryJsonWriter.line(record));
                writer.write('\n');
                if (record.applicant().defaulted()) {
                    defaults++;
                }
                written++;
            }
        }
        long tookMs = (System.nanoTime() - startNanos) / 1_000_000;
        double defaultRate = written == 0 ? 0.0 : (double) defaults / written;
        System.out.printf("Wrote %d records to %s — default rate %.4f, took %d ms%n",
                written, out, defaultRate, tookMs);
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (args[i].startsWith("--")) {
                opts.put(args[i].substring(2), args[i + 1]);
            }
        }
        return opts;
    }

    private static int intOpt(Map<String, String> opts, String key, int def) {
        return opts.containsKey(key) ? Integer.parseInt(opts.get(key)) : def;
    }

    private static long longOpt(Map<String, String> opts, String key, long def) {
        return opts.containsKey(key) ? Long.parseLong(opts.get(key)) : def;
    }

    private static double doubleOpt(Map<String, String> opts, String key, double def) {
        return opts.containsKey(key) ? Double.parseDouble(opts.get(key)) : def;
    }
}
