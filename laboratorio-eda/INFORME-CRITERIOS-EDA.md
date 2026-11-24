# Laboratorio: evaluar criterios EDA con RabbitMQ

**Estudiante:** Pablo Galarza  
**Asignatura:** Desarrollo de Software Moderno — USFQ  
**Fecha:** 24 de noviembre de 2026  
**Rama de trabajo:** `feat/criterios-eda`

## Objetivo y alcance

Comprobar mediante cuatro pruebas la publicación condicional de eventos, la comunicación asíncrona, el desacoplamiento temporal y el consumo posterior en LOGISTPULSE.

Se reutilizó la implementación existente en `laboratorio-eda/`. El productor recibe lecturas mediante `POST http://localhost:8080/api/inventory/stock`; RabbitMQ comunica inventario con recomendaciones. Los resultados de esta ejecución están en [evidencias/criterios-eda/](evidencias/criterios-eda/).

Se usaron el producto `CAF-500`, la ubicación `QUITO-CENTRO` y un mínimo de 10 unidades. Las lecturas con riesgo tuvieron 4 unidades y la lectura sin riesgo tuvo 15. El código evalúa la condición `available <= minimum`.

## Hoja de resultados

| Prueba | Evidencia observada | Criterio EDA evaluado | ¿Cumple? |
|---|---|---|---|
| Stock bajo | HTTP 200, `riskDetected: true`, un `eventId` y una línea `RECOMENDACION_PENDIENTE` con ese mismo identificador. | Publicación y consumo del evento. | Sí. |
| Stock suficiente | HTTP 200, `riskDetected: false`, `eventId: null` y ningún log nuevo del consumidor durante la observación. | Regla de publicación condicional. | Sí. |
| Consumidor detenido | Ambas solicitudes devolvieron HTTP 200 y `riskDetected: true`. La cola de recomendaciones pasó de 0 a 2 mensajes Ready, con 0 sin confirmar en ambas consultas. | Desacoplamiento y retención temporal. | Sí. |
| Consumidor reanudado | Los dos identificadores publicados aparecieron en `RECOMENDACION_PENDIENTE`; después, la cola de recomendaciones mostró 0 Ready y 0 sin confirmar. | Consumo posterior. | Sí. |

La comunicación asíncrona se sustenta especialmente en las respuestas HTTP obtenidas con el consumidor detenido: el productor pudo responder antes de que se generaran las recomendaciones. Las respuestas, los identificadores y los contadores de las colas proporcionaron observabilidad del recorrido.

### Evidencias de cada actividad

| Actividad | Archivos |
|---|---|
| Estado inicial | [Servicios activos](evidencias/criterios-eda/00-servicios.txt). RabbitMQ figuró como `healthy`. |
| 1. Stock bajo | [Respuesta HTTP](evidencias/criterios-eda/01-stock-bajo-http.txt) y [logs del consumidor](evidencias/criterios-eda/01-stock-bajo-consumidor.txt). |
| 2. Stock suficiente | [Respuesta HTTP](evidencias/criterios-eda/02-stock-suficiente-http.txt) y [logs desde el inicio de esta actividad](evidencias/criterios-eda/02-stock-suficiente-consumidor.txt). Este último archivo quedó vacío porque no hubo nuevas líneas en la ventana observada. |
| 3. Consumidor detenido | [Colas antes](evidencias/criterios-eda/03-colas-antes.txt), [primera respuesta HTTP](evidencias/criterios-eda/03-riesgo-1-http.txt), [segunda respuesta HTTP](evidencias/criterios-eda/03-riesgo-2-http.txt) y [colas después de publicar](evidencias/criterios-eda/03-colas-despues.txt). |
| 4. Consumidor reanudado | [Logs del consumidor](evidencias/criterios-eda/04-consumidor-reanudado.txt) y [estado final de las colas](evidencias/criterios-eda/04-colas.txt). |

### Correspondencia de eventos

Los horarios están en UTC. La hora de respuesta procede del encabezado HTTP `Date`; la hora de procesamiento procede del log del consumidor.

| Publicación | `eventId` | Respuesta HTTP | Recomendación observada |
|---|---|---|---|
| Actividad 1, stock bajo | `14c05f07-daa0-43d9-8d67-2e1d03cd6dd7` | 22:08:39, HTTP 200 | 22:08:39.483, con el consumidor activo. |
| Actividad 3, primera solicitud | `d59ebb19-4a11-4b78-a3c0-2538953bbb5c` | 22:14:04, HTTP 200 | 22:15:22.388, después de reanudar el consumidor. |
| Actividad 3, segunda solicitud | `7603d88d-c4b1-40ca-be9d-659a3fcf9512` | 22:14:12, HTTP 200 | 22:15:22.396, después de reanudar el consumidor. |

El archivo de logs de la actividad 4 también conserva la línea histórica de la actividad 1. Su horario permite distinguirla de las dos recomendaciones generadas tras la reanudación.

### Evolución de las colas

Cada celda indica `Ready / sin confirmar`.

| Cola | Antes de publicar con el consumidor detenido | Después de las dos publicaciones | Después de reanudar |
|---|---|---|---|
| `recommendation.stockout-risk.q` | 0 / 0 | 2 / 0 | 0 / 0 |
| `audit.stockout-risk.q` | 0 / 0 | 0 / 0 | 0 / 0 |
| `recommendation.stockout-risk.dlq` | 1 / 0 | 1 / 0 | 1 / 0 |

