# Operación Alquimia — Protocolo Savitar

API REST en Java 21 y Spring Boot 4.1.2 que localiza el laboratorio de Alchemy y reconstruye mensajes recibidos por tres dispositivos. Implementa DDD y Arquitectura Hexagonal, con datos temporales en memoria.

## Verificación rápida para el evaluador

1. Descargue el proyecto completo o clone el repositorio y entre en la carpeta que contiene `pom.xml`. Conserve los archivos ocultos `.mvn` y `.github`.
2. Compruebe `java -version` y `javac -version` (JDK 21 o 25).
3. Ejecute `clean verify` con el comando de su sistema, indicado abajo. **No necesita iniciar el servidor antes:** las pruebas HTTP arrancan uno con puerto aleatorio y datos aislados.
4. El resultado esperado es **39 pruebas, cero fallos y `BUILD SUCCESS`**. Los informes se generan en `target/surefire-reports/` y el JAR en `target/operacion-alquimia-1.0.0.jar`.
5. Ejecute el JAR y consulte `GET /api/devices`. Para verificar manualmente las seis funcionalidades, siga [docs/verification.md](docs/verification.md), que incluye comandos y respuestas esperadas.

Las pruebas verifican los valores devueltos por la API, errores, aislamiento entre dispositivos, concurrencia y la regresión numérica con distancias grandes. No requieren aplicaciones adicionales ni servicios externos.

## Requisitos y portabilidad

- Windows, Linux o macOS, con un **JDK 21 o superior compatible con Spring Boot 4.1**. Se recomienda JDK 21 LTS o 25 LTS, para la arquitectura del computador (x64 o ARM64).
- `java -version` debe funcionar. Para compilar, `JAVA_HOME` debe apuntar al JDK o `javac` debe estar disponible.
- No requiere instalar Maven ni un IDE: incluye Maven Wrapper 3.3.4 y fija Maven 3.10.1.
- La primera compilación necesita Internet para descargar Maven y dependencias desde Maven Central. El JAR construido no necesita Internet ni Maven para ejecutarse.
- No utiliza base de datos, servicios externos, rutas absolutas, bibliotecas nativas ni características preview de Java. Los archivos usan UTF-8 y Maven compila con `--release 21`.

