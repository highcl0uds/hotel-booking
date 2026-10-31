package ru.hotelbooking.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DateRangeValidatorTest {

    private static final LocalDate START = LocalDate.of(2030, 1, 10);

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ValidDateRange(maxDays = 30)
    record Stay(LocalDate startDate, LocalDate endDate) implements DateRangeAware {}

    @Test
    @DisplayName("Корректный период проходит валидацию")
    void validRangePasses() {
        assertThat(validator.validate(new Stay(START, START.plusDays(3)))).isEmpty();
    }

    @Test
    @DisplayName("Дата выезда раньше или равна дате заезда — ошибка на поле endDate")
    void endNotAfterStartIsRejected() {
        Set<ConstraintViolation<Stay>> sameDay = validator.validate(new Stay(START, START));
        Set<ConstraintViolation<Stay>> reversed = validator.validate(new Stay(START, START.minusDays(1)));

        assertThat(sameDay)
                .singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath()).hasToString("endDate"));
        assertThat(reversed).hasSize(1);
    }

    @Test
    @DisplayName("Слишком длинный период отклоняется")
    void tooLongRangeIsRejected() {
        Set<ConstraintViolation<Stay>> violations = validator.validate(new Stay(START, START.plusDays(31)));

        assertThat(violations)
                .singleElement()
                .satisfies(v -> assertThat(v.getMessage()).contains("30"));
    }

    @Test
    @DisplayName("Пустые даты оставляются на @NotNull")
    void nullDatesAreIgnored() {
        assertThat(validator.validate(new Stay(null, START))).isEmpty();
    }
}
