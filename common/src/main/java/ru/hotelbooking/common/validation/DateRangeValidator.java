package ru.hotelbooking.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.temporal.ChronoUnit;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, DateRangeAware> {

    private int maxDays;
    private String message;
    private String endProperty;

    @Override
    public void initialize(ValidDateRange annotation) {
        this.maxDays = annotation.maxDays();
        this.message = annotation.message();
        this.endProperty = annotation.endProperty();
    }

    @Override
    public boolean isValid(DateRangeAware range, ConstraintValidatorContext context) {
        if (range == null || range.startDate() == null || range.endDate() == null) {
            return true;
        }
        if (!range.endDate().isAfter(range.startDate())) {
            return reject(context, message);
        }
        long days = ChronoUnit.DAYS.between(range.startDate(), range.endDate());
        if (days > maxDays) {
            return reject(context, "date range must not exceed %d days".formatted(maxDays));
        }
        return true;
    }

    private boolean reject(ConstraintValidatorContext context, String violationMessage) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(violationMessage)
                .addPropertyNode(endProperty)
                .addConstraintViolation();
        return false;
    }
}
