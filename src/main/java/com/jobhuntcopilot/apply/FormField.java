package com.jobhuntcopilot.apply;

import java.util.List;

/**
 * A single field found on a scanned application form. {@code locatorKey} is an opaque string only
 * the scanner/filler interpret (an element id or a stable CSS selector) — {@link FieldMatcher} and
 * {@link ClaudeFieldInterpreter} never touch Selenium types, which is what keeps them testable
 * without a browser. {@code options} holds the literal option text for SELECT/RADIO_GROUP fields
 * (empty otherwise) — matching happens against this form's actual wording, not a canonical list.
 */
public record FormField(String locatorKey, String labelText, FieldType fieldType, boolean required, List<String> options) {
}
