package com.parallax.assistant.evals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The 15-question assistant eval set (SPEC §12). Tagged {@code evals} and excluded from the default
 * build; run it with {@code ./mvnw -pl assistant-service verify -Dgroups=evals} against a seeded, running
 * stack, with {@code OPENAI_API_KEY} (so the assistant is configured) and {@code PARALLAX_BASE_URL}
 * (application-service) set. It is gated on those env vars, so it is skipped — never failed — otherwise.
 *
 * <p>For each item it resolves every fact reference in {@code must_include} to the live value by calling
 * application-service (never inventing a number), asks the question over {@code /api/v1/assistant/chat},
 * and checks: the required tools appear in the answer's tool calls; each resolved fact is present (numbers
 * matched flexibly — 445, 83.2%, $12,000); and every forbidden literal is absent. A reference (or a
 * {@code {placeholder}}) that cannot be resolved marks the item SKIPPED with a reason. Results are written
 * to {@code evals/results/<yyyy-MM-dd>.md} and a summary is printed.
 */
@Tag("evals")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PARALLAX_BASE_URL", matches = ".+")
class EvalRunner {

    private final ObjectMapper mapper = new ObjectMapper();

    private final String appBase = env("PARALLAX_BASE_URL", "http://localhost:8080");
    private final String assistantBase = env("PARALLAX_ASSISTANT_URL", "http://localhost:8083");
    private final RestClient app = client(appBase,
            "assistant@parallax.dev", env("ASSISTANT_PASSWORD", "assistant-dev"));
    private final RestClient appInternal = client(appBase,
            "priya.menon@parallax.dev", env("DEMO_PASSWORD", "demo-password"));
    private final RestClient assistant = client(assistantBase,
            "priya.menon@parallax.dev", env("DEMO_PASSWORD", "demo-password"));

    @Test
    void runEvalSet() throws IOException {
        List<Map<String, Object>> items = loadItems();
        List<Result> results = new ArrayList<>();
        for (Map<String, Object> item : items) {
            results.add(runOne(item));
        }
        writeReport(results);
        printSummary(results);

        // The build must not fail merely because parts of the dataset were unavailable (e.g. no replay
        // has been run); it fails only on a genuine FAIL.
        List<String> failed = results.stream().filter(r -> r.status == Status.FAIL).map(r -> r.id).toList();
        if (!failed.isEmpty()) {
            throw new AssertionError("Eval failures: " + failed);
        }
    }

    private Result runOne(Map<String, Object> item) {
        String id = str(item.get("id"));
        String question = str(item.get("question"));
        List<String> requiredTools = strings(item.get("required_tools"));
        List<String> mustInclude = strings(item.get("must_include"));
        List<String> mustNotInclude = strings(item.get("must_not_include"));
        try {
            question = substitutePlaceholders(question);
            List<String> resolvedIncludes = new ArrayList<>();
            for (String ref : mustInclude) {
                resolvedIncludes.add(substitutePlaceholders(ref));
            }
            List<Fact> facts = new ArrayList<>();
            for (String ref : resolvedIncludes) {
                facts.add(resolveFact(ref));
            }

            JsonNode chat = ask(question);
            String answer = chat.path("answer").asText("");
            List<String> toolsUsed = new ArrayList<>();
            for (JsonNode call : chat.path("toolCalls")) {
                toolsUsed.add(call.path("name").asText());
            }

            List<String> failures = new ArrayList<>();
            for (String tool : requiredTools) {
                if (!toolsUsed.contains(tool)) {
                    failures.add("missing tool " + tool);
                }
            }
            for (Fact fact : facts) {
                if (!fact.present(answer)) {
                    failures.add("missing fact " + fact.reference);
                }
            }
            for (String forbidden : mustNotInclude) {
                if (answer.toLowerCase(Locale.US).contains(forbidden.toLowerCase(Locale.US))) {
                    failures.add("forbidden string present: " + forbidden);
                }
            }
            if (failures.isEmpty()) {
                return new Result(id, Status.PASS, "tools=" + toolsUsed);
            }
            return new Result(id, Status.FAIL, String.join("; ", failures));
        } catch (Skip skip) {
            return new Result(id, Status.SKIP, skip.getMessage());
        } catch (Exception e) {
            return new Result(id, Status.FAIL, "error: " + e.getMessage());
        }
    }

    // --- fact references -----------------------------------------------------------------------------

    private Fact resolveFact(String ref) {
        int colon = ref.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("not a fact reference: " + ref);
        }
        String source = ref.substring(0, colon);
        String selector = ref.substring(colon + 1);
        return switch (source) {
            case "decision" -> decisionFact(ref, selector);
            case "reasons" -> codeList(ref, selector, "reasonCodes");
            case "fraud" -> codeList(ref, selector, "fraudFlags");
            case "version" -> versionFact(ref, selector);
            case "replay" -> replayFact(ref, selector);
            case "drift" -> driftFact(ref, selector);
            case "override" -> overrideFact(ref, selector);
            default -> throw new IllegalArgumentException("unknown fact source: " + source);
        };
    }

    private Fact decisionFact(String ref, String selector) {
        int dot = selector.lastIndexOf('.');
        String id = selector.substring(0, dot);
        String field = selector.substring(dot + 1);
        JsonNode a = getApplication(id);
        JsonNode base = a.path("base");
        JsonNode current = a.path("current");
        return switch (field) {
            case "score" -> Fact.number(ref, base.path("score").asDouble());
            case "creditLimit" -> Fact.number(ref, current.path("creditLimit").asDouble());
            case "atpMax" -> Fact.number(ref, base.path("atpMax").asDouble());
            case "outcome" -> Fact.string(ref, current.path("outcome").asText());
            case "ruleVersion" -> Fact.string(ref, base.path("ruleVersion").asText());
            default -> throw new IllegalArgumentException("unknown decision field: " + field);
        };
    }

    private Fact codeList(String ref, String id, String node) {
        JsonNode base = getApplication(id).path("base");
        List<String> codes = new ArrayList<>();
        for (JsonNode c : base.path(node)) {
            codes.add(c.path("code").asText());
        }
        if (codes.isEmpty()) {
            throw new Skip("no " + node + " on " + id);
        }
        return Fact.list(ref, codes);
    }

    private Fact versionFact(String ref, String selector) {
        int dot = selector.lastIndexOf('.');
        String version = selector.substring(0, dot);
        String field = selector.substring(dot + 1);
        JsonNode versions = app.get().uri("/api/v1/lab/versions").retrieve().body(JsonNode.class);
        for (JsonNode item : Objects.requireNonNull(versions).path("items")) {
            if (item.path("version").asText().equals(version)) {
                JsonNode value = item.path("config").path(field);
                if (value.isMissingNode()) {
                    throw new IllegalArgumentException("no config field " + field + " on " + version);
                }
                return Fact.number(ref, value.asDouble());
            }
        }
        throw new Skip("version " + version + " not found");
    }

    private Fact replayFact(String ref, String selector) {
        // selector is "latest.<dot.path>"
        String path = selector.substring("latest.".length());
        JsonNode report = latestReplayReport();
        JsonNode value = navigate(report, path);
        if (value == null || value.isMissingNode() || value.isNull()) {
            throw new Skip("replay path " + path + " unavailable");
        }
        return value.isNumber() ? Fact.number(ref, value.asDouble()) : Fact.string(ref, value.asText());
    }

    private Fact driftFact(String ref, String selector) {
        JsonNode drift;
        try {
            drift = app.get().uri("/api/v1/drift/latest").retrieve().body(JsonNode.class);
        } catch (Exception e) {
            throw new Skip("no drift report");
        }
        String field = selector.substring("latest.".length());
        JsonNode value = Objects.requireNonNull(drift).path(field);
        if (value.isMissingNode()) {
            throw new Skip("drift field " + field + " unavailable");
        }
        return value.isNumber() ? Fact.number(ref, value.asDouble()) : Fact.string(ref, value.asText());
    }

    private Fact overrideFact(String ref, String selector) {
        JsonNode stats = app.get().uri("/api/v1/reviews/override-stats").retrieve().body(JsonNode.class);
        long refers = 0;
        long overridden = 0;
        for (JsonNode band : Objects.requireNonNull(stats).path("bands")) {
            refers += band.path("refers").asLong();
            overridden += band.path("overriddenToApprove").asLong();
        }
        return switch (selector) {
            case "refers" -> Fact.number(ref, refers);
            case "overridden" -> Fact.number(ref, overridden);
            default -> throw new IllegalArgumentException("unknown override selector: " + selector);
        };
    }

    // --- placeholders --------------------------------------------------------------------------------

    private String substitutePlaceholders(String text) {
        String result = text;
        if (result.contains("{b01}")) {
            result = result.replace("{b01}", findBureauUnavailableApp());
        }
        if (result.contains("{candidate}")) {
            result = result.replace("{candidate}", latestReplayCandidateVersion());
        }
        return result;
    }

    private String findBureauUnavailableApp() {
        // The list endpoint is INTERNAL-only, so use the internal client for the scan.
        JsonNode page = appInternal.get()
                .uri("/api/v1/applications?outcome=REFER&source=ALL&size=100")
                .retrieve().body(JsonNode.class);
        for (JsonNode row : Objects.requireNonNull(page).path("items")) {
            String id = row.path("applicationId").asText();
            for (JsonNode c : getApplication(id).path("base").path("reasonCodes")) {
                if ("B01".equals(c.path("code").asText())) {
                    return id;
                }
            }
        }
        throw new Skip("no bureau-unavailable (B01) application in the dataset");
    }

    private String latestReplayCandidateVersion() {
        JsonNode versions = app.get().uri("/api/v1/lab/versions").retrieve().body(JsonNode.class);
        for (JsonNode item : Objects.requireNonNull(versions).path("items")) {
            if (item.hasNonNull("latestReplayJobId")) {
                return item.path("version").asText();
            }
        }
        throw new Skip("no completed replay to summarize");
    }

    private JsonNode latestReplayReport() {
        JsonNode versions = app.get().uri("/api/v1/lab/versions").retrieve().body(JsonNode.class);
        for (JsonNode item : Objects.requireNonNull(versions).path("items")) {
            if (item.hasNonNull("latestReplayJobId")) {
                JsonNode job = app.get().uri("/api/v1/lab/replays/{id}", item.path("latestReplayJobId").asText())
                        .retrieve().body(JsonNode.class);
                JsonNode report = Objects.requireNonNull(job).path("report");
                if (!report.isMissingNode() && !report.isNull()) {
                    return report;
                }
            }
        }
        throw new Skip("no completed replay report");
    }

    // --- HTTP ----------------------------------------------------------------------------------------

    private JsonNode getApplication(String id) {
        return app.get().uri("/api/v1/applications/{id}", id).retrieve().body(JsonNode.class);
    }

    private JsonNode ask(String question) {
        ObjectNode body = mapper.createObjectNode();
        body.put("message", question);
        return assistant.post().uri("/api/v1/assistant/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(JsonNode.class);
    }

    private RestClient client(String baseUrl, String user, String password) {
        String basic = Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        return RestClient.builder().baseUrl(baseUrl).defaultHeader("Authorization", "Basic " + basic).build();
    }

    // --- report --------------------------------------------------------------------------------------

    private void writeReport(List<Result> results) throws IOException {
        Path dir = repoRoot().resolve("evals").resolve("results");
        Files.createDirectories(dir);
        Path out = dir.resolve(LocalDate.now() + ".md");
        StringBuilder md = new StringBuilder();
        md.append("# Assistant eval results — ").append(LocalDate.now()).append("\n\n");
        md.append("Application-service: ").append(appBase).append(" · assistant: ").append(assistantBase)
                .append("\n\n");
        long pass = results.stream().filter(r -> r.status == Status.PASS).count();
        long skip = results.stream().filter(r -> r.status == Status.SKIP).count();
        long fail = results.stream().filter(r -> r.status == Status.FAIL).count();
        md.append("**").append(pass).append(" passed, ").append(fail).append(" failed, ").append(skip)
                .append(" skipped of ").append(results.size()).append("**\n\n");
        md.append("| id | result | detail |\n| --- | --- | --- |\n");
        for (Result r : results) {
            md.append("| ").append(r.id).append(" | ").append(r.status).append(" | ")
                    .append(r.detail.replace("|", "\\|")).append(" |\n");
        }
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        System.out.println("Wrote " + out);
    }

    private void printSummary(List<Result> results) {
        System.out.println("=== Assistant eval summary ===");
        for (Result r : results) {
            System.out.printf(Locale.US, "%-28s %-5s %s%n", r.id, r.status, r.detail);
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadItems() throws IOException {
        Path yaml = repoRoot().resolve("evals").resolve("assistant-evals.yaml");
        try (var in = Files.newInputStream(yaml)) {
            return (List<Map<String, Object>>) new Yaml().load(in);
        }
    }

    private Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("evals").resolve("assistant-evals.yaml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the evals directory from " + Path.of("").toAbsolutePath());
    }

    private static JsonNode navigate(JsonNode node, String dotPath) {
        JsonNode current = node;
        for (String part : dotPath.split("\\.")) {
            current = current.path(part);
        }
        return current;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object o) {
        if (o == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object e : (List<Object>) o) {
            out.add(String.valueOf(e));
        }
        return out;
    }

    private enum Status { PASS, FAIL, SKIP }

    private record Result(String id, Status status, String detail) {
    }

    /** A resolved fact reference and how to test its presence in an answer. */
    private record Fact(String reference, Kind kind, String string, double number, List<String> list) {
        enum Kind { NUMBER, STRING, LIST }

        static Fact number(String ref, double v) {
            return new Fact(ref, Kind.NUMBER, null, v, List.of());
        }

        static Fact string(String ref, String v) {
            return new Fact(ref, Kind.STRING, v, 0, List.of());
        }

        static Fact list(String ref, List<String> v) {
            return new Fact(ref, Kind.LIST, null, 0, v);
        }

        boolean present(String answer) {
            String haystack = answer.toLowerCase(Locale.US);
            return switch (kind) {
                case STRING -> haystack.contains(string.toLowerCase(Locale.US));
                case LIST -> list.stream().allMatch(e -> haystack.contains(e.toLowerCase(Locale.US)));
                case NUMBER -> numberVariants(number).stream().anyMatch(haystack::contains);
            };
        }

        private static List<String> numberVariants(double v) {
            List<String> out = new ArrayList<>();
            long rounded = Math.round(v);
            boolean isInt = Math.abs(v - rounded) < 1e-9;
            if (isInt) {
                out.add(Long.toString(rounded));
                out.add(String.format(Locale.US, "%,d", rounded));
            } else {
                out.add(String.format(Locale.US, "%.2f", v));
                out.add(String.format(Locale.US, "%.4f", v));
            }
            // Rate-like values (0 < v < 1): also match a percentage rendering.
            if (v > 0 && v < 1) {
                out.add(String.format(Locale.US, "%.1f%%", v * 100));
                out.add(String.format(Locale.US, "%.2f%%", v * 100));
                out.add(Math.round(v * 100) + "%");
            }
            return out.stream().map(s -> s.toLowerCase(Locale.US)).toList();
        }
    }

    /** Thrown to mark an item skipped (data not present) rather than failed. */
    private static final class Skip extends RuntimeException {
        Skip(String message) {
            super(message);
        }
    }
}
