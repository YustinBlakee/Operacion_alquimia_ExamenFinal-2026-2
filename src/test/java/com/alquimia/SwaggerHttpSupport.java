package com.alquimia;

import com.alquimia.application.port.in.IntelligenceUseCases;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

abstract class SwaggerHttpSupport {
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired IntelligenceUseCases useCases;
    final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(5)).build();

    String contextPath() { return ""; }

    @BeforeEach
    void clearReadings() { useCases.findAll().forEach(reading -> useCases.delete(reading.device())); }

    HttpResponse<String> request(String method, String path, int expected, String body) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + contextPath() + path))
            .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(expected);
        return response;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> map(Object value) {
        return value instanceof String text ? json.readValue(text, Map.class) : (Map<String, Object>) value;
    }

    Map<String, Object> document() throws Exception {
        return map(request("GET", "/v3/api-docs", 200, null).body());
    }

    Map<String, Object> operation(Map<String, Object> document, String path, String method) {
        return map(map(map(document.get("paths")).get(path)).get(method));
    }

    Map<String, Object> examples(Map<String, Object> document, String path, String method) {
        var operation = operation(document, path, method);
        var content = map(map(operation.get("requestBody")).get("content"));
        var mediaType = map(content.get("application/json"));
        if (mediaType.containsKey("examples")) return map(mediaType.get("examples"));
        if (mediaType.containsKey("example")) return Map.of("default", Map.of("value", mediaType.get("example")));
        return Map.of();
    }

    String exampleBody(Map<String, Object> examples, String name) {
        Object value = map(examples.get(name)).get("value");
        return value instanceof String text ? text : json.writeValueAsString(value);
    }
}