Consulte la [compatibilidad oficial de Spring Boot](https://docs.spring.io/spring-boot/4.1/system-requirements.html).

## Compilar, probar y ejecutar

Abra una terminal en la carpeta que contiene `pom.xml`.

Si la aplicación ya está ejecutándose desde el JAR que va a reconstruir, deténgala con `Ctrl+C` antes de ejecutar `clean verify`. En Windows el JAR en uso queda bloqueado. Luego arranque nuevamente el JAR generado para cargar la versión actualizada.

Windows (PowerShell):

```powershell
.\mvnw.cmd clean verify
java -jar target/operacion-alquimia-1.0.0.jar
```

Linux y macOS:

```sh
sh ./mvnw clean verify
java -jar target/operacion-alquimia-1.0.0.jar
```

Usar `sh ./mvnw` evita depender del permiso ejecutable al copiar el proyecto por ZIP. También puede ejecutar `chmod +x mvnw` y luego `./mvnw`.

Para desarrollo, ejecute `.\mvnw.cmd spring-boot:run` (Windows) o `sh ./mvnw spring-boot:run` (Linux/macOS).

### Uso con Visual Studio Code

El proyecto desactiva la compilación automática de la extensión Java mediante `.vscode/settings.json`. Maven se encarga de compilar: así se evita que el editor y Maven modifiquen simultáneamente `target/classes`. Después de editar código, detenga la aplicación y ejecute nuevamente el comando de desarrollo o compile el JAR.

Si aparece `NoSuchFileException` para un archivo `.class` dentro de `target` durante la compilación, asegúrese de que no haya otra compilación ejecutándose y vuelva a ejecutar `clean verify`. Si VS Code ya estaba abierto al incorporar esta configuración, use **Developer: Reload Window** desde la paleta de comandos para recargarla antes de compilar otra vez.

La API escucha en `http://localhost:8080/api`. Para cambiar el puerto de forma idéntica en los tres sistemas:

```sh
java -jar target/operacion-alquimia-1.0.0.jar --server.port=8081
```

El arranque correcto muestra `Tomcat started on port 8080` y `Started OperacionAlquimiaApplication`. Mantenga esa terminal abierta; desde otra terminal o un navegador consulte `http://localhost:8080/api/devices`. Debe responder HTTP 200 con los tres dispositivos. La ruta `/` no es una página web del proyecto.

También acepta la variable de entorno `PORT`. Detenga el proceso con `Ctrl+C`. **Los datos se pierden al reiniciar**, por requisito del examen. Cada instancia mantiene sus propios datos.

Para otro computador: copie el JAR y ejecútelo con Java compatible, o copie el proyecto completo (incluidos `.mvn`, `mvnw` y `mvnw.cmd`) para compilarlo. No necesita copiar `target` ni `.m2` si comparte los fuentes.

## Dispositivos

| ID de la API | Nombre | Posición |
| --- | --- | --- |
| `star-labs` | STAR Labs | (-500, -200) |
| `ccpd` | CCPD | (100, -100) |
| `star-satellite` | S.T.A.R. Satellite | (500, 100) |

`GET /api/devices` consulta este catálogo. Los identificadores son exactos y distinguen mayúsculas de minúsculas.

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

Las distancias son finitas, no negativas y como máximo 1 000 000 000 unidades. Un fragmento tiene entre 1 y 1000 posiciones; cada posición contiene una palabra de hasta 100 caracteres sin espacios o `""`. No se aceptan elementos nulos.

## Reglas adoptadas donde el enunciado no precisa detalles

El PDF no define todos los detalles del contrato. Esta implementación adopta estas reglas explícitas, que pueden ajustarse si el profesor especifica otras:

1. Los arreglos tienen la misma longitud y **conservan la posición original de cada palabra**. `""` indica ausencia en esa posición, no se elimina antes de combinar los fragmentos. No se infieren desplazamientos entre arreglos de diferentes tamaños.
2. Dos palabras distintas en la misma posición son un conflicto; las comparaciones distinguen mayúsculas y tildes. No se adivinan palabras faltantes.
3. La trilateración usa distancias euclidianas en un plano. Obtiene una estimación lineal con diferencias de cuadrados factorizadas y la refina numéricamente cuando es necesario. Solo devuelve una posición si **cada una** de las tres distancias tiene un error absoluto de como máximo **0,01 unidades**. No devuelve resultados fuera de esa tolerancia aunque el refinamiento haya reducido el error.
4. El procesamiento completo es independiente del almacenamiento. Un error de procesamiento no borra ni cambia las lecturas.
5. El registro duplicado es un conflicto. PUT reemplaza únicamente registros existentes; PATCH conserva los campos omitidos y rechaza `null` explícito.
6. Los límites de tamaño y distancia son decisiones de implementación para evitar entradas desproporcionadas, no límites definidos por el PDF.

## Arquitectura y pruebas

Consulte [docs/architecture.md](docs/architecture.md) para responsabilidades, flujo y justificación del modelo.

`clean verify` ejecuta 39 pruebas: 20 de dominio, 3 de aplicación/concurrencia y 16 de HTTP real. Las pruebas de dominio también recorren 250 muestras deterministas de ubicaciones a distintas escalas. Se comprueban posiciones cercanas, distancia cero y distancias cercanas al límite de entrada; el caso `(100000000, 100000000)` reproduce el defecto de precisión corregido.

La tolerancia de 0,01 se aplica a las distancias, no garantiza esa misma precisión para las coordenadas. Cuando el laboratorio está muy lejos comparado con la separación entre dispositivos, pequeños errores de las distancias pueden producir mayor incertidumbre en las coordenadas. El refinamiento conserva el límite de aceptación y rechaza distancias incompatibles.

El flujo [.github/workflows/verify.yml](.github/workflows/verify.yml) ejecuta la misma verificación en Windows, Linux y macOS con JDK 21 y 25 después de cada push o pull request. La [ejecución del commit base `f22df88`](https://github.com/YustinBlakee/Operacion_alquimia_ExamenFinal-2026-2/actions/runs/44566284515) pasó las seis combinaciones. Las mejoras locales deben publicarse para que GitHub ejecute su propia verificación de esa nueva revisión.
