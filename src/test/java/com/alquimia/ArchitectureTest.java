package com.alquimia;

import com.alquimia.application.IntelligenceService;
import com.alquimia.application.port.in.IntelligenceUseCases;
import com.alquimia.application.port.out.ReadingRepository;
import com.alquimia.domain.IntelligenceProcessor;
import com.alquimia.infrastructure.adapter.out.InMemoryReadingRepository;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

import static org.assertj.core.api.Assertions.*;

class ArchitectureTest {
    @Test
    void coreSourceImportsOnlyJavaAndItsInnerLayers() throws Exception {
        Pattern imports = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([^;]+);");
        for (String layer : new String[] {"domain", "application"}) {
            Path directory = Path.of("src/main/java/com/alquimia", layer);
            try (var sources = Files.walk(directory)) {
                for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                    var matcher = imports.matcher(Files.readString(source));
                    while (matcher.find()) {
                        assertThat(allowed(layer, matcher.group(1).trim()))
                            .as("Importacion de %s: %s", source, matcher.group(1)).isTrue();
                    }
                }
            }
        }
    }

    @Test
    void compiledCoreHasNoHiddenFrameworkOrInfrastructureDependencies() throws Exception {
        Path classes = Path.of(IntelligenceProcessor.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var output = new StringWriter();
        var errors = new StringWriter();
        var jdeps = ToolProvider.findFirst("jdeps").orElseThrow(() -> new IllegalStateException("Las pruebas requieren un JDK completo"));
        int status = jdeps.run(new PrintWriter(output), new PrintWriter(errors), "-verbose:class", "-filter:none",
            "-include", "com\\.alquimia\\.(domain|application)\\..*", classes.toString());
        assertThat(status).as(errors.toString()).isZero();
        Pattern dependency = Pattern.compile("(?m)^\\s*(com\\.alquimia\\.(domain|application)\\.\\S+)\\s+->\\s+(\\S+)");
        var matcher = dependency.matcher(output.toString());
        int checked = 0;
        while (matcher.find()) {
            checked++;
            assertThat(allowed(matcher.group(2), matcher.group(3)))
                .as("Dependencia compilada: %s -> %s", matcher.group(1), matcher.group(3)).isTrue();
        }
        assertThat(checked).as("jdeps debe analizar dependencias reales del nucleo").isPositive();
    }

    @Test
    void adaptersAndApplicationImplementExplicitPorts() {
        assertThat(IntelligenceUseCases.class.isInterface()).isTrue();
        assertThat(ReadingRepository.class.isInterface()).isTrue();
        assertThat(IntelligenceUseCases.class).isAssignableFrom(IntelligenceService.class);
        assertThat(ReadingRepository.class).isAssignableFrom(InMemoryReadingRepository.class);
    }

    private boolean allowed(String layer, String dependency) {
        return dependency.startsWith("java.") || dependency.startsWith("com.alquimia.domain.")
            || (layer.equals("application") && dependency.startsWith("com.alquimia.application."));
    }
}
