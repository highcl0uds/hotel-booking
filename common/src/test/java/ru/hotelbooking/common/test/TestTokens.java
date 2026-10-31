package ru.hotelbooking.common.test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import ru.hotelbooking.common.security.JwtProperties;
import ru.hotelbooking.common.security.JwtTokenFactory;
import ru.hotelbooking.common.security.Roles;

/**
 * Строит настоящие подписанные значения заголовка {@code Authorization} для тестов с секретом, настроенным в
 * тестируемом приложении. Сервисы получают класс через test-jar модуля {@code common}.
 */
public final class TestTokens {

    private static final String FOREIGN_SECRET = "this-secret-is-not-known-to-the-services-0123456789";

    private final JwtProperties properties;
    private final JwtTokenFactory factory;

    public TestTokens(JwtProperties properties) {
        this.properties = properties;
        this.factory = new JwtTokenFactory(properties, Clock.systemUTC());
    }

    public String user(long userId, String username) {
        return bearer(factory.issueUserToken(userId, username, List.of(Roles.USER)));
    }

    public String admin(long userId, String username) {
        return bearer(factory.issueUserToken(userId, username, List.of(Roles.ADMIN)));
    }

    public String internal() {
        return bearer(factory.issueServiceToken("booking-service"));
    }

    /** Токен, срок жизни которого истёк час назад. */
    public String expired(long userId, String username) {
        Clock past = Clock.offset(
                Clock.systemUTC(),
                properties.accessTokenTtl().plus(Duration.ofHours(1)).negated());
        return bearer(new JwtTokenFactory(properties, past).issueUserToken(userId, username, List.of(Roles.USER)));
    }

    /** Корректно сформированный токен, подписанный ключом, которому сервисы не доверяют. */
    public String forged(long userId, String username) {
        JwtProperties foreign = new JwtProperties(
                FOREIGN_SECRET, properties.issuer(), properties.accessTokenTtl(), properties.serviceTokenTtl());
        return bearer(
                new JwtTokenFactory(foreign, Clock.systemUTC()).issueUserToken(userId, username, List.of(Roles.ADMIN)));
    }

    private static String bearer(JwtTokenFactory.IssuedToken token) {
        return "Bearer " + token.value();
    }
}