El mensaje de la DLQ ya existía al comenzar esta prueba y su cantidad permaneció igual. Esta ejecución no demuestra un nuevo envío a la DLQ. Auditoría permaneció activa y utiliza su propia cola.

## Preguntas de análisis

### 1. ¿Qué evidencia muestra que inventory-service no llama directamente a recommendation-service?

En el flujo de `POST /api/inventory/stock`, [StockController.java](inventory-service/src/main/java/edu/usfq/logistpulse/inventory/StockController.java) utiliza `RabbitTemplate.convertAndSend` para publicar el evento en un exchange. Este método del controlador no invoca la API de recomendaciones.

Además, al detener recomendaciones, inventario siguió devolviendo HTTP 200 y los eventos se acumularon en RabbitMQ. El procesamiento de este flujo pudo continuar posteriormente, cuando el consumidor estuvo disponible.

### 2. ¿Qué pudo hacer el productor mientras el consumidor estaba detenido?

Pudo recibir las dos lecturas de inventario, evaluar la condición de riesgo, generar un identificador para cada evento, publicarlos mediante RabbitMQ y responder las solicitudes HTTP. RabbitMQ conservó ambos eventos hasta la reanudación del consumidor.

El productor mantuvo su dependencia de RabbitMQ; la prueba demuestra independencia temporal respecto del consumidor.

### 3. ¿Qué papel cumplen el exchange, la routing key y la cola?

| Elemento | Valor utilizado | Papel |
|---|---|---|
| Exchange de tipo topic | `logistpulse.events` | Recibe las publicaciones y las dirige mediante sus bindings. |
| Routing key | `inventory.stockout-risk.detected` | Identifica el tipo de evento y coincide con el binding de la cola de recomendaciones. |
| Cola | `recommendation.stockout-risk.q` | Retiene los eventos destinados a recomendaciones y los entrega al consumidor disponible. |

El [RabbitConfig del consumidor](recommendation-service/src/main/java/edu/usfq/logistpulse/recommendation/RabbitConfig.java) declara la cola como durable y establece su binding con ese exchange y esa routing key. El [RabbitConfig del productor](inventory-service/src/main/java/edu/usfq/logistpulse/inventory/RabbitConfig.java) declara el exchange utilizado al publicar.

El consumo de los mismos identificadores publicados es consistente con este enrutamiento. Estas cuatro actividades no prueban rutas alternativas ni routing keys incorrectas.

### 4. Si la cola conserva mensajes, ¿significa que el procesamiento está garantizado para siempre? ¿Qué límites tendría esta prueba?

No. Se comprobó retención durante la detención del consumidor, mientras RabbitMQ permaneció activo. No se probaron una caída del broker, pérdida de almacenamiento, fallos de red o fallos durante el procesamiento.

Una cola durable y mensajes persistentes ayudan a recuperarse ante reinicios. La fiabilidad también requiere confirmaciones del broker al productor y confirmaciones del consumidor después de completar el trabajo. La prueba realizada no acredita procesamiento exactamente una vez ni conservación indefinida. Estas diferencias están descritas en la [documentación de colas](https://www.rabbitmq.com/docs/queues#durability) y la [guía de fiabilidad de RabbitMQ](https://www.rabbitmq.com/docs/reliability).

### 5. ¿Qué pasaría si el consumidor recibiera el mismo evento dos veces? ¿Qué debería hacer una versión real para evitar duplicar recomendaciones?

Un consumidor que aplica sus efectos en cada entrega podría generar dos recomendaciones. En la implementación reutilizada, [RecommendationStore.java](recommendation-service/src/main/java/edu/usfq/logistpulse/recommendation/RecommendationStore.java) utiliza `eventId` como clave y `putIfAbsent` sobre un `ConcurrentHashMap`. [StockoutRiskListener.java](recommendation-service/src/main/java/edu/usfq/logistpulse/recommendation/StockoutRiskListener.java) registra `EVENTO_DUPLICADO_IGNORADO` cuando el identificador ya existe.

Esa protección corresponde a una instancia en ejecución: el registro se pierde al reiniciar y no se comparte entre instancias. Una versión real debería guardar el identificador de forma durable, con una restricción única, y registrar el procesamiento junto con la recomendación en una transacción. Debe confirmar la entrega después de completar ese trabajo.

RabbitMQ contempla entregas repetidas, por lo que el consumidor debe ser idempotente ante entregas repetidas; véase su [guía de fiabilidad](https://www.rabbitmq.com/docs/reliability#data-safety-on-the-consumer-side). Esta ejecución no incluyó una prueba de duplicados.

## Conclusión

Las cuatro actividades demostraron publicación condicional, comunicación asíncrona, desacoplamiento temporal y consumo posterior. Inventario respondió con el consumidor detenido, RabbitMQ retuvo dos eventos y recomendaciones procesó sus mismos identificadores al reanudarse. Los logs y los contadores de las colas permitieron verificar lo ocurrido.

La cola está declarada durable en el código, pero el ensayo sólo comprobó su retención con el broker activo. Los resultados no permiten afirmar tolerancia completa a fallos ni procesamiento exactamente una vez.

La base reutilizada ya contiene reintentos, una DLQ e idempotencia en memoria. Estas cuatro pruebas no validaron esos mecanismos ante fallos. Para ampliar las garantías habría que ejecutar pruebas específicas de fallos y redelivery, verificar la recuperación del broker y usar almacenamiento durable para las recomendaciones y el control de duplicados.
