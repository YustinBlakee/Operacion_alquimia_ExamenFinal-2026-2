package com.alquimia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.alquimia.application.port.in.IntelligenceUseCases;
import com.alquimia.domain.Device;
import com.alquimia.domain.IntelligenceResult;
import com.alquimia.domain.Position;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {
    @LocalServerPort int port;
    @Autowired IntelligenceUseCases useCases;
    @Autowired JsonMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    record ReadingPayload(String device, double distance, List<String> message) { }
    record DevicePayload(String device, Position position) { }
    record ProcessingPayload(List<ReadingPayload> devices) { }
    record ProblemPayload(String type, String title, int status, String detail, String instance, String code) { }

    @BeforeEach
    void clear() { useCases.findAll().forEach(reading -> useCases.delete(reading.device())); }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void fullLifecycleIncludingAccumulatedProcessing() throws Exception {
        assertProblem(request("GET", "/intelligence", null), 422, "INSUFFICIENT_DATA");
        var created = request("POST", "/readings/star-labs", """
            {"distance":538.5164807134504,"message":["el","",""]}
            """);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location")).contains("/api/readings/star-labs");
        var originalLabs = json.readValue(created.body(), ReadingPayload.class);
        assertThat(originalLabs).isEqualTo(new ReadingPayload("star-labs", 538.5164807134504, List.of("el", "", "")));
        assertThat(request("POST", "/readings/ccpd", """
            {"distance":141.4213562373095,"message":["","laboratorio",""]}
            """).statusCode()).isEqualTo(201);
        assertThat(request("POST", "/readings/star-satellite", """
            {"distance":509.9019513592785,"message":["","","oculto"]}
            """).statusCode()).isEqualTo(201);
        var result = request("GET", "/intelligence", null);
        assertResult(result, 0, 0, "el laboratorio oculto", 1e-8);
        assertThat(request("POST", "/readings/ccpd", "{\"distance\":2,\"message\":[\"x\"]}").statusCode()).isEqualTo(409);
        var replaced = request("PUT", "/readings/ccpd", "{\"distance\":5,\"message\":[\"nuevo\"]}");
        assertThat(replaced.statusCode()).isEqualTo(200);
        assertThat(json.readValue(replaced.body(), ReadingPayload.class))
            .isEqualTo(new ReadingPayload("ccpd", 5, List.of("nuevo")));
        var patch = request("PATCH", "/readings/ccpd", "{\"distance\":7}");
        assertThat(patch.statusCode()).isEqualTo(200);
        assertThat(json.readValue(patch.body(), ReadingPayload.class))
            .isEqualTo(new ReadingPayload("ccpd", 7, List.of("nuevo")));
        var messagePatch = request("PATCH", "/readings/ccpd", "{\"message\":[\"parcial\"]}");
        assertThat(messagePatch.statusCode()).isEqualTo(200);
        assertThat(json.readValue(messagePatch.body(), ReadingPayload.class))
            .isEqualTo(new ReadingPayload("ccpd", 7, List.of("parcial")));
        var both = request("PATCH", "/readings/ccpd", "{\"distance\":8,\"message\":[\"ambos\"]}");
        assertThat(both.statusCode()).isEqualTo(200);
        assertThat(json.readValue(both.body(), ReadingPayload.class))
            .isEqualTo(new ReadingPayload("ccpd", 8, List.of("ambos")));
        var deleted = request("DELETE", "/readings/ccpd", null);
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(deleted.body()).isEmpty();
        assertProblem(request("GET", "/readings/ccpd", null), 404, "NOT_FOUND");
        var remaining = request("GET", "/readings", null);
        assertThat(remaining.statusCode()).isEqualTo(200);
        assertThat(json.readValue(remaining.body(), ReadingPayload[].class)).containsExactly(originalLabs,
            new ReadingPayload("star-satellite", 509.9019513592785, List.of("", "", "oculto")));
        assertProblem(request("GET", "/intelligence", null), 422, "INSUFFICIENT_DATA");
        assertThat(request("POST", "/readings/ccpd", """
            {"distance":141.4213562373095,"message":["","laboratorio",""]}
            """).statusCode()).isEqualTo(201);
        assertResult(request("GET", "/intelligence", null), 0, 0, "el laboratorio oculto", 1e-8);
    }

    @Test
    void completeProcessingIsStatelessAndAcceptsDifferentOrder() throws Exception {
        var response = request("POST", "/intelligence", """
            {"devices":[
              {"device":"star-satellite","distance":509.9019513592785,"message":["","oculto"]},
              {"device":"star-labs","distance":538.5164807134504,"message":["laboratorio",""]},
              {"device":"ccpd","distance":141.4213562373095,"message":["",""]}
            ]}
            """);
        assertResult(response, 0, 0, "laboratorio oculto", 1e-8);
        assertThat(request("GET", "/readings", null).body()).isEqualTo("[]");
    }

    @Test
    void rejectsMalformedInvalidUnknownAndExplicitNullWithoutChangingState() throws Exception {
        assertThat(request("POST", "/readings/unknown", "{\"distance\":1,\"message\":[\"x\"]}").statusCode()).isEqualTo(400);
        assertThat(request("PUT", "/readings/ccpd", "{\"distance\":1,\"message\":[\"x\"]}").statusCode()).isEqualTo(404);
        for (String body : new String[] {"{}", "{", "null", "{\"distance\":-1,\"message\":[\"x\"]}",
            "{\"distance\":null,\"message\":[\"x\"]}", "{\"distance\":1,\"message\":[null]}",
            "{\"distance\":1,\"message\":[\"x\"],\"typo\":1}", "{\"distance\":\"abc\",\"message\":[\"x\"]}",
            "{\"distance\":\"1\",\"message\":[\"x\"]}", "{\"distance\":1,\"message\":[123]}",
            "{\"distance\":1,\"message\":[true]}"}) {
            assertThat(request("POST", "/readings/ccpd", body).statusCode()).as(body).isEqualTo(400);
        }
        assertThat(request("POST", "/readings/ccpd", "{\"distance\":1,\"message\":[\"original\"]}").statusCode()).isEqualTo(201);
        for (String body : new String[] {"{}", "{\"distance\":null}", "{\"message\":null}",
            "{\"distance\":-1}", "{\"typo\":1}",
            "{\"distance\":2,\"message\":null}", "{\"distance\":2,\"message\":[null]}"}) {
            assertThat(request("PATCH", "/readings/ccpd", body).statusCode()).as(body).isEqualTo(400);
        }
        assertThat(request("PUT", "/readings/ccpd", "{\"distance\":2}").statusCode()).isEqualTo(400);
        assertThat(request("PUT", "/readings/ccpd", "{\"message\":[\"x\"]}").statusCode()).isEqualTo(400);
        assertThat(json.readValue(request("GET", "/readings/ccpd", null).body(), ReadingPayload.class))
            .isEqualTo(new ReadingPayload("ccpd", 1, List.of("original")));
        var problem = request("GET", "/readings/star-labs", null);
        assertProblem(problem, 404, "NOT_FOUND");
    }

    @Test
    void catalogContainsTheThreeFixedPositions() throws Exception {
        var response = request("GET", "/devices", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readValue(response.body(), DevicePayload[].class)).containsExactly(
            new DevicePayload("star-labs", new Position(-500, -200)),
            new DevicePayload("ccpd", new Position(100, -100)),
            new DevicePayload("star-satellite", new Position(500, 100)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
    void mutationsRequireAnExistingReading(String method) throws Exception {
        String body = method.equals("DELETE") ? null : "{\"distance\":1,\"message\":[\"x\"]}";
        assertProblem(request(method, "/readings/ccpd", body), 404, "NOT_FOUND");
        assertThat(request("GET", "/readings", null).body()).isEqualTo("[]");
    }

    @ParameterizedTest
    @CsvSource({"100000000,100000000", "500000000,300000000", "999999000,0", "1100000000,0"})
    void largeValidDistancesWorkOverHttp(double x, double y) throws Exception {
        ProcessingPayload input = complete(x, y);
        var response = request("POST", "/intelligence", json.writeValueAsString(input));
        IntelligenceResult result = assertResult(response, x, y, "el laboratorio oculto", 4);
        for (ReadingPayload reading : input.devices()) {
            Position sensor = Device.fromId(reading.device()).position();
            assertThat(Math.hypot(result.position().x() - sensor.x(), result.position().y() - sensor.y()))
                .isCloseTo(reading.distance(), within(0.01));
        }
    }

    @ParameterizedTest
    @CsvSource({"missing,422,INSUFFICIENT_DATA", "duplicate,400,INVALID_DATA",
        "distances,422,INCONSISTENT_DISTANCES", "empty,422,INCOMPLETE_MESSAGE",
        "conflict,422,CONFLICTING_MESSAGE", "alignment,422,CONFLICTING_MESSAGE"})
    void processingErrorsDoNotMutateStoredData(String scenario, int status, String code) throws Exception {
        assertThat(request("POST", "/readings/ccpd", "{\"distance\":1,\"message\":[\"original\"]}").statusCode()).isEqualTo(201);
        var devices = new java.util.ArrayList<>(complete(0, 0).devices());
        ReadingPayload first = devices.get(0);
        switch (scenario) {
            case "missing" -> devices.remove(0);
            case "duplicate" -> devices.set(1, first);
            case "distances" -> devices.replaceAll(reading -> new ReadingPayload(reading.device(), 1, reading.message()));
            case "empty" -> devices.replaceAll(reading -> new ReadingPayload(reading.device(), reading.distance(), List.of("", "", "")));
            case "conflict" -> devices.set(0, new ReadingPayload(first.device(), first.distance(), List.of("otro", "", "oculto")));
            case "alignment" -> devices.set(0, new ReadingPayload(first.device(), first.distance(), List.of("corto")));
            default -> throw new IllegalArgumentException(scenario);
        }
        assertProblem(request("POST", "/intelligence", json.writeValueAsString(new ProcessingPayload(devices))), status, code);
        assertThat(json.readValue(request("GET", "/readings", null).body(), ReadingPayload[].class))
            .containsExactly(new ReadingPayload("ccpd", 1, List.of("original")));
    }

    private ProcessingPayload complete(double x, double y) {
        List<Device> order = List.of(Device.STAR_SATELLITE, Device.CCPD, Device.STAR_LABS);
        return new ProcessingPayload(order.stream().map(device -> {
            var words = switch (device) {
                case STAR_LABS -> List.of("el", "", "");
                case CCPD -> List.of("", "laboratorio", "");
                case STAR_SATELLITE -> List.of("", "", "oculto");
            };
            return new ReadingPayload(device.id(), Math.hypot(x - device.position().x(), y - device.position().y()), words);
        }).toList());
    }

    @Test
    void unequalAndEmptyFragmentsWorkInBothProcessingPaths() throws Exception {
        var original = complete(0, 0);
        var devices = original.devices().stream().map(reading -> new ReadingPayload(reading.device(), reading.distance(),
            switch (reading.device()) {
                case "star-labs" -> List.of("el");
                case "ccpd" -> List.of("", "laboratorio");
                default -> List.of("", "", "oculto");
            })).toList();
        assertResult(request("POST", "/intelligence", json.writeValueAsString(new ProcessingPayload(devices))),
            0, 0, "el laboratorio oculto", 1e-8);
        for (var reading : devices) {
            assertThat(request("POST", "/readings/" + reading.device(), json.writeValueAsString(
                java.util.Map.of("distance", reading.distance(), "message", reading.message()))).statusCode()).isEqualTo(201);
        }
        assertResult(request("GET", "/intelligence", null), 0, 0, "el laboratorio oculto", 1e-8);
        var cleared = request("PATCH", "/readings/star-labs", "{\"message\":[]}");
        assertThat(cleared.statusCode()).isEqualTo(200);
        assertThat(json.readValue(cleared.body(), ReadingPayload.class).message()).isEmpty();
        assertProblem(request("GET", "/intelligence", null), 422, "INCOMPLETE_MESSAGE");
        assertThat(request("PUT", "/readings/ccpd", """
            {"distance":141.4213562373095,"message":["el","laboratorio"]}
            """).statusCode()).isEqualTo(200);
        assertResult(request("GET", "/intelligence", null), 0, 0, "el laboratorio oculto", 1e-8);
    }

    @Test
    void acceptsInputsBeyondFormerArbitrarySizeLimits() throws Exception {
        var words = new java.util.ArrayList<>(java.util.Collections.nCopies(1001, "palabra"));
        words.set(0, "z".repeat(101));
        words.set(1, "dos palabras");
        var registered = request("POST", "/readings/ccpd", json.writeValueAsString(
            java.util.Map.of("distance", 1e10, "message", words)));
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(json.readValue(registered.body(), ReadingPayload.class))
            .isEqualTo(new ReadingPayload("ccpd", 1e10, words));
        var devices = complete(0, 0).devices().stream().map(reading -> new ReadingPayload(reading.device(),
            reading.distance(), reading.device().equals("star-labs") ? words : List.<String>of())).toList();
        assertResult(request("POST", "/intelligence", json.writeValueAsString(new ProcessingPayload(devices))),
            0, 0, String.join(" ", words), 1e-8);
    }

    private IntelligenceResult assertResult(HttpResponse<String> response, double x, double y, String message, double tolerance) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        IntelligenceResult result = json.readValue(response.body(), IntelligenceResult.class);
        assertThat(result.position().x()).isFinite().isCloseTo(x, within(tolerance));
        assertThat(result.position().y()).isFinite().isCloseTo(y, within(tolerance));
        assertThat(result.message()).isEqualTo(message);
        return result;
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        var problem = json.readValue(response.body(), ProblemPayload.class);
        assertThat(problem.status()).isEqualTo(status);
        assertThat(problem.code()).isEqualTo(code);
        assertThat(problem.detail()).isNotBlank();
        assertThat(problem.instance()).startsWith("/api/");
    }
}
