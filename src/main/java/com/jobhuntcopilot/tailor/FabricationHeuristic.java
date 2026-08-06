package com.jobhuntcopilot.tailor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extra safety net beyond prompting: flags numeric tokens (percentages, counts, dollar amounts)
 * that appear in a reworded bullet but not in the original. Reordering/rewording is allowed, but a
 * brand-new number in a rewording is a strong signal of a fabricated metric — this doesn't block
 * anything, it just surfaces the flag in the diff view so it gets a second look before use.
 */
public final class FabricationHeuristic {

    private static final Pattern NUMERIC_TOKEN = Pattern.compile("\\$?\\d[\\d,.]*[%+]?");

    private FabricationHeuristic() {
    }

    public static List<String> newNumbersIn(String originalText, String rewordedText) {
        Set<String> originalNumbers = extractNumbers(originalText);
        Set<String> newNumbers = new LinkedHashSet<>();
        for (String token : extractNumbers(rewordedText)) {
            if (!originalNumbers.contains(token)) {
                newNumbers.add(token);
            }
        }
        return List.copyOf(newNumbers);
    }

    private static Set<String> extractNumbers(String text) {
        Set<String> numbers = new LinkedHashSet<>();
        Matcher matcher = NUMERIC_TOKEN.matcher(text);
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        return numbers;
    }
}
