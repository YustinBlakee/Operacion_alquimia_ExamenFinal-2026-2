# Verificación para el evaluador

## 1. Compilación y pruebas automáticas

Abra una terminal en la carpeta que contiene `pom.xml`. Necesita un JDK 17 como mínimo, compatible con Spring Boot 4.1, y conexión a Internet para la primera descarga de dependencias. La matriz del proyecto incluye JDK 17, 21 y 25. Maven viene incluido mediante Wrapper.

Si está ejecutando el JAR de esta carpeta, deténgalo con `Ctrl+C` antes de reconstruirlo: Windows bloquea el JAR en uso. Evite compilar simultáneamente desde varias terminales.

Windows PowerShell:

```powershell
java -version
javac -version
.\mvnw.cmd -B -ntp clean verify
if ($LASTEXITCODE -ne 0) {
    throw "La compilación falló. Corrija el error antes de iniciar el JAR."
}
```

Linux y macOS:

```sh
java -version
javac -version
sh ./mvnw -B -ntp clean verify
```

El resumen debe indicar:

```text
Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

No continúe con el arranque si aparece `BUILD FAILURE`. El comando compila con `--release 17`; el JAR generado puede ejecutarse en Java 17 o versiones posteriores compatibles con las dependencias, sin prometer compatibilidad con todas las versiones futuras.

Las pruebas de integración crean su propio servidor en un puerto aleatorio; no necesitan una instancia iniciada previamente. Los datos de las pruebas no se mezclan con los de una instancia externa.

Los informes XML y de texto se generan en `target/surefire-reports/`. El artefacto ejecutable se genera en `target/operacion-alquimia-1.0.0.jar`.

La suite completa de esta revisión pasó localmente **90 pruebas** en Windows con JDK 17.0.21, 21.0.13 y 25.0.5. Se ejecutó `clean verify` con Java 17 y se comprobó el JAR desde otra carpeta: Swagger, sus recursos locales y los ejemplos de las seis funcionalidades funcionaron con un proxy externo inutilizable. Los recursos de Swagger también se inspeccionaron dentro del JAR. El flujo de GitHub Actions comprobará nueve combinaciones de sistema/JDK al publicarla; las ejecuciones de commits anteriores no verifican estos cambios locales.

| Requisito | Verificación automática principal |
| --- | --- |
| Procesamiento completo | `ApiIntegrationTest.completeProcessingIsStatelessAndAcceptsDifferentOrder` |
| Registro individual | `ApiIntegrationTest.fullLifecycleIncludingAccumulatedProcessing` |
| Consulta y procesamiento acumulado | `ApiIntegrationTest.fullLifecycleIncludingAccumulatedProcessing` |
| Reemplazo completo | `ApiIntegrationTest.fullLifecycleIncludingAccumulatedProcessing` y `IntelligenceServiceTest.replacementPatchAndDeletionPreserveOtherDevice` |
| Actualización parcial | Las anteriores, más `IntelligenceServiceTest.concurrentPatchesToSeparateFieldsAreNotLost` |
| Eliminación individual | `ApiIntegrationTest.fullLifecycleIncludingAccumulatedProcessing` |
| Distancias grandes válidas | `IntelligenceProcessorTest.locatesAcrossTheSupportedRangeWithoutRejectingValidDistances` y `ApiIntegrationTest.largeValidDistancesWorkOverHttp` |
| Fragmentos de distinta longitud y vacíos | `MessageReconstructorTest` y las pruebas de contrato de `ApiIntegrationTest` |
| Datos inválidos y consistencia | `ApiIntegrationTest.processingErrorsDoNotMutateStoredData` y `rejectsMalformedInvalidUnknownAndExplicitNullWithoutChangingState` |
| Documentación HTTP y alias de dispositivos | Pruebas HTTP de `/v3/api-docs` y entrada de Swagger; `DeviceAliasesIntegrationTest` verifica las variantes de nombres en las operaciones |
| Independencia del núcleo | `ArchitectureTest`: restricciones de dependencias de Spring, Jackson, Swagger e infraestructura en dominio y aplicación e implementación de los puertos |

La suite comprueba valores JSON exactos, coordenadas dentro de su precisión numérica, cada distancia reconstruida, campos omitidos frente a nulos, información insuficiente, conflictos de palabras, fragmentos de distinta longitud o vacíos, dispositivos desconocidos, duplicados y conservación de otros dispositivos. También verifica que las antiguas restricciones de distancia, longitud de cadena y cantidad de posiciones no se apliquen como límites de negocio.

## 2. Iniciar el servidor para las pruebas manuales

En la misma carpeta, ejecute:

```sh
java -jar target/operacion-alquimia-1.0.0.jar
```

Espere `Tomcat started on port 8080` y `Started OperacionAlquimiaApplication`. Deje esa terminal abierta y use una segunda terminal para las peticiones. Los ejemplos siguientes usan el puerto 8080. Si está ocupado, arranque con `--server.port=8081` y sustituya el puerto en todas las URL.

Para trabajar desde el navegador, abra [Swagger UI](http://localhost:8080/swagger-ui.html). Redirige a `/swagger-ui/index.html` y carga la descripción de [`/v3/api-docs`](http://localhost:8080/v3/api-docs), que debe responder HTTP 200. Los recursos de la interfaz están incluidos en el JAR, por lo que se pueden usar sin conexión después de compilarlo.

Estas direcciones corresponden al arranque predeterminado sin prefijo de contexto adicional. Si define `server.servlet.context-path=/api`, todas reciben ese prefijo: Swagger queda en `/api/swagger-ui.html`, OpenAPI en `/api/v3/api-docs` y los endpoints del negocio en `/api/api/...`, porque los controladores ya declaran `/api`. No necesita esa configuración para seguir esta guía.

La tolerancia predeterminada es 0,01 unidades por distancia. Para verificar otra precisión, reinicie con `--alquimia.distance-tolerance=0.001` o configure la variable de entorno `DISTANCE_TOLERANCE` antes de arrancar. Debe ser un valor finito y mayor que cero; una configuración inválida impide el arranque. Las respuestas se validan contra el valor configurado, y las distancias redondeadas pueden dejar de ser compatibles si se exige más precisión.

**Ejecute la secuencia en orden, comenzando con un servidor recién iniciado y sin lecturas.** Al reiniciarlo se vacía la memoria. Los registros de una ejecución anterior pueden producir un 409 esperado al intentar crearlos de nuevo.

### Verificar las seis funcionalidades desde Swagger UI

En cada operación abra su fila, pulse **Try it out**, complete los parámetros y el cuerpo cuando corresponda y pulse **Execute**. Compruebe **Server response**: el estado y cuerpo de la respuesta real aparecen ahí. Utilice esta secuencia completa o la alternativa con curl que sigue; no ejecute ambas seguidas sin vaciar los registros o reiniciar, porque comparten el mismo estado del servidor.

**1. Información completa.** Seleccione `POST /api/intelligence` y envíe:

```json
{
  "devices": [
    {"device":"star-labs","distance":538.5164807134504,"message":["el","",""]},
    {"device":"ccpd","distance":141.4213562373095,"message":["","laboratorio",""]},
    {"device":"star-satellite","distance":509.9019513592785,"message":["","","oculto"]}
  ]
}
```

Espere 200, posición cercana a `(0,0)` y mensaje `el laboratorio oculto`. Ejecute `GET /api/readings`: debe devolver `[]`, porque esta operación no guarda las lecturas.

**2. Registro individual.** Seleccione `POST /api/readings/{device}` y ejecute una petición por fila, completando el parámetro `device` y el cuerpo:

| `device` | Cuerpo JSON |
| --- | --- |
| `star-satellite` | `{"distance":509.9019513592785,"message":["","","oculto"]}` |
| `star-labs` | `{"distance":538.5164807134504,"message":["el","",""]}` |
| `ccpd` | `{"distance":141.4213562373095,"message":["","laboratorio",""]}` |

Cada respuesta debe ser 201 y contener la lectura registrada. Las respuestas usan los IDs canónicos. También se aceptan los nombres del enunciado: se ignoran mayúsculas, espacios, guiones, guiones bajos y puntos al resolver la identidad. Por ejemplo, `STAR Labs` equivale a `star-labs` y `S.T.A.R. Satellite` a `star-satellite`. Un alias del mismo dispositivo no crea un registro adicional: un segundo POST devuelve 409.

**3. Consulta y procesamiento acumulado.** Ejecute `GET /api/readings`: deben aparecer tres lecturas. Ejecute `GET /api/intelligence`: espere 200, posición cercana a `(0,0)` y `el laboratorio oculto`.

**4. Reemplazo completo.** En `PUT /api/readings/{device}`, indique `star-labs` y envíe ambos campos:

```json
{"distance":538.52,"message":["un","",""]}
```

Espere 200 con esos valores. Consulte `GET /api/readings`: CCPD y el satélite deben conservar sus datos anteriores.

**5. Actualización parcial.** En `PATCH /api/readings/{device}`, indique `star-labs` y envíe primero:

```json
{"message":["este","",""]}
```

Espere 200 con distancia `538.52` conservada. En la misma operación envíe después:

```json
{"distance":538.5164807134504}
```

Espere 200 con mensaje `["este","",""]` conservado. Ejecute `GET /api/intelligence`: la posición debe ser cercana a `(0,0)` y el mensaje `este laboratorio oculto`.

**6. Eliminación.** En `DELETE /api/readings/{device}`, indique `ccpd`. Espere 204 sin cuerpo. `GET /api/readings` debe conservar solo STAR Labs y el satélite; `GET /api/readings/ccpd` debe responder 404 `NOT_FOUND`; `GET /api/intelligence` debe responder 422 `INSUFFICIENT_DATA`. Esos errores demuestran que el registro ya no está disponible y que faltan datos para procesar.

El servidor conserva los cambios hechos desde Swagger. Para repetir desde cero, reinícielo o elimine las lecturas restantes. Los cuerpos de referencia también están en [examples](../examples).

### Alternativa mediante curl

Los comandos siguientes son para PowerShell de Windows con `curl.exe`. En Linux o macOS use los mismos comandos sustituyendo `curl.exe` por `curl`. La opción `-i` muestra el estado HTTP y los encabezados. Todos los cuerpos se leen de archivos para evitar diferencias de escape de comillas entre shells.

Si su equipo no tiene curl, puede comprobar todos los casos ejecutando las pruebas automáticas del paso 1; curl solo se necesita para esta secuencia manual.

## 3. Catálogo y estado inicial

```powershell
curl.exe -i http://localhost:8080/api/devices
curl.exe -i http://localhost:8080/api/readings
```

Ambas responden 200. El catálogo contiene `star-labs: (-500,-200)`, `ccpd: (100,-100)` y `star-satellite: (500,100)`. Las lecturas inicialmente son `[]`.

## 4. Funcionalidad 1: procesamiento completo

```powershell
curl.exe -i -X POST http://localhost:8080/api/intelligence -H "Content-Type: application/json" --data-binary "@examples/complete-request.json"
curl.exe -i http://localhost:8080/api/readings
```

El POST responde 200 con posición cercana a `(0,0)` y mensaje `el laboratorio oculto`. El GET continúa devolviendo `[]`: procesar una petición completa no registra sus datos.

Compruebe también el ejemplo con fragmentos de distinta longitud:

```powershell
curl.exe -i -X POST http://localhost:8080/api/intelligence -H "Content-Type: application/json" --data-binary "@examples/unequal-request.json"
```

Debe responder 200 con el mismo mensaje y posición. Los índices conservan la posición original desde 0; las posiciones finales no enviadas cuentan como ausencias. Una cadena `""` dentro de un fragmento no desplaza las palabras siguientes.

## 5. Funcionalidad 2: registro individual, en cualquier orden

```powershell
curl.exe -i -X POST http://localhost:8080/api/readings/star-satellite -H "Content-Type: application/json" --data-binary "@examples/star-satellite.json"
curl.exe -i -X POST http://localhost:8080/api/readings/star-labs -H "Content-Type: application/json" --data-binary "@examples/star-labs.json"
curl.exe -i -X POST http://localhost:8080/api/readings/ccpd -H "Content-Type: application/json" --data-binary "@examples/ccpd.json"
```

Cada POST responde 201, devuelve la lectura registrada e incluye el encabezado `Location`. Se utiliza un orden distinto al del catálogo para comprobar que no depende del orden de llegada.

Un cuerpo con `"message": []` también es válido: registra que ese dispositivo no recibió palabras, manteniendo su distancia. El procesamiento posterior depende de que los otros dispositivos completen las posiciones necesarias. Un arreglo o un elemento `null` se rechaza; `[]` y `null` no son equivalentes.

## 6. Funcionalidad 3: consulta y procesamiento acumulado

```powershell
curl.exe -i http://localhost:8080/api/readings
curl.exe -i http://localhost:8080/api/readings/ccpd
curl.exe -i http://localhost:8080/api/intelligence
```

Todos responden 200. La colección contiene tres lecturas. La de CCPD tiene distancia `141.4213562373095` y mensaje `["","laboratorio",""]`. El procesamiento devuelve:

```json
{"position":{"x":0.0,"y":0.0},"message":"el laboratorio oculto"}
```

Pueden aparecer pequeños residuos de punto flotante en las coordenadas, en lugar de ceros exactos.

## 7. Funcionalidad 4: reemplazo completo

```powershell
curl.exe -i -X PUT http://localhost:8080/api/readings/star-labs -H "Content-Type: application/json" --data-binary "@examples/replace-star-labs.json"
curl.exe -i http://localhost:8080/api/readings
```

El PUT responde 200 con `distance: 538.52` y `message: ["un","",""]`. Sustituye los dos campos de STAR Labs. En el GET, CCPD y el satélite conservan exactamente sus valores anteriores.

## 8. Funcionalidad 5: actualización parcial

Cambie solo el mensaje:

```powershell
curl.exe -i -X PATCH http://localhost:8080/api/readings/star-labs -H "Content-Type: application/json" --data-binary "@examples/patch-message.json"
```

Responde 200 con distancia `538.52` conservada y mensaje `["este","",""]`.

Cambie solo la distancia, restaurando el valor exacto:

```powershell
curl.exe -i -X PATCH http://localhost:8080/api/readings/star-labs -H "Content-Type: application/json" --data-binary "@examples/patch-distance.json"
curl.exe -i http://localhost:8080/api/intelligence
```

El PATCH responde 200 con distancia `538.5164807134504` y conserva `["este","",""]`. El GET responde 200, posición cercana a `(0,0)` y mensaje `este laboratorio oculto`.

PATCH también admite `{"message":[]}` para sustituir únicamente el fragmento por uno vacío y conservar la distancia. No ejecute esa variante en esta secuencia si quiere mantener exactamente las respuestas de los pasos siguientes; su comportamiento está cubierto por las pruebas automáticas. Si ninguna de las lecturas aporta una palabra necesaria, el procesamiento responde 422 `INCOMPLETE_MESSAGE`.

## 9. Funcionalidad 6: eliminación individual

```powershell
curl.exe -i -X DELETE http://localhost:8080/api/readings/ccpd
curl.exe -i http://localhost:8080/api/readings
curl.exe -i http://localhost:8080/api/readings/ccpd
curl.exe -i http://localhost:8080/api/intelligence
```

Resultados esperados en orden:

1. HTTP 204 sin cuerpo.
2. HTTP 200 con solo STAR Labs y el satélite, sin cambios.
3. HTTP 404 con `code: NOT_FOUND`.
4. HTTP 422 con `code: INSUFFICIENT_DATA`: falta una lectura para calcular ambos resultados.

Los errores 404 y 422 de esta secuencia son resultados correctos, no fallos de la aplicación.

## 10. Validación y regresión numérica

Intente registrar una distancia negativa:

```powershell
curl.exe -i -X POST http://localhost:8080/api/readings/ccpd -H "Content-Type: application/json" --data-binary "@examples/invalid-distance.json"
curl.exe -i http://localhost:8080/api/readings/ccpd
```

Respuestas: 400 con `code: INVALID_DATA`, y luego 404 con `code: NOT_FOUND`. La lectura inválida no se guarda.

Compruebe el caso de distancias grandes que antes fallaba:

```powershell
curl.exe -i -X POST http://localhost:8080/api/intelligence -H "Content-Type: application/json" --data-binary "@examples/large-request.json"
```

Debe responder 200 con mensaje `el laboratorio oculto` y coordenadas cercanas a `(100000000,100000000)`. Con la configuración predeterminada, las pruebas verifican las tres distancias reconstruidas dentro de 0,01 unidades. A esa escala, las coordenadas pueden variar en sus decimales por la precisión de las distancias de entrada; la tolerancia de distancia no equivale a tolerancia de coordenadas. No existe un máximo de negocio de distancia, pero los cálculos usan `double` y pueden rechazar datos para los que no se consigue una posición finita y compatible.

Las cadenas se conservan literalmente, incluidos espacios, mayúsculas y tildes. No se adivinan palabras ni desplazamientos de los fragmentos: si sus índices no corresponden a las posiciones originales, hace falta corregir los datos de entrada. Si todos los fragmentos están vacíos o quedan huecos, se responde 422 `INCOMPLETE_MESSAGE`; si hay valores diferentes en una posición, se responde 422 `CONFLICTING_MESSAGE`.

## 11. Finalizar o repetir

Detenga el servidor con `Ctrl+C`. Al reiniciarlo, la memoria estará vacía y podrá repetir la secuencia. Para ejecutar en otro equipo basta con copiar el JAR construido y usar Java compatible; para verificar y recompilar copie el proyecto completo y siga el paso 1.

## 12. Estado de las verificaciones remotas

El [workflow Verify](https://github.com/YustinBlakee/Operacion_alquimia_ExamenFinal-2026-2/actions/workflows/verify.yml) comprueba nueve combinaciones: Windows, Linux y macOS con Java 17, 21 y 25. Abra la ejecución correspondiente al commit entregado y compruebe que las nueve tareas terminen con resultado satisfactorio. Una ejecución en cola o en progreso todavía no confirma el resultado, y las ejecuciones de otros commits no verifican una revisión diferente. Para la comprobación local utilice el resumen de `clean verify` y los informes generados en su equipo.
