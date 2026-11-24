package edu.usfq.logistpulse.recommendation;

import edu.usfq.logistpulse.contract.StockoutRiskDetected;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RecommendationStore {
    private final ConcurrentMap<String, Recommendation> recommendations =
            new ConcurrentHashMap<>();

    public boolean saveIfAbsent(StockoutRiskDetected event) {
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("eventId es obligatorio");
        }

        Recommendation recommendation = new Recommendation(
                event.eventId(),
                event.sku(),
                event.location(),
                event.available(),
                event.minimum(),
                "Revisar compra o transferencia",
                Instant.now().toString()
        );

        return recommendations.putIfAbsent(
                event.eventId(), recommendation
        ) == null;
    }

    public List<Recommendation> findAll() {
        return List.copyOf(recommendations.values());
    }

    public Recommendation findById(String eventId) {
        return recommendations.get(eventId);
    }

    public record Recommendation(
            String eventId,
            String sku,
            String location,
            int available,
            int minimum,
            String action,
            String createdAt
    ) {}
}