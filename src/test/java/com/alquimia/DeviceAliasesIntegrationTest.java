package com.alquimia;

import com.alquimia.application.port.in.IntelligenceUseCases;
import com.alquimia.domain.Device;
import com.alquimia.domain.IntelligenceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceAliasesIntegrationTest {
    @LocalServerPort int port;
    @Autowired IntelligenceUseCases useCases;
    @Autowired JsonMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    record ReadingPayload(String device, double distance, List<String> message) { }
    record ProblemPayload(String type, String title, int status, String detail, String instance, String code) { }

    @BeforeEach
    void clear() { useCases.findAll().forEach(reading -> useCases.delete(reading.device())); }

    @ParameterizedTest
    @CsvSource({"STAR Labs,STAR_LABS", "C.C.P.D,CCPD", "S.T.A.R. Satellite,STAR_SATELLITE"})
    void aliasesWorkAcrossCrudAndResponsesUseCanonicalIds(String alias, Device device) throws Exception {
        var registered = request("POST", readingPath(alias), "{\"distance\":1,\"message\":[\"original\"]}");
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(registered.headers().firstValue("Location")).contains("/api/readings/" + device.id());
        assertReading(registered, device, 1, "original");

        var queried = request("GET", readingPath(device.id().toUpperCase(Locale.ROOT).replace('-', '_')), null);
        assertThat(queried.statusCode()).isEqualTo(200);
        assertReading(queried, device, 1, "original");

        var duplicate = request("POST", readingPath(device.id()), "{\"distance\":9,\"message\":[\"duplicado\"]}");
        assertProblem(duplicate, 409, "ALREADY_REGISTERED");

        var replaced = request("PUT", readingPath(alias.toLowerCase(Locale.ROOT)), "{\"distance\":2,\"message\":[\"nuevo\"]}");
        assertThat(replaced.statusCode()).isEqualTo(200);
        assertReading(replaced, device, 2, "nuevo");

        var updated = request("PATCH", readingPath(device.id().replace("-", "")), "{\"distance\":3}");
        assertThat(updated.statusCode()).isEqualTo(200);
        assertReading(updated, device, 3, "nuevo");

        var deleted = request("DELETE", readingPath(device.id().replace('-', '.').toUpperCase(Locale.ROOT)), null);
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(deleted.body()).isEmpty();
        assertProblem(request("GET", readingPath(alias), null), 404, "NOT_FOUND");
        assertThat(request("GET", "/readings", null).body()).isEqualTo("[]");
    }

    @Test
    void completeProcessingAcceptsDisplayNamesInTheRequestBody() throws Exception {
        var response = request("POST", "/intelligence", """
            {"devices":[
              {"device":"STAR Labs","distance":538.5164807134504,"message":["el"]},
              {"device":"C.C.P.D","distance":141.4213562373095,"message":["","laboratorio"]},
              {"device":"S.T.A.R. Satellite","distance":509.9019513592785,"message":["","","oculto"]}
            ]}
            """);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var result = json.readValue(response.body(), IntelligenceResult.class);
        assertThat(result.message()).isEqualTo("el laboratorio oculto");
        assertThat(result.position().x()).isCloseTo(0, within(1e-8));
        assertThat(result.position().y()).isCloseTo(0, within(1e-8));
        assertThat(request("GET", "/readings", null).body()).isEqualTo("[]");
    }

    @Test
    void duplicateAliasesInTheSameBatchAreRejectedAsTheSameDevice() throws Exception {
        var response = request("POST", "/intelligence", """
            {"devices":[
              {"device":"STAR Labs","distance":1,"message":["x"]},
              {"device":"STAR_LABS","distance":1,"message":["x"]},
              {"device":"ccpd","distance":1,"message":["x"]}
            ]}
            """);
        assertProblem(response, 400, "INVALID_DATA");
        assertThat(request("GET", "/readings", null).body()).isEqualTo("[]");
    }

    @Test
    void unknownPunctuationInAPathRemainsAnUnknownDevice() throws Exception {
        assertProblem(request("POST", readingPath("STAR:LABS"), "{\"distance\":1,\"message\":[\"x\"]}"),
            400, "UNKNOWN_DEVICE");
        assertThat(request("GET", "/readings", null).body()).isEqualTo("[]");
    }

    private String readingPath(String alias) {
        return "/readings/" + URLEncoder.encode(alias, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
            .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertReading(HttpResponse<String> response, Device device, double distance, String word) {
        assertThat(json.readValue(response.body(), ReadingPayload.class))
            .isEqualTo(new ReadingPayload(device.id(), distance, List.of(word)));
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        var problem = json.readValue(response.body(), ProblemPayload.class);
        assertThat(problem.status()).isEqualTo(status);
        assertThat(problem.code()).isEqualTo(code);
    }
}
