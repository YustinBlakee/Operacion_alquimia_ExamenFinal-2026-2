package com.alquimia.infrastructure.adapter.in.web;

import com.alquimia.application.port.in.IntelligenceUseCases;
import com.alquimia.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Operacion Alquimia", description = "Las seis funcionalidades del examen. Los datos se conservan en memoria y se pierden al reiniciar.")
public class IntelligenceController {
    private static final String COMPLETE_REQUEST = """
        {"devices":[
          {"device":"star-labs","distance":538.5164807134504,"message":["el","",""]},
          {"device":"ccpd","distance":141.4213562373095,"message":["","laboratorio",""]},
          {"device":"star-satellite","distance":509.9019513592785,"message":["","","oculto"]}
        ]}
        """;
    private static final String LABS_READING = """
        {"distance":538.5164807134504,"message":["el","",""]}
        """;
    private static final String CCPD_READING = """
        {"distance":141.4213562373095,"message":["","laboratorio",""]}
        """;
    private static final String SATELLITE_READING = """
        {"distance":509.9019513592785,"message":["","","oculto"]}
        """;
    private static final String COMPLETE_RESULT = """
        {"position":{"x":0.0,"y":0.0},"message":"el laboratorio oculto"}
        """;

    private final IntelligenceUseCases useCases;

    public IntelligenceController(IntelligenceUseCases useCases) { this.useCases = useCases; }

    public record ReadingRequest(
            @Schema(description = "Identificador o alias: star-labs, CCPD, STAR Labs, STAR_LABS o S.T.A.R. Satellite. La respuesta utiliza el identificador canonico.", example = "star-labs")
            @NotNull String device,
            @Schema(description = "Distancia finita y no negativa", minimum = "0") @NotNull Double distance,
            @ArraySchema(schema = @Schema(type = "string", description = "Palabra recibida o cadena vacia si no se recibio"))
            @NotNull List<String> message) {
        Reading toDomain() { return Reading.of(Device.fromId(device), distance, message); }
    }
    public record DataRequest(
            @Schema(description = "Distancia finita y no negativa", minimum = "0") @NotNull Double distance,
            @ArraySchema(schema = @Schema(type = "string", description = "Palabra recibida o cadena vacia"))
            @NotNull List<String> message) { }
    public record ProcessingRequest(@NotEmpty @Size(max = 3) List<@NotNull @Valid ReadingRequest> devices) { }
    public record ReadingResponse(String device, double distance, List<String> message) {
        static ReadingResponse from(Reading reading) {
            return new ReadingResponse(reading.device().id(), reading.distance(), reading.message());
        }
    }
    public record DeviceResponse(String device, Position position) { }
    @Schema(name = "ReadingPatch", minProperties = 1, additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        description = "Incluya distance, message o ambos. Los campos omitidos se conservan; null explicito no es valido.")
    public record PatchSchema(
            @Schema(description = "Distancia nueva; omitir para conservar la anterior", minimum = "0") Double distance,
            @ArraySchema(schema = @Schema(type = "string")) List<String> message) { }
    @Schema(name = "ApiProblem", description = "Error con estado HTTP y codigo estable")
    public record ProblemSchema(String type, String title, int status, String detail, String instance, String code) { }

    @GetMapping("/devices")
    @Operation(summary = "0. Consultar el catalogo de dispositivos", description = "Posiciones fijas e identificadores permitidos; no modifica lecturas.")
    @ApiResponse(responseCode = "200", description = "Los tres dispositivos", content = @Content(mediaType = "application/json",
        array = @ArraySchema(schema = @Schema(implementation = DeviceResponse.class)),
        examples = @ExampleObject(value = """
            [{"device":"star-labs","position":{"x":-500.0,"y":-200.0}},
             {"device":"ccpd","position":{"x":100.0,"y":-100.0}},
             {"device":"star-satellite","position":{"x":500.0,"y":100.0}}]
            """)))
    public List<DeviceResponse> devices() {
        return java.util.Arrays.stream(Device.values())
            .map(device -> new DeviceResponse(device.id(), device.position())).toList();
    }

