package edu.usfq.logistpulse.recommendation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationStore store;

    public RecommendationController(RecommendationStore store) {
        this.store = store;
    }

    @GetMapping
    public List<RecommendationStore.Recommendation> list(
            @RequestParam(name = "eventId", required = false) String eventId) {
        if (eventId == null) {
            return store.findAll();
        }

        RecommendationStore.Recommendation recommendation =
                store.findById(eventId);

        return recommendation == null
                ? List.of()
                : List.of(recommendation);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "recommendation-service"
        );
    }
}