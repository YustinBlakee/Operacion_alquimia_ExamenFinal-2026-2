# Operación Alquimia — Protocolo Savitar

API REST en Java 17 y Spring Boot 4.1.2 que localiza el laboratorio de Alchemy y reconstruye mensajes recibidos por tres dispositivos. Implementa DDD y Arquitectura Hexagonal, con datos temporales en memoria.

## Verificación rápida para el evaluador

1. Descargue el proyecto completo o clone el repositorio y entre en la carpeta que contiene `pom.xml`. Conserve los archivos ocultos `.mvn` y `.github`.
2. Compruebe `java -version` y `javac -version` (JDK 17 como mínimo; la matriz de verificación incluye 17, 21 y 25).
3. Ejecute `clean verify` con el comando de su sistema, indicado abajo. **No necesita iniciar el servidor antes:** las pruebas HTTP arrancan uno con puerto aleatorio y datos aislados.
4. El resultado esperado es **90 pruebas, cero fallos, cero errores y `BUILD SUCCESS`**. Los informes se generan en `target/surefire-reports/` y el JAR en `target/operacion-alquimia-1.0.0.jar`.
5. Ejecute el JAR y abra [Swagger UI](http://localhost:8080/swagger-ui.html) para consultar y ejecutar las peticiones desde el navegador. La [guía del evaluador](docs/verification.md) explica cómo comprobar las seis funcionalidades mediante Swagger UI o curl.

Las pruebas verifican los valores devueltos por la API, errores, aislamiento entre dispositivos, concurrencia y la regresión numérica con distancias grandes. No requieren aplicaciones adicionales ni servicios externos.

## Requisitos y portabilidad

- Windows, Linux o macOS, con un **JDK 17 como mínimo, compatible con Spring Boot 4.1**, para la arquitectura del computador (x64 o ARM64). La matriz del proyecto incluye JDK 17, 21 y 25; esto no garantiza todas las versiones futuras de Java.
- `java -version` debe funcionar. Para compilar, `JAVA_HOME` debe apuntar al JDK o `javac` debe estar disponible.
- No requiere instalar Maven ni un IDE: incluye Maven Wrapper 3.3.4 y fija Maven 3.10.1.
- La primera compilación necesita Internet para descargar Maven y dependencias desde Maven Central. El JAR construido no necesita Internet ni Maven para ejecutarse.
- No utiliza base de datos, servicios externos, rutas absolutas, bibliotecas nativas ni características preview de Java. Los archivos usan UTF-8 y Maven compila con `--release 17`.

Consulte la [compatibilidad oficial de Spring Boot](https://docs.spring.io/spring-boot/4.1/system-requirements.html).

## Compilar, probar y ejecutar

Abra una terminal en la carpeta que contiene `pom.xml`.

Si la aplicación ya está ejecutándose desde el JAR que va a reconstruir, deténgala con `Ctrl+C` antes de ejecutar `clean verify`. En Windows el JAR en uso queda bloqueado. Luego arranque nuevamente el JAR generado para cargar la versión actualizada.

Windows (PowerShell):

```powershell
.\mvnw.cmd clean verify
if ($LASTEXITCODE -eq 0) {
    java -jar target/operacion-alquimia-1.0.0.jar
}
```

Linux y macOS:

```sh
sh ./mvnw clean verify && java -jar target/operacion-alquimia-1.0.0.jar
```

Usar `sh ./mvnw` evita depender del permiso ejecutable al copiar el proyecto por ZIP. También puede ejecutar `chmod +x mvnw` y luego `./mvnw`.

Para desarrollo, ejecute `.\mvnw.cmd spring-boot:run` (Windows) o `sh ./mvnw spring-boot:run` (Linux/macOS).

### Uso con Visual Studio Code

El proyecto desactiva la compilación automática de la extensión Java mediante `.vscode/settings.json`. Maven se encarga de compilar: así se evita que el editor y Maven modifiquen simultáneamente `target/classes`. Después de editar código, detenga la aplicación y ejecute nuevamente el comando de desarrollo o compile el JAR.

Si aparece `NoSuchFileException` para un archivo `.class` dentro de `target` durante la compilación, asegúrese de que no haya otra compilación ejecutándose y vuelva a ejecutar `clean verify`. Si VS Code ya estaba abierto al incorporar esta configuración, use **Developer: Reload Window** desde la paleta de comandos para recargarla antes de compilar otra vez.

La API escucha en `http://localhost:8080/api` y la documentación interactiva en `http://localhost:8080/swagger-ui.html`. Para cambiar el puerto de forma idéntica en los tres sistemas:

```sh
java -jar target/operacion-alquimia-1.0.0.jar --server.port=8081
```

El arranque correcto muestra `Tomcat started on port 8080` y `Started OperacionAlquimiaApplication`. Mantenga esa terminal abierta; desde otra terminal o un navegador consulte `http://localhost:8080/api/devices`. Debe responder HTTP 200 con los tres dispositivos. La ruta `/` no es una página web del proyecto.

También acepta la variable de entorno `PORT`. Detenga el proceso con `Ctrl+C`. **Los datos se pierden al reiniciar**, por requisito del examen. Cada instancia mantiene sus propios datos.

Para otro computador: copie el JAR y ejecútelo con Java compatible, o copie el proyecto completo (incluidos `.mvn`, `mvnw` y `mvnw.cmd`) para compilarlo. No necesita copiar `target` ni `.m2` si comparte los fuentes.

## Swagger UI y OpenAPI

Con el servidor iniciado, abra [Swagger UI](http://localhost:8080/swagger-ui.html). La entrada redirige a `/swagger-ui/index.html`; la página permite elegir una operación, pulsar **Try it out**, completar los parámetros y el JSON y pulsar **Execute**. La descripción OpenAPI está disponible en [`/v3/api-docs`](http://localhost:8080/v3/api-docs).

La documentación usa `springdoc-openapi-starter-webmvc-ui` 3.2.0, cuya [matriz oficial de compatibilidad](https://springdoc.org/) admite Spring Boot 4.1.x con springdoc 3.1.x o 3.2.x. Las páginas, scripts y estilos de Swagger UI se incluyen en el JAR: una vez compilado, el servidor y su documentación se pueden usar localmente sin conexión.

Las URL anteriores usan la configuración predeterminada, sin `server.servlet.context-path`. Si se configura ese prefijo, se antepone a todas las rutas: con `context-path=/api`, Swagger queda en `/api/swagger-ui.html`, OpenAPI en `/api/v3/api-docs` y los controladores en `/api/api/...`, porque ya declaran `/api`. No necesita configurar un prefijo adicional para ejecutar el proyecto.

## Dispositivos

| ID de la API | Nombre | Posición |
| --- | --- | --- |
| `star-labs` | STAR Labs | (-500, -200) |
| `ccpd` | CCPD | (100, -100) |
| `star-satellite` | S.T.A.R. Satellite | (500, 100) |

`GET /api/devices` consulta este catálogo y las respuestas siempre usan sus identificadores canónicos. Las entradas también aceptan los nombres del enunciado, sin distinguir mayúsculas y omitiendo espacios, guiones, guiones bajos y puntos: `STAR Labs`, `STAR_LABS` y `star-labs` identifican el mismo dispositivo; `S.T.A.R. Satellite` identifica `star-satellite`. Otros nombres se rechazan como `UNKNOWN_DEVICE`.

## Contrato HTTP

| Método | Ruta | Operación | Éxito |
| --- | --- | --- | --- |
| POST | `/api/intelligence` | Procesar las tres lecturas enviadas, sin almacenarlas | 200 |
| POST | `/api/readings/{device}` | Registrar los datos de un dispositivo | 201 + Location |
| GET | `/api/readings` | Consultar todas las lecturas almacenadas | 200 |
| GET | `/api/readings/{device}` | Consultar una lectura | 200 |
| GET | `/api/intelligence` | Procesar una instantánea de las lecturas almacenadas | 200 |
| PUT | `/api/readings/{device}` | Reemplazar distancia y mensaje completos | 200 |
| PATCH | `/api/readings/{device}` | Modificar distancia, mensaje o ambos | 200 |
| DELETE | `/api/readings/{device}` | Eliminar una lectura | 204 |

POST individual y PUT requieren `distance` y `message`. PATCH acepta uno o ambos; los campos omitidos se conservan. `null` explícito, un cuerpo vacío o un campo desconocido se rechazan. PATCH sustituye el arreglo de palabras completo cuando incluye `message`; no edita índices individuales. PUT, PATCH y DELETE requieren una lectura existente (404 si falta). Registrar dos veces devuelve 409: no sobrescribe silenciosamente.

Un arreglo `message: []` es válido al registrar, reemplazar o actualizar una lectura: representa un fragmento sin palabras recibidas. No equivale a omitir el campo ni a enviar `null`. El procesamiento puede reconstruir el mensaje si los otros fragmentos completan sus posiciones; si faltan palabras, responde 422 `INCOMPLETE_MESSAGE`.

### Ejemplo de procesamiento completo

El archivo [examples/complete-request.json](examples/complete-request.json) contiene distancias compatibles con el punto (0, 0). Puede enviarlo desde Linux/macOS:

```sh
curl -X POST http://localhost:8080/api/intelligence \
  -H 'Content-Type: application/json' \
  --data-binary @examples/complete-request.json
```

En Windows PowerShell:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/intelligence' -ContentType 'application/json' -InFile 'examples/complete-request.json'
```

Cuerpo enviado:

```json
{
  "devices": [
    {"device": "star-labs", "distance": 538.5164807134504, "message": ["el", "", ""]},
    {"device": "ccpd", "distance": 141.4213562373095, "message": ["", "laboratorio", ""]},
    {"device": "star-satellite", "distance": 509.9019513592785, "message": ["", "", "oculto"]}
  ]
}
```

Respuesta (las coordenadas pueden contener pequeños residuos de punto flotante cercanos a cero):

```json
{"position":{"x":0.0,"y":0.0},"message":"el laboratorio oculto"}
```

Los fragmentos también pueden tener distinta longitud. El archivo [examples/unequal-request.json](examples/unequal-request.json) muestra ese caso: cada índice conserva la posición original de la palabra y las posiciones finales omitidas se consideran ausentes. Las cadenas vacías internas siguen ocupando su posición; no se eliminan antes de combinar los fragmentos.

### Registro, actualización y procesamiento acumulado

Cada dispositivo se registra con POST a su ruta y un cuerpo como:

```json
{"distance":538.5164807134504,"message":["el","",""]}
```

Registre los otros dos dispositivos con sus respectivas distancias y fragmentos del ejemplo. Puede hacerlo en cualquier orden. Luego consulte `GET /api/intelligence` para obtener posición y mensaje.

Ejemplo PATCH para cambiar solo la distancia:

```json
{"distance":538.52}
```

Ejemplo PUT para reemplazar ambos campos:

```json
{"distance":538.5164807134504,"message":["el","",""]}
```

## Validaciones y errores

Los errores de negocio y de cuerpo JSON usan `application/problem+json`, con `status`, `detail` y un `code` estable.

| HTTP | Códigos | Significado |
| --- | --- | --- |
| 400 | `INVALID_DATA`, `INVALID_JSON`, `UNKNOWN_DEVICE` | Cuerpo, tipos, campos o dispositivo inválidos |
| 404 | `NOT_FOUND` | Dispositivo conocido sin lectura registrada |
| 409 | `ALREADY_REGISTERED` | Registro duplicado |
| 422 | `INSUFFICIENT_DATA` | Faltan lecturas de dispositivos |
| 422 | `INCONSISTENT_DISTANCES` | Las distancias no producen una posición compatible |
| 422 | `INCOMPLETE_MESSAGE` | Ningún dispositivo recibió una palabra necesaria |
| 422 | `CONFLICTING_MESSAGE` | Hay palabras diferentes en la misma posición |

Ejemplo:

```json
{"type":"about:blank","title":"Not Found","status":404,"detail":"El dispositivo no tiene informacion registrada.","instance":"/api/readings/ccpd","code":"NOT_FOUND"}
```

Las distancias deben ser finitas y no negativas. `message` debe ser un arreglo de cadenas, sin elementos nulos; puede estar vacío. No se imponen topes de negocio adicionales para distancia, cantidad de posiciones o longitud de las cadenas. El contenido de cada cadena se conserva literalmente, incluidos sus espacios; solo `""` representa una palabra ausente. Siguen aplicándose los límites técnicos del formato JSON, de la memoria disponible y de la precisión numérica.

### Tolerancia de las distancias

El PDF no fija una precisión numérica. El valor predeterminado de aceptación es **0,01 unidades por distancia** y puede ajustarse al arrancar:

```sh
java -jar target/operacion-alquimia-1.0.0.jar --alquimia.distance-tolerance=0.001
```

También puede usar la variable de entorno `DISTANCE_TOLERANCE`. El valor debe ser finito y estrictamente positivo; una configuración inválida impide el arranque. El sistema acepta una posición únicamente si las tres distancias cumplen la tolerancia configurada. Reducirla exige datos más precisos y puede hacer que distancias redondeadas dejen de ser compatibles.

## Reglas adoptadas donde el enunciado no precisa detalles

El PDF no define todos los detalles del contrato. Esta implementación adopta estas reglas explícitas, que pueden ajustarse si el profesor especifica otras:

1. Los arreglos pueden tener distinta longitud y **conservan la posición original de cada palabra desde el índice 0**. Las posiciones finales omitidas equivalen a ausencias. `""` indica ausencia en su posición, no se elimina antes de combinar los fragmentos. Sin información sobre un desplazamiento, el sistema no adivina ni cambia el índice de las palabras.
2. Dos palabras distintas en la misma posición son un conflicto; las comparaciones distinguen mayúsculas y tildes. No se adivinan palabras faltantes.
3. La trilateración usa distancias euclidianas en un plano. Obtiene una estimación lineal con diferencias de cuadrados factorizadas y la refina numéricamente cuando es necesario. Solo devuelve una posición si **cada una** de las tres distancias tiene un error absoluto dentro de la tolerancia configurada. No devuelve resultados fuera de esa tolerancia aunque el refinamiento haya reducido el error.
4. El procesamiento completo es independiente del almacenamiento. Un error de procesamiento no borra ni cambia las lecturas.
5. El registro duplicado es un conflicto. PUT reemplaza únicamente registros existentes; PATCH conserva los campos omitidos y rechaza `null` explícito.
6. Un fragmento vacío o de menor longitud no invalida por sí mismo el registro. Durante el procesamiento debe existir al menos una palabra recibida en cada posición del mensaje resultante; si los tres fragmentos están vacíos o queda un hueco, informa `INCOMPLETE_MESSAGE`.

## Arquitectura y pruebas

Consulte [docs/architecture.md](docs/architecture.md) para responsabilidades, flujo y justificación del modelo.

`clean verify` ejecuta pruebas de dominio, reconstrucción de mensajes, aplicación/concurrencia, arquitectura y HTTP real, incluidos el catálogo de dispositivos, sus alias y los endpoints de documentación. Se comprueban fragmentos de distinta longitud, fragmentos vacíos, cadenas largas, conservación de campos, posiciones cercanas, distancia cero y distancias grandes. Las pruebas de dominio también recorren 250 muestras deterministas a distintas escalas; el caso `(100000000, 100000000)` reproduce el defecto de precisión corregido.

Esta revisión pasó localmente **90 pruebas en Windows con Java 17.0.21, 21.0.13 y 25.0.5**. Incluye las 56 pruebas anteriores, 27 casos de alias, 3 comprobaciones arquitectónicas y 4 de Swagger/OpenAPI. Se ejecutó `clean verify` con Java 17 y se verificó el JAR desde otra carpeta, con sus recursos de Swagger embebidos y un proxy externo inutilizable. La compilación local y el flujo remoto son evidencias distintas.

La tolerancia configurada se aplica a las distancias, no garantiza esa misma precisión para las coordenadas. Cuando el laboratorio está muy lejos comparado con la separación entre dispositivos, pequeños errores de las distancias pueden producir mayor incertidumbre en las coordenadas. Los cálculos usan `double`: quitar un máximo de entrada no garantiza procesar toda magnitud finita con precisión suficiente. El procesamiento rechaza datos incompatibles o para los que no consigue una posición finita dentro de la tolerancia.

El flujo [.github/workflows/verify.yml](.github/workflows/verify.yml) ejecuta nueve combinaciones: Windows, Linux y macOS con JDK 17, 21 y 25, después de cada push o pull request. Consulte el [resultado de GitHub Actions](https://github.com/YustinBlakee/Operacion_alquimia_ExamenFinal-2026-2/actions/workflows/verify.yml) para el commit que está evaluando. Una verificación completa debe mostrar las nueve tareas finalizadas correctamente; los resultados de otros commits no verifican una revisión diferente.
