import json
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

folder = Path(__file__).resolve().parents[1] / "evidencias" / "caso-4"
folder.mkdir(parents=True, exist_ok=True)

event = {
    "eventId": str(uuid4()),
    "occurredAt": datetime.now(timezone.utc).isoformat(),
    "sku": "CAF-DUPLICADO",
    "location": "QUITO-CENTRO",
    "available": 4,
    "minimum": 10,
}

publication = {
    "properties": {
        "content_type": "application/json",
        "delivery_mode": 2,
        "message_id": event["eventId"],
        "headers": {
            "__TypeId__": "edu.usfq.logistpulse.contract.StockoutRiskDetected"
        },
    },
    "routing_key": "inventory.stockout-risk.detected",
    "payload": json.dumps(event),
    "payload_encoding": "string",
}

for filename, content in [
    ("03-evento-duplicado.json", event),
    ("04-publicacion-rabbitmq.json", publication),
]:
    (folder / filename).write_text(
        json.dumps(content, indent=2) + "\n",
        encoding="utf-8",
    )

print(json.dumps(event, indent=2))