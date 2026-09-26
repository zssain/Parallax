package com.parallax.assistant.tools;

import java.util.List;

/**
 * The shape every Parallax tool returns to the model (SPEC §12). {@code type} is always
 * {@code "tool_result"}; {@code untrustedTextFields} names any {@code data} fields written by
 * applicants (e.g. {@code address}) so the model treats them as data, never instructions.
 */
public record ToolResult(String type, List<String> untrustedTextFields, Object data) {

    public static ToolResult of(Object data) {
        return new ToolResult("tool_result", List.of(), data);
    }

    public static ToolResult of(List<String> untrustedTextFields, Object data) {
        return new ToolResult("tool_result", untrustedTextFields, data);
    }
}
