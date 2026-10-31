package ru.hotelbooking.booking.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки саги бронирования.
 *
 * @param maxCandidates сколько рекомендованных номеров перебирает автоподбор, если предыдущие уже заняты. Параллельные
 *     запросы на одни даты получают одинаковый порядок рекомендаций, поэтому k-й из них добивается успеха на k-м
 *     кандидате: значение ограничивает и стоимость одного запроса, и число таких запросов, которые будут обслужены
 * @param pendingTimeout бронь, которая остаётся в PENDING дольше этого времени, считается брошенной и компенсируется
 * @param recoveryBatchSize сколько броней обрабатывает один запуск фоновой задачи восстановления
 */
@ConfigurationProperties("app.saga")
public record SagaProperties(
        @DefaultValue("20") int maxCandidates,
        @DefaultValue("1m") Duration pendingTimeout,
        @DefaultValue("50") int recoveryBatchSize) {}
