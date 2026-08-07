package com.jobhuntcopilot.apply;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans a Lever-hosted application form (jobs.lever.co/.../apply). Unlike Greenhouse, Lever doesn't
 * associate fields via {@code <label for="id">} — each question is an {@code .application-question}
 * wrapper containing an {@code .application-label} text div and the actual input(s), so association
 * here is structural (same wrapper), not id-based. Falls back to a
 * {@code "questionIndex:n:selector"} locator (see AtsFormScanner) when a field has no id of its own.
 *
 * Built from documented Lever job-board form structure; if the real DOM has drifted, this is exactly
 * what the live walkthrough test (see README) is for.
 */
public class LeverFormScanner implements AtsFormScanner {

    @Override
    public List<FormField> scan(WebDriver driver) {
        List<WebElement> questions = driver.findElements(By.cssSelector(".application-question"));
        if (questions.isEmpty()) {
            throw new ApplyException("Could not find a Lever application form on this page");
        }

        List<FormField> fields = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            WebElement question = questions.get(index);
            String labelText = textOf(question, ".application-label");
            if (labelText.isBlank()) {
                continue;
            }
            boolean required = labelText.contains("*") || !question.findElements(By.cssSelector(".required")).isEmpty();

            FormField field = toFormField(question, index, labelText, required);
            if (field != null) {
                fields.add(field);
            }
        }
        return fields;
    }

    private FormField toFormField(WebElement question, int index, String labelText, boolean required) {
        List<WebElement> files = question.findElements(By.cssSelector("input[type='file']"));
        if (!files.isEmpty()) {
            return new FormField(locatorFor(files.get(0), index, "input[type='file']"), labelText, FieldType.FILE, required, List.of());
        }

        List<WebElement> selects = question.findElements(By.tagName("select"));
        if (!selects.isEmpty()) {
            return new FormField(locatorFor(selects.get(0), index, "select"), labelText, FieldType.SELECT, required,
                    optionTexts(selects.get(0)));
        }

        List<WebElement> radios = question.findElements(By.cssSelector("input[type='radio']"));
        if (!radios.isEmpty()) {
            String name = radios.get(0).getAttribute("name");
            if (name == null || name.isBlank()) {
                return null;
            }
            return new FormField("name:" + name, labelText, FieldType.RADIO_GROUP, required, radioOptionTexts(question, radios));
        }

        List<WebElement> checkboxes = question.findElements(By.cssSelector("input[type='checkbox']"));
        if (!checkboxes.isEmpty()) {
            return new FormField(locatorFor(checkboxes.get(0), index, "input[type='checkbox']"), labelText,
                    FieldType.CHECKBOX, required, List.of());
        }

        List<WebElement> textareas = question.findElements(By.tagName("textarea"));
        if (!textareas.isEmpty()) {
            return new FormField(locatorFor(textareas.get(0), index, "textarea"), labelText, FieldType.TEXTAREA, required, List.of());
        }

        List<WebElement> texts = question.findElements(
                By.cssSelector("input[type='text'], input[type='email'], input[type='tel']"));
        if (!texts.isEmpty()) {
            WebElement text = texts.get(0);
            String type = text.getAttribute("type");
            FieldType fieldType = "email".equalsIgnoreCase(type) ? FieldType.EMAIL
                    : "tel".equalsIgnoreCase(type) ? FieldType.TEL : FieldType.TEXT;
            return new FormField(locatorFor(text, index, "input[type='" + type + "']"), labelText, fieldType, required, List.of());
        }

        return null;
    }

    private String locatorFor(WebElement element, int questionIndex, String cssSelector) {
        String id = element.getAttribute("id");
        return (id != null && !id.isBlank()) ? "id:" + id : "questionIndex:" + questionIndex + ":" + cssSelector;
    }

    private List<String> radioOptionTexts(WebElement question, List<WebElement> radios) {
        List<String> options = new ArrayList<>();
        for (WebElement radio : radios) {
            String id = radio.getAttribute("id");
            String optionText = null;
            if (id != null) {
                List<WebElement> labels = question.findElements(By.cssSelector("label[for='" + id + "']"));
                if (!labels.isEmpty()) {
                    optionText = labels.get(0).getText().trim();
                }
            }
            if (optionText == null || optionText.isBlank()) {
                optionText = radio.getAttribute("value");
            }
            if (optionText != null && !optionText.isBlank()) {
                options.add(optionText);
            }
        }
        return options;
    }

    private List<String> optionTexts(WebElement select) {
        List<String> texts = new ArrayList<>();
        for (WebElement option : select.findElements(By.tagName("option"))) {
            texts.add(option.getText().trim());
        }
        return texts;
    }

    private String textOf(WebElement scope, String cssSelector) {
        List<WebElement> matches = scope.findElements(By.cssSelector(cssSelector));
        return matches.isEmpty() ? "" : matches.get(0).getText().trim();
    }
}
