package com.alquimia;

import com.alquimia.domain.IntelligenceResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SwaggerIntegrationTest extends SwaggerHttpSupport {
    @Test
    void openApiDescribesAllSixRequirementsAndPatchFields() throws Exception {
        var document = document();
        assertThat(document.get("openapi").toString()).startsWith("3.");
        assertThat(map(document.get("info")).get("title").toString()).contains("Alquimia");
        for (String[] operation : new String[][] {{"/api/intelligence", "post"}, {"/api/intelligence", "get"},
            {"/api/readings", "get"}, {"/api/readings/{device}", "post"}, {"/api/readings/{device}", "put"},
            {"/api/readings/{device}", "patch"}, {"/api/readings/{device}", "delete"}}) {
            assertThat(operation(document, operation[0], operation[1]).get("summary")).isNotNull();
        }
        for (String[] operation : new String[][] {{"/api/intelligence", "post"}, {"/api/readings/{device}", "post"},
            {"/api/readings/{device}", "put"}, {"/api/readings/{device}", "patch"}}) {
            assertThat(examples(document, operation[0], operation[1])).isNotEmpty();
        }
        var schemas = map(map(document.get("components")).get("schemas"));
        assertThat(map(map(schemas.get("ReadingPatch")).get("properties")).keySet())
            .containsExactlyInAnyOrder("distance", "message");
        assertThat(map(schemas.get("ReadingPatch")).get("minProperties")).isEqualTo(1);
        assertThat(map(schemas.get("ReadingPatch")).get("additionalProperties")).isEqualTo(false);
        var patchOperation = operation(document, "/api/readings/{device}", "patch");
        var patchMediaType = map(map(map(patchOperation.get("requestBody")).get("content")).get("application/json"));
        assertThat(map(patchMediaType.get("schema")).get("$ref").toString()).endsWith("/ReadingPatch");
        assertThat(map(map(schemas.get("ReadingRequest")).get("properties")).get("device")).isNotNull();
        assertThat(map(map(map(schemas.get("ReadingRequest")).get("properties")).get("device"))).doesNotContainKey("enum");
    }

    @Test
    void swaggerHtmlAndEveryReferencedAssetAreServedLocally() throws Exception {
        var page = request("GET", "/swagger-ui.html", 200, null);
        assertThat(page.body()).contains("Swagger UI");
        var matcher = Pattern.compile("(?:src|href)=[\"']([^\"']+)[\"']").matcher(page.body());
        var resources = new LinkedHashSet<URI>();
        while (matcher.find()) {
            URI resource = page.uri().resolve(matcher.group(1));
            assertThat(resource.getHost()).isEqualTo("localhost");
            assertThat(resource.getPort()).isEqualTo(port);
            resources.add(resource);
        }
        assertThat(resources).isNotEmpty();
        boolean initializerChecked = false;
        for (URI resource : resources) {
            var response = request("GET", resource.getRawPath(), 200, null);
            if (resource.getPath().endsWith("swagger-initializer.js")) {
                initializerChecked = true;
                assertThat(response.body()).doesNotContain("petstore.swagger.io");
            }
        }
        assertThat(initializerChecked).isTrue();
        var config = map(request("GET", "/v3/api-docs/swagger-config", 200, null).body());
        assertThat(config.get("url")).isEqualTo("/v3/api-docs");
        assertThat(config.get("validatorUrl")).isIn(null, "");
    }

    @Test
    void documentedExamplesExecuteTheWholeLifecycle() throws Exception {
        var document = document();
        var full = examples(document, "/api/intelligence", "post");
        var complete = request("POST", "/api/intelligence", 200, exampleBody(full, full.keySet().iterator().next()));
        var result = json.readValue(complete.body(), IntelligenceResult.class);
        assertThat(result.position().x()).isCloseTo(0, within(1e-8));
        assertThat(result.position().y()).isCloseTo(0, within(1e-8));
        assertThat(result.message()).isEqualTo("el laboratorio oculto");
        assertThat(request("GET", "/api/readings", 200, null).body()).isEqualTo("[]");
        var registered = examples(document, "/api/readings/{device}", "post");
        assertThat(registered.keySet()).containsAll(List.of("star-labs", "ccpd", "star-satellite"));
        for (String device : List.of("star-labs", "ccpd", "star-satellite")) {
            request("POST", "/api/readings/" + device, 201, exampleBody(registered, device));
        }
        assertThat(json.readValue(request("GET", "/api/intelligence", 200, null).body(), IntelligenceResult.class).message())
            .isEqualTo("el laboratorio oculto");
        var replace = examples(document, "/api/readings/{device}", "put");
        request("PUT", "/api/readings/star-labs", 200, exampleBody(replace, replace.keySet().iterator().next()));
        var patch = examples(document, "/api/readings/{device}", "patch");
        for (String example : patch.keySet()) {
            request("PATCH", "/api/readings/star-labs", 200, exampleBody(patch, example));
        }
        request("DELETE", "/api/readings/ccpd", 204, null);
        request("GET", "/api/readings/ccpd", 404, null);
        request("GET", "/api/intelligence", 422, null);
    }
}
