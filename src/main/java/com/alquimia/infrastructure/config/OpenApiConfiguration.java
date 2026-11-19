package com.alquimia.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI alquimiaOpenApi() {
        // Sin servidor fijo: springdoc obtiene la URL y el context-path de la peticion.
        return new OpenAPI().info(new Info()
            .title("Operacion Alquimia - Protocolo Savitar")
            .version("1.0.0")
            .description("API REST de vigilancia con DDD y Arquitectura Hexagonal. "
                + "Permite procesamiento completo, registro individual, consulta y procesamiento acumulado, "
                + "reemplazo completo, actualizacion parcial y eliminacion. "
                + "Los datos son temporales en memoria. Para probar las operaciones acumuladas, "
                + "registre los tres dispositivos usando sus ejemplos correspondientes. "
                + "Las palabras vacias representan informacion no recibida. "
                + "Los ejemplos completos localizan el laboratorio en (0,0)."));
    }
}
