package ru.hotelbooking.common.validation;

import java.time.LocalDate;

public interface DateRangeAware {

    LocalDate startDate();

    LocalDate endDate();
}
