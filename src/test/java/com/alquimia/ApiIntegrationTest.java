package com.alquimia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.alquimia.application.port.in.IntelligenceUseCases;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {
    @LocalServerPort int port;
    @Autowired IntelligenceUseCases useCases;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void clear() { useCases.findAll().forEach(reading -> useCases.delete(reading.device())); }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
            .header("Content-Type", "application/json")
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void fullLifecycleIncludingAccumulatedProcessing() throws Exception {
        assertThat(request("GET", "/intelligence", null).statusCode()).isEqualTo(422);
        var created = request("POST", "/readings/star-labs", """
            {"distance":538.5164807134504,"message":["el","",""]}
            """);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location")).contains("/api/readings/star-labs");
        assertThat(request("POST", "/readings/ccpd", """
            {"distance":141.4213562373095,"message":["","laboratorio",""]}
            """).statusCode()).isEqualTo(201);
        assertThat(request("POST", "/readings/star-satellite", """
            {"distance":509.9019513592785,"message":["","","oculto"]}
            """).statusCode()).isEqualTo(201);
        var result = request("GET", "/intelligence", null);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).contains("el laboratorio oculto", "\"position\"");
        assertThat(request("POST", "/readings/ccpd", "{\"distance\":2,\"message\":[\"x\"]}").statusCode()).isEqualTo(409);
        assertThat(request("PUT", "/readings/ccpd", "{\"distance\":5,\"message\":[\"nuevo\"]}").statusCode()).isEqualTo(200);
        var patch = request("PATCH", "/readings/ccpd", "{\"distance\":7}");
        assertThat(patch.statusCode()).isEqualTo(200);
        assertThat(patch.body()).contains("nuevo", "7.0");
        var messagePatch = request("PATCH", "/readings/ccpd", "{\"message\":[\"parcial\"]}");
        assertThat(messagePatch.body()).contains("parcial", "7.0");
        assertThat(request("DELETE", "/readings/ccpd", null).statusCode()).isEqualTo(204);
        assertThat(request("GET", "/readings/ccpd", null).statusCode()).isEqualTo(404);
        assertThat(request("GET", "/readings/star-labs", null).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/intelligence", null).statusCode()).isEqualTo(422);
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
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("laboratorio oculto");
        assertThat(request("GET", "/readings", null).body()).isEqualTo("[]");
    }

    @Test
    void rejectsMalformedInvalidUnknownAndExplicitNullWithoutChangingState() throws Exception {
        assertThat(request("POST", "/readings/unknown", "{\"distance\":1,\"message\":[\"x\"]}").statusCode()).isEqualTo(400);
        assertThat(request("PUT", "/readings/ccpd", "{\"distance\":1,\"message\":[\"x\"]}").statusCode()).isEqualTo(404);
        for (String body : new String[] {"{}", "{", "{\"distance\":-1,\"message\":[\"x\"]}",
            "{\"distance\":null,\"message\":[\"x\"]}", "{\"distance\":1,\"message\":[null]}",
            "{\"distance\":1,\"message\":[\"x\"],\"typo\":1}", "{\"distance\":\"abc\",\"message\":[\"x\"]}",
            "{\"distance\":\"1\",\"message\":[\"x\"]}", "{\"distance\":1,\"message\":[123]}",
            "{\"distance\":1,\"message\":[true]}"}) {
            assertThat(request("POST", "/readings/ccpd", body).statusCode()).as(body).isEqualTo(400);
        }
        assertThat(request("POST", "/readings/ccpd", "{\"distance\":1,\"message\":[\"original\"]}").statusCode()).isEqualTo(201);
        for (String body : new String[] {"{}", "{\"distance\":null}", "{\"message\":null}",
            "{\"message\":[]}", "{\"distance\":-1}", "{\"typo\":1}"}) {
            assertThat(request("PATCH", "/readings/ccpd", body).statusCode()).as(body).isEqualTo(400);
        }
        assertThat(request("GET", "/readings/ccpd", null).body()).contains("original", "1.0");
        var problem = request("GET", "/readings/star-labs", null);
        assertThat(problem.body()).contains("NOT_FOUND", "detail");
        assertThat(problem.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
    }
}
