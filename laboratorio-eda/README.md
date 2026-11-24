# Laboratorio avanzado de resiliencia EDA

Implementación de los cuatro casos con Spring Boot y RabbitMQ.

- [Guía general y arquitectura](../README.md)
- [Informe de resultados, respuestas y conclusión](INFORME.md)
- [Evidencias de ejecución](evidencias/)

## Ejecutar desde esta carpeta

```bash
docker compose up --build -d
docker compose ps
```

Espera a que las aplicaciones terminen de arrancar. Puedes observarlas con:

```bash
docker compose logs -f inventory-service recommendation-service audit-service
```

Sal de los logs con `Ctrl + C`.

## Repetir la prueba de idempotencia

Los siguientes comandos generan un evento nuevo y publican dos veces el mismo contenido.

### 1. Preparar el evento

```bash
python3 scripts/preparar_duplicado.py

CASE4_EVENT_ID=$(python3 -c 'import json; print(json.load(open("evidencias/caso-4/03-evento-duplicado.json"))["eventId"])')
```

### 2. Publicarlo dos veces

```bash
for i in 1 2; do
  curl -fsS -u lab:lab123 \
    -X POST "http://localhost:15672/api/exchanges/%2F/logistpulse.events/publish" \
    -H 'Content-Type: application/json' \
    --data-binary @evidencias/caso-4/04-publicacion-rabbitmq.json \
    -w '\n'
done | tee evidencias/caso-4/05-dos-publicaciones.jsonl
```

Ambas respuestas deben indicar `routed: true`.

### 3. Observar el procesamiento

```bash
docker compose logs --no-color -f recommendation-service \
  | grep --line-buffered "eventId=$CASE4_EVENT_ID " \
  | tee evidencias/caso-4/06-consumidor.txt
```

Espera dos líneas `EVENTO_RECIBIDO`, una `RECOMENDACION_PENDIENTE` y una `EVENTO_DUPLICADO_IGNORADO`. Después sal con `Ctrl + C`.

### 4. Consultar el registro

En la misma terminal:

```bash
curl -fsS -w '\n' "http://localhost:8081/api/recommendations?eventId=$CASE4_EVENT_ID" \
  | tee evidencias/caso-4/07-recomendacion.json
```

Debe devolver una lista con una sola recomendación para ese identificador.

## Alcance

El registro de recomendaciones y la detección de duplicados están en memoria y corresponden a una instancia del servicio. Se pierden cuando la aplicación se reinicia.

Las evidencias de los otros tres casos y su interpretación están enlazadas en el informe.