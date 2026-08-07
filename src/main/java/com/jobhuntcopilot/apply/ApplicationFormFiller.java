package com.jobhuntcopilot.apply;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.util.List;

/**
 * Fills a live browser's application form with resolved field matches. Deliberately has no method
 * that locates or clicks anything resembling a submit control — that omission is the actual
 * "human submits" boundary, not just a UI convention (see ApplyFlowService, which never calls
 * driver.quit() either, leaving the browser under the user's control from here on).
 */
public class ApplicationFormFiller {

    public void fill(WebDriver driver, List<FieldMatch> matches) {
        for (FieldMatch match : matches) {
            if (match.source() == MatchSource.UNMATCHED) {
                continue;
            }
            fillOne(driver, match);
        }
    }

    private void fillOne(WebDriver driver, FieldMatch match) {
        FormField field = match.field();
        switch (field.fieldType()) {
            case TEXT, EMAIL, TEL, TEXTAREA -> setText(resolveElement(driver, field.locatorKey()), match.resolvedValue());
            case SELECT -> selectOption(resolveElement(driver, field.locatorKey()), match.resolvedOptionText());
            case RADIO_GROUP -> selectRadio(driver, field.locatorKey(), match.resolvedOptionText());
            case CHECKBOX -> setCheckbox(resolveElement(driver, field.locatorKey()), "true".equalsIgnoreCase(match.resolvedValue()));
            case FILE -> resolveElement(driver, field.locatorKey()).sendKeys(match.resolvedValue());
        }
    }

    private void setText(WebElement element, String value) {
        element.clear();
        element.sendKeys(value);
    }

    private void selectOption(WebElement selectElement, String optionText) {
        new Select(selectElement).selectByVisibleText(optionText);
    }

    private void selectRadio(WebDriver driver, String locatorKey, String optionText) {
        String name = locatorKey.substring("name:".length());
        for (WebElement radio : driver.findElements(By.cssSelector("input[type='radio'][name='" + name + "']"))) {
            if (optionText.equals(radioLabel(driver, radio))) {
                radio.click();
                return;
            }
        }
        throw new ApplyException("Could not re-locate radio option \"" + optionText + "\" for group " + name);
    }

    private String radioLabel(WebDriver driver, WebElement radio) {
        String id = radio.getAttribute("id");
        if (id != null) {
            List<WebElement> labels = driver.findElements(By.cssSelector("label[for='" + id + "']"));
            if (!labels.isEmpty()) {
                return labels.get(0).getText().trim();
            }
        }
        return radio.getAttribute("value");
    }

    private void setCheckbox(WebElement checkbox, boolean checked) {
        if (checkbox.isSelected() != checked) {
            checkbox.click();
        }
    }

    private WebElement resolveElement(WebDriver driver, String locatorKey) {
        if (locatorKey.startsWith("id:")) {
            return driver.findElement(By.id(locatorKey.substring("id:".length())));
        }
        if (locatorKey.startsWith("questionIndex:")) {
            String[] parts = locatorKey.split(":", 3);
            int index = Integer.parseInt(parts[1]);
            String cssSelector = parts[2];
            WebElement question = driver.findElements(By.cssSelector(".application-question")).get(index);
            return question.findElement(By.cssSelector(cssSelector));
        }
        throw new ApplyException("Unrecognized locator key: " + locatorKey);
    }
}
