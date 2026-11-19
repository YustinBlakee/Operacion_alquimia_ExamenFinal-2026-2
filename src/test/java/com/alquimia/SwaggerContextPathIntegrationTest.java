package com.alquimia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.servlet.context-path=/api")
class SwaggerContextPathIntegrationTest extends SwaggerHttpSupport {
    @Override String contextPath() { return "/api"; }

    @Test
    void contextPathPrefixesSwaggerDocsAndCanonicalLocation() throws Exception {
        var page = request("GET", "/swagger-ui.html", 200, null);
        assertThat(page.uri().getPath()).startsWith("/api/swagger-ui/");
        var config = map(request("GET", "/v3/api-docs/swagger-config", 200, null).body());
        assertThat(config.get("url")).isEqualTo("/api/v3/api-docs");
        assertThat(document().get("paths")).isNotNull();
        var registered = request("POST", "/api/readings/STAR_LABS", 201,
            "{\"distance\":538.5164807134504,\"message\":[\"el\"]}");
        assertThat(registered.headers().firstValue("Location")).contains("/api/api/readings/star-labs");
        assertThat(map(registered.body()).get("device")).isEqualTo("star-labs");
    }
}