    @PostMapping("/intelligence")
    @Operation(summary = "1. Procesar la informacion completa", description = "Envia los tres dispositivos en una sola peticion, en cualquier orden. Calcula ubicacion y mensaje sin almacenar las lecturas.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = ProcessingRequest.class),
            examples = @ExampleObject(name = "Laboratorio en (0,0)", value = COMPLETE_REQUEST))))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ubicacion y mensaje reconstruidos", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = IntelligenceResult.class), examples = @ExampleObject(value = COMPLETE_RESULT))),
        @ApiResponse(responseCode = "400", description = "Datos invalidos, dispositivos desconocidos o repetidos", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemSchema.class))),
        @ApiResponse(responseCode = "422", description = "Informacion insuficiente, distancias incompatibles o mensaje incompleto/contradictorio", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemSchema.class)))
    })
    public IntelligenceResult process(@Valid @RequestBody ProcessingRequest request) {
        return useCases.process(request.devices().stream().map(ReadingRequest::toDomain).toList());
    }

    @GetMapping("/intelligence")
    @Operation(summary = "3. Procesar las lecturas acumuladas", description = "Registra primero las tres lecturas con POST /api/readings/{device}. Devuelve ambos resultados sin borrar ni modificar los registros.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ubicacion y mensaje reconstruidos", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = IntelligenceResult.class), examples = @ExampleObject(value = COMPLETE_RESULT))),
        @ApiResponse(responseCode = "422", description = "Faltan lecturas o no permiten reconstruir una ubicacion y un mensaje compatibles", content = @Content(mediaType = "application/problem+json",
            schema = @Schema(implementation = ProblemSchema.class), examples = @ExampleObject(value = """
                {"type":"about:blank","title":"Unprocessable Content","status":422,
                 "detail":"Se necesitan lecturas de los tres dispositivos.","instance":"/api/intelligence","code":"INSUFFICIENT_DATA"}
                """)))
    })
    public IntelligenceResult processStored() { return useCases.processStored(); }

    @GetMapping("/readings")
    @Operation(summary = "3. Consultar todas las lecturas", description = "Devuelve un arreglo vacio cuando no hay informacion registrada.")
    @ApiResponse(responseCode = "200", description = "Lecturas almacenadas", content = @Content(mediaType = "application/json",
        array = @ArraySchema(schema = @Schema(implementation = ReadingResponse.class)),
        examples = @ExampleObject(value = """
            [{"device":"star-labs","distance":538.5164807134504,"message":["el","",""]},
             {"device":"ccpd","distance":141.4213562373095,"message":["","laboratorio",""]},
             {"device":"star-satellite","distance":509.9019513592785,"message":["","","oculto"]}]
            """)))
    public List<ReadingResponse> readings() {
        return useCases.findAll().stream().map(ReadingResponse::from).toList();
    }

    @GetMapping("/readings/{device}")
    @Operation(summary = "3. Consultar una lectura individual")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lectura registrada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReadingResponse.class))),
        @ApiResponse(responseCode = "400", description = "Dispositivo desconocido", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemSchema.class))),
        @ApiResponse(responseCode = "404", description = "Dispositivo conocido sin lectura registrada", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemSchema.class)))
    })
    public ReadingResponse reading(@Parameter(description = "Identificador o alias, por ejemplo star-labs, STAR_LABS, CCPD o S.T.A.R. Satellite", schema = @Schema(example = "star-labs", defaultValue = "star-labs")) @PathVariable String device) {
        return ReadingResponse.from(useCases.find(Device.fromId(device)));
    }

    @PostMapping("/readings/{device}")
    @Operation(summary = "2. Registrar informacion individual", description = "Ejecuta una vez por dispositivo. Selecciona un ejemplo y el mismo identificador en device: STAR Labs, CCPD o S.T.A.R. Satellite. Pueden llegar en cualquier orden. Un segundo POST al mismo dispositivo devuelve 409.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = DataRequest.class), examples = {
                @ExampleObject(name = "star-labs", summary = "Usar device=star-labs", value = LABS_READING),
                @ExampleObject(name = "ccpd", summary = "Usar device=ccpd", value = CCPD_READING),
                @ExampleObject(name = "star-satellite", summary = "Usar device=star-satellite", value = SATELLITE_READING)
            })))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Lectura creada; Location indica su ruta", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = ReadingResponse.class), examples = @ExampleObject(value = """
                {"device":"star-labs","distance":538.5164807134504,"message":["el","",""]}
                """))),
        @ApiResponse(responseCode = "400", description = "Datos invalidos o dispositivo desconocido", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemSchema.class))),
        @ApiResponse(responseCode = "409", description = "ALREADY_REGISTERED: utilice PUT o PATCH para modificar", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemSchema.class)))
    })
    public ResponseEntity<ReadingResponse> register(@Parameter(description = "Identificador o alias del dispositivo del ejemplo seleccionado; se acepta STAR_LABS, CCPD o S.T.A.R. Satellite", schema = @Schema(example = "star-labs", defaultValue = "star-labs")) @PathVariable String device, @Valid @RequestBody DataRequest request, HttpServletRequest servletRequest) {
        Reading reading = useCases.register(Reading.of(Device.fromId(device), request.distance(), request.message()));
        return ResponseEntity.created(URI.create(servletRequest.getContextPath() + "/api/readings/" + reading.device().id()))
            .body(ReadingResponse.from(reading));
    }

    @PutMapping("/readings/{device}")
    @Operation(summary = "4. Reemplazar completamente una lectura", description = "Requiere una lectura existente y ambos campos. Cambia solo el dispositivo seleccionado. El ejemplo cambia STAR Labs; conserve device=star-labs.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = DataRequest.class), examples = @ExampleObject(value = """
                {"distance":538.52,"message":["un","",""]}
                """))))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lectura reemplazada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReadingResponse.class),
            examples = @ExampleObject(value = """
                {"device":"star-labs","distance":538.52,"message":["un","",""]}
                """))),
        @ApiResponse(responseCode = "400", description = "Falta distance o message, datos invalidos o dispositivo desconocido", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemSchema.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND: registre primero el dispositivo", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemSchema.class)))
    })
    public ReadingResponse replace(@Parameter(description = "Identificador o alias, por ejemplo STAR_LABS, CCPD o S.T.A.R. Satellite", schema = @Schema(example = "star-labs", defaultValue = "star-labs")) @PathVariable String device, @Valid @RequestBody DataRequest request) {
        return ReadingResponse.from(useCases.replace(Reading.of(Device.fromId(device), request.distance(), request.message())));
    }

    @PatchMapping("/readings/{device}")
    @Operation(summary = "5. Actualizar parcialmente una lectura", description = "Incluye distance, message o ambos. Los omitidos conservan su valor. No se acepta null explicito ni cuerpo vacio. message sustituye el fragmento completo. Ejemplos para device=star-labs.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = PatchSchema.class), examples = {
                @ExampleObject(name = "Solo distancia", value = "{\"distance\":538.5164807134504}"),
                @ExampleObject(name = "Solo mensaje", value = "{\"message\":[\"este\",\"\",\"\"]}"),
                @ExampleObject(name = "Ambos campos", value = "{\"distance\":538.5164807134504,\"message\":[\"este\",\"\",\"\"]}")
            })))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lectura actualizada; los campos omitidos se conservan", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReadingResponse.class))),
        @ApiResponse(responseCode = "400", description = "Campos invalidos, null explicito, cuerpo vacio o dispositivo desconocido", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemSchema.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND: registre primero el dispositivo", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemSchema.class)))
    })
    public ReadingResponse update(@Parameter(description = "Identificador o alias, por ejemplo STAR_LABS, CCPD o S.T.A.R. Satellite", schema = @Schema(example = "star-labs", defaultValue = "star-labs")) @PathVariable String device, @RequestBody PatchRequest request) {
        request.validate();
        return ReadingResponse.from(useCases.update(Device.fromId(device), request.distance(), request.message()));
    }

    @DeleteMapping("/readings/{device}")
    @Operation(summary = "6. Eliminar la informacion de un dispositivo", description = "Elimina unicamente la lectura seleccionada. Devuelve 204 sin cuerpo. Una consulta posterior de esa lectura devuelve 404, y el procesamiento acumulado devuelve 422 si falta ese dispositivo.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Lectura eliminada; sin cuerpo", content = @Content),
        @ApiResponse(responseCode = "400", description = "Dispositivo desconocido", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemSchema.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND: el dispositivo no tiene lectura registrada", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemSchema.class)))
    })
    public ResponseEntity<Void> delete(@Parameter(description = "Identificador o alias, por ejemplo STAR_LABS, CCPD o S.T.A.R. Satellite", schema = @Schema(example = "ccpd", defaultValue = "ccpd")) @PathVariable String device) {
        useCases.delete(Device.fromId(device));
        return ResponseEntity.noContent().build();
    }
}
