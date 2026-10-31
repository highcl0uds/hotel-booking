package ru.hotelbooking.booking.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hotel Service, заменённый WireMock. Один сервер на JVM, чтобы Spring-контекст (в нём балансировщик нагрузки указывает
 * на этот сервер) кешировался между тестовыми классами.
 */
public final class HotelServiceStub {

    private static final WireMockServer SERVER = start();

    private HotelServiceStub() {}

    public static String baseUrl() {
        return SERVER.baseUrl();
    }

    public static void reset() {
        SERVER.resetAll();
    }

    public static void recommends(long... roomIds) {
        String rooms = Arrays.stream(roomIds)
                .mapToObj(id -> "{\"id\":%d,\"hotelId\":1,\"number\":\"%d\",\"timesBooked\":0}".formatted(id, id))
                .collect(Collectors.joining(",", "[", "]"));
        SERVER.stubFor(get(urlPathEqualTo("/api/rooms/recommend")).willReturn(json(200, rooms)));
    }

    public static void recommendFails() {
        SERVER.stubFor(get(urlPathEqualTo("/api/rooms/recommend"))
                .willReturn(aResponse().withStatus(503)));
    }

    public static void holdSucceeds(long roomId) {
        SERVER.stubFor(post(holdPath(roomId)).willReturn(reservation(roomId, "HELD")));
    }

    public static void holdRejected(long roomId, String code) {
        SERVER.stubFor(post(holdPath(roomId))
                .willReturn(json(
                        409,
                        "{\"status\":409,\"code\":\"%s\",\"message\":\"Room %d is already booked\"}"
                                .formatted(code, roomId))));
    }

    public static void holdFails(long roomId, int status) {
        SERVER.stubFor(post(holdPath(roomId)).willReturn(aResponse().withStatus(status)));
    }

    /** Успешный ответ, отправленный после задержки (дольше read timeout, то есть вызов завершается по тайм-ауту). */
    public static void holdRespondsAfter(long roomId, int delayMillis) {
        SERVER.stubFor(
                post(holdPath(roomId)).willReturn(reservation(roomId, "HELD").withFixedDelay(delayMillis)));
    }

    /** Первая попытка теряет соединение, повтор проходит успешно. */
    public static void holdFailsOnceThenSucceeds(long roomId) {
        SERVER.stubFor(post(holdPath(roomId))
                .inScenario("flaky-hold")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo("recovered"));
        SERVER.stubFor(post(holdPath(roomId))
                .inScenario("flaky-hold")
                .whenScenarioStateIs("recovered")
                .willReturn(reservation(roomId, "HELD")));
    }

    public static void releaseSucceeds(long roomId) {
        SERVER.stubFor(post(releasePath(roomId)).willReturn(reservation(roomId, "RELEASED")));
    }

    public static void releaseFails(long roomId) {
        SERVER.stubFor(post(releasePath(roomId)).willReturn(aResponse().withStatus(500)));
    }

    /** Hotel Service не знает номера (например, его удалили): освобождать нечего. */
    public static void releaseRoomNotFound(long roomId) {
        SERVER.stubFor(post(releasePath(roomId))
                .willReturn(json(
                        404,
                        "{\"status\":404,\"code\":\"ROOM_NOT_FOUND\",\"message\":\"Room with id %d not found\"}"
                                .formatted(roomId))));
    }

    public static List<LoggedRequest> holdRequests(long roomId) {
        return SERVER.findAll(postRequestedFor(urlPathEqualTo(holdPath(roomId))));
    }

    public static int holdRequests(long roomId, String requestId) {
        return SERVER.findAll(withRequestId(postRequestedFor(urlPathEqualTo(holdPath(roomId))), requestId))
                .size();
    }

    public static int releaseRequests(long roomId, String requestId) {
        return SERVER.findAll(withRequestId(postRequestedFor(urlPathEqualTo(releasePath(roomId))), requestId))
                .size();
    }

    public static int totalRequests() {
        return SERVER.getAllServeEvents().size();
    }

    private static RequestPatternBuilder withRequestId(RequestPatternBuilder pattern, String requestId) {
        return pattern.withRequestBody(matchingJsonPath("$.requestId", equalTo(requestId)));
    }

    private static String holdPath(long roomId) {
        return "/api/rooms/%d/confirm-availability".formatted(roomId);
    }

    private static String releasePath(long roomId) {
        return "/api/rooms/%d/release".formatted(roomId);
    }

    private static ResponseDefinitionBuilder reservation(long roomId, String status) {
        return json(
                200,
                "{\"id\":%d,\"roomId\":%d,\"hotelId\":1,\"status\":\"%s\"}".formatted(500 + roomId, roomId, status));
    }

    private static ResponseDefinitionBuilder json(int status, String body) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    private static WireMockServer start() {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        return server;
    }
}
