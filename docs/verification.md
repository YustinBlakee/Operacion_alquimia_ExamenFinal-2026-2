# Verificación para el evaluador

## 1. Compilación y pruebas automáticas

Abra una terminal en la carpeta que contiene `pom.xml`. Necesita JDK 21 o 25 y conexión a Internet para la primera descarga de dependencias. Maven viene incluido mediante Wrapper.

Si está ejecutando el JAR de esta carpeta, deténgalo con `Ctrl+C` antes de reconstruirlo: Windows bloquea el JAR en uso. Evite compilar simultáneamente desde varias terminales.

Windows PowerShell:

```powershell
java -version
javac -version
.\mvnw.cmd -B -ntp clean verify
```

Linux y macOS:

```sh
java -version
javac -version
sh ./mvnw -B -ntp clean verify
```

Resultado esperado:

```text
Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Las pruebas de integración crean su propio servidor en un puerto aleatorio; no necesitan una instancia iniciada previamente. Los datos de las pruebas no se mezclan con los de una instancia externa.

Los informes XML y de texto se generan en `target/surefire-reports/`. El artefacto ejecutable se genera en `target/operacion-alquimia-1.0.0.jar`.

| Requisito | Verificación automática principal |
| --- | --- |
| Procesamiento completo | `ApiIntegrationTest.completeProcessingIsStatelessAndAcceptsDifferentOrder` |
| Registro individual | `ApiIntegrationTest.fullLifecycleIncludingAccumulatedProcessing` |
| Consulta y procesamiento acumulado | `ApiIntegrationTest.fullLifecycleIncludingAccumulatedProcessing` |
| Reemplazo completo | `ApiIntegrationTest.fullLifecycleIncludingAccumulatedProcessing` y `IntelligenceServiceTest.replacementPatchAndDeletionPreserveOtherDevice` |
| Actualización parcial | Las anteriores, más `IntelligenceServiceTest.concurrentPatchesToSeparateFieldsAreNotLost` |
| Eliminación individual | `ApiIntegrationTest.fullLifecycleIncludingAccumulatedProcessing` |
| Distancias grandes válidas | `IntelligenceProcessorTest.locatesAcrossTheSupportedRangeWithoutRejectingValidDistances` y `ApiIntegrationTest.largeValidDistancesWorkOverHttp` |
| Datos inválidos y consistencia | `ApiIntegrationTest.processingErrorsDoNotMutateStoredData` y `rejectsMalformedInvalidUnknownAndExplicitNullWithoutChangingState` |

La suite comprueba valores JSON exactos, coordenadas dentro de su precisión numérica, cada distancia reconstruida, campos omitidos frente a nulos, información insuficiente, conflictos de palabras, dispositivos desconocidos, duplicados, límites de entrada y conservación de otros dispositivos.

## 2. Iniciar el servidor para las pruebas manuales

En la misma carpeta, ejecute:

```sh
java -jar target/operacion-alquimia-1.0.0.jar
```

Espere `Tomcat started on port 8080` y `Started OperacionAlquimiaApplication`. Deje esa terminal abierta y use una segunda terminal para las peticiones. Los ejemplos siguientes usan el puerto 8080. Si está ocupado, arranque con `--server.port=8081` y sustituya el puerto en todas las URL.

**Ejecute la secuencia en orden, comenzando con un servidor recién iniciado y sin lecturas.** Al reiniciarlo se vacía la memoria. Los registros de una ejecución anterior pueden producir un 409 esperado al intentar crearlos de nuevo.

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

## 5. Funcionalidad 2: registro individual, en cualquier orden

```powershell
curl.exe -i -X POST http://localhost:8080/api/readings/star-satellite -H "Content-Type: application/json" --data-binary "@examples/star-satellite.json"
curl.exe -i -X POST http://localhost:8080/api/readings/star-labs -H "Content-Type: application/json" --data-binary "@examples/star-labs.json"
curl.exe -i -X POST http://localhost:8080/api/readings/ccpd -H "Content-Type: application/json" --data-binary "@examples/ccpd.json"
```

Cada POST responde 201, devuelve la lectura registrada e incluye el encabezado `Location`. Se utiliza un orden distinto al del catálogo para comprobar que no depende del orden de llegada.

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

Debe responder 200 con mensaje `el laboratorio oculto` y coordenadas cercanas a `(100000000,100000000)`. Las pruebas automáticas verifican las tres distancias reconstruidas dentro de 0,01 unidades. A esa escala, las coordenadas pueden variar en sus decimales por la precisión de las distancias de entrada; la tolerancia de distancia no equivale a tolerancia de coordenadas.

## 11. Finalizar o repetir

Detenga el servidor con `Ctrl+C`. Al reiniciarlo, la memoria estará vacía y podrá repetir la secuencia. Para ejecutar en otro equipo basta con copiar el JAR construido y usar Java compatible; para verificar y recompilar copie el proyecto completo y siga el paso 1.
