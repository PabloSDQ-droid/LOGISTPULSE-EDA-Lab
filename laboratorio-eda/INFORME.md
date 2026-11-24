# Informe: resiliencia y evolución EDA en LOGISTPULSE

**Autor:** Pablo Galarza  
**Curso:** Desarrollo de Software Moderno  
**Fecha:** 24 de noviembre de 2026

## Resultados

Las pruebas se realizaron por etapas, ampliando la base del profesor para los casos 2, 3 y 4.

| Caso | Criterio EDA | Evidencia recolectada | Resultado |
|---|---|---|---|
| Consumidor detenido | Desacoplamiento temporal y recuperación | [Publicaciones](evidencias/caso-1/02-eventos-publicados.jsonl), [cola acumulada](evidencias/caso-1/03-cola-acumulada.txt), [procesamiento](evidencias/caso-1/04-recuperacion.txt) y [cola recuperada](evidencias/caso-1/05-cola-recuperada.txt) | Diez eventos publicados y acumulados. Al reanudar el consumidor se registraron diez recomendaciones para CAF-1 a CAF-10 y quedaron cero mensajes pendientes. |
| Dos consumidores | Publicación/suscripción y procesamiento independiente | [Colas independientes](evidencias/caso-2/01-colas.txt) y [logs de ambos servicios](evidencias/caso-2/03-ambos-consumidores.txt) | Un mismo eventId apareció en recomendaciones y auditoría, cada servicio mediante su propia cola. |
| Error controlado | Reintentos limitados y aislamiento de fallos | [Tres intentos](evidencias/caso-3/04-intentos.txt), [mensaje en DLQ](evidencias/caso-3/05-colas-despues-fallo.txt) y [evento válido procesado](evidencias/caso-3/07-evento-valido-procesado.txt) | FAIL-TEST se intentó procesar tres veces, con pausas de aproximadamente un segundo, y terminó en la DLQ. Después se procesó CAF-OK correctamente. |
| Evento repetido | Idempotencia en una instancia del consumidor | [Dos publicaciones](evidencias/caso-4/05-dos-publicaciones.jsonl), [logs de consumo](evidencias/caso-4/06-consumidor.txt) y [registro consultado](evidencias/caso-4/07-recomendacion.json) | Dos entregas con el mismo eventId produjeron una recomendación efectiva y un duplicado ignorado. |

## Respuestas

### Caso 1. ¿Qué observaste en la API mientras el consumidor estaba detenido?

La API continuó aceptando solicitudes y publicando eventos de riesgo. Las diez respuestas incluyeron `riskDetected: true` y un `eventId`.

Los eventos quedaron esperando en RabbitMQ. Al reanudar recomendaciones, el consumidor los procesó y la cola pasó de diez mensajes pendientes a cero. La publicación y el procesamiento estuvieron desacoplados temporalmente.

### Caso 2. ¿Qué pasaría con auditoría si recomendaciones estuviera detenido?

Con la configuración implementada, auditoría continuaría procesando desde su propia cola. Los mensajes destinados a recomendaciones quedarían pendientes en la cola de ese servicio.

Ambos consumidores reciben los eventos desde el exchange `logistpulse.events`, mediante colas enlazadas con la routing key `inventory.stockout-risk.detected`.

### Caso 3. ¿Por qué conviene limitar los reintentos?

Un error permanente podría consumir recursos indefinidamente y retrasar otros mensajes. Limitar los intentos permite recuperar el procesamiento ante fallos temporales y apartar los eventos que siguen fallando.

En la prueba, después del tercer intento, el mensaje se rechazó y RabbitMQ lo dirigió a `recommendation.stockout-risk.dlq`. La recomendación posterior para CAF-OK demostró que el fallo quedó aislado.

### Caso 4. ¿Por qué no debemos asumir una entrega exactamente una vez?

El consumidor puede procesar un mensaje y perder la conexión antes de confirmarlo. En ese caso, RabbitMQ puede volver a entregarlo. También pueden producirse publicaciones repetidas por parte del productor.

Por eso se utilizó el `eventId` como clave del registro de recomendaciones. `ConcurrentHashMap.putIfAbsent` realiza el registro de forma atómica: únicamente la primera inserción genera una recomendación efectiva.

La limitación es que este registro vive en la memoria de una instancia. Al reiniciar la aplicación se pierde, y varias instancias tampoco compartirían sus identificadores procesados. Para conservar la protección sería necesario un almacenamiento persistente compartido.

## Cambios realizados

- **Caso 1:** se utilizó la base del profesor sin cambios funcionales.
- **Caso 2:** se añadió `audit-service`, su cola propia y su configuración en Docker Compose.
- **Caso 3:** se incorporaron el fallo controlado, Spring Retry, tres intentos, espera fija y el enrutamiento hacia la DLQ.
- **Caso 4:** se añadió el registro de recomendaciones en memoria, la detección de duplicados y una API HTTP para consultar los resultados.

## Conclusión

El laboratorio comprobó desacoplamiento temporal, publicación/suscripción, procesamiento independiente, reintentos limitados, aislamiento de mensajes problemáticos e idempotencia dentro de una instancia del consumidor.

La recuperación validada consistió en detener y reanudar el consumidor. La idempotencia se verificó durante la ejecución de una misma instancia, utilizando almacenamiento en memoria.

Antes de usar este diseño en un entorno real agregaríamos:

- Persistencia compartida con `eventId` único y transacciones para guardar las recomendaciones.
- Almacenamiento persistente del broker y pruebas de recuperación.
- Confirmaciones de publicación y, al incorporar cambios de inventario en una base de datos, un patrón Transactional Outbox.
- Monitorización de colas, alertas de DLQ y un procedimiento controlado de revisión y reprocesamiento.
- Gestión de credenciales, TLS, permisos mínimos y validación y versionado de los contratos de eventos.

## Base utilizada

El código inicial del laboratorio procede de [randUSFQ/LABEDA](https://github.com/randUSFQ/LABEDA), archivo `rabbitmq-logistpulse-lab (4).zip`.

La implementación y las evidencias de esta entrega se encuentran en la carpeta `laboratorio-eda`.