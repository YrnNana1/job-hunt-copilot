package com.jobhuntcopilot.apply;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scans a Greenhouse-hosted application form (boards.greenhouse.io, or an embedded
 * #application_form widget on a company's own domain). Greenhouse associates each field with a
 * proper {@code <label for="id">}, so this walks every label in the form container and resolves its
 * target element by id — the most reliable association a form can offer.
 *
 * Built from documented Greenhouse job-board form structure; if the real DOM has drifted, this is
 * exactly what the live walkthrough test (see README) is for.
 */
public class GreenhouseFormScanner implements AtsFormScanner {

    private static final List<String> CONTAINER_SELECTORS = List.of("#application_form", "form");

    @Override
    public List<FormField> scan(WebDriver driver) {
        WebElement container = findContainer(driver);
        List<FormField> fields = new ArrayList<>();
        Set<String> handledRadioGroups = new LinkedHashSet<>();

        for (WebElement label : container.findElements(By.cssSelector("label[for]"))) {
            String forId = label.getAttribute("for");
            if (forId == null || forId.isBlank()) {
                continue;
            }
            List<WebElement> targets = container.findElements(By.id(forId));
            if (targets.isEmpty()) {
                continue;
            }
            WebElement target = targets.get(0);
            String labelText = label.getText().trim();
            boolean required = labelText.contains("*") || "true".equals(target.getAttribute("aria-required"));

            FormField field = toFormField(container, target, forId, labelText, required, handledRadioGroups);
            if (field != null) {
                fields.add(field);
            }
        }
        return fields;
    }

    private FormField toFormField(
            WebElement container, WebElement target, String id, String labelText, boolean required,
            Set<String> handledRadioGroups) {
        String tag = target.getTagName().toLowerCase();
        String type = target.getAttribute("type");

        if ("select".equals(tag)) {
            return new FormField("id:" + id, labelText, FieldType.SELECT, required, optionTexts(target));
        }
        if ("textarea".equals(tag)) {
            return new FormField("id:" + id, labelText, FieldType.TEXTAREA, required, List.of());
        }
        if ("input".equals(tag) && "radio".equalsIgnoreCase(type)) {
            String name = target.getAttribute("name");
            if (name == null || !handledRadioGroups.add(name)) {
                return null;
            }
            return radioGroupField(container, name, labelText, required);
        }
        if ("input".equals(tag) && "checkbox".equalsIgnoreCase(type)) {
            return new FormField("id:" + id, labelText, FieldType.CHECKBOX, required, List.of());
        }
        if ("input".equals(tag) && "file".equalsIgnoreCase(type)) {
            return new FormField("id:" + id, labelText, FieldType.FILE, required, List.of());
        }
        if ("input".equals(tag) && "email".equalsIgnoreCase(type)) {
            return new FormField("id:" + id, labelText, FieldType.EMAIL, required, List.of());
        }
        if ("input".equals(tag) && "tel".equalsIgnoreCase(type)) {
            return new FormField("id:" + id, labelText, FieldType.TEL, required, List.of());
        }
        if ("input".equals(tag)) {
            return new FormField("id:" + id, labelText, FieldType.TEXT, required, List.of());
        }
        return null;
    }

    private FormField radioGroupField(WebElement container, String name, String labelText, boolean required) {
        List<WebElement> radios = container.findElements(By.cssSelector("input[type='radio'][name='" + name + "']"));
        List<String> options = new ArrayList<>();
        for (WebElement radio : radios) {
            String optionText = radioOptionLabel(container, radio);
            if (optionText != null && !optionText.isBlank()) {
                options.add(optionText);
            }
        }
        return new FormField("name:" + name, labelText, FieldType.RADIO_GROUP, required, options);
    }

    private String radioOptionLabel(WebElement container, WebElement radio) {
        String id = radio.getAttribute("id");
        if (id != null) {
            List<WebElement> matchingLabels = container.findElements(By.cssSelector("label[for='" + id + "']"));
            if (!matchingLabels.isEmpty()) {
                return matchingLabels.get(0).getText().trim();
            }
        }
        return radio.getAttribute("value");
    }

    private List<String> optionTexts(WebElement select) {
        List<String> texts = new ArrayList<>();
        for (WebElement option : select.findElements(By.tagName("option"))) {
            texts.add(option.getText().trim());
        }
        return texts;
    }

    private WebElement findContainer(WebDriver driver) {
        for (String selector : CONTAINER_SELECTORS) {
            List<WebElement> matches = driver.findElements(By.cssSelector(selector));
            if (!matches.isEmpty()) {
                return matches.get(0);
            }
        }
        throw new ApplyException("Could not find a Greenhouse application form on this page");
    }
}
