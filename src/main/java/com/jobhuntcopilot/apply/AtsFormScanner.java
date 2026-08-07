package com.jobhuntcopilot.apply;

import org.openqa.selenium.WebDriver;

import java.util.List;

/**
 * Enumerates the fields on a loaded application form. Each implementation knows one ATS's DOM
 * conventions — see GreenhouseFormScanner/LeverFormScanner. {@code FormField.locatorKey()} uses a
 * shared 3-prefix scheme both implementations produce and ApplicationFormFiller interprets:
 * {@code "id:<id>"} (element locatable by id), {@code "name:<name>"} (radio group locatable by the
 * shared name attribute of its inputs), or {@code "questionIndex:<n>:<cssSelector>"} (fallback for
 * an element with no id — the n-th question wrapper on the page, then a CSS sub-selector within it).
 */
public interface AtsFormScanner {
    List<FormField> scan(WebDriver driver);
}
