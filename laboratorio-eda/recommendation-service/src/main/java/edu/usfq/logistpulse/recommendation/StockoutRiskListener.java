package edu.usfq.logistpulse.recommendation;

import edu.usfq.logistpulse.contract.StockoutRiskDetected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;

@Component
public class StockoutRiskListener {
    private static final Logger log =
            LoggerFactory.getLogger(StockoutRiskListener.class);

    private final RecommendationStore store;

    public StockoutRiskListener(RecommendationStore store) {
        this.store = store;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void handle(StockoutRiskDetected event) {
        RetryContext context = RetrySynchronizationManager.getContext();
        int attempt = context == null ? 1 : context.getRetryCount() + 1;

        if (attempt == 1) {
            log.info(
                    "EVENTO_RECIBIDO eventId={} sku={}",
                    event.eventId(), event.sku()
            );
        }

        log.info(
                "INTENTO_PROCESAMIENTO eventId={} sku={} intento={}/3",
                event.eventId(), event.sku(), attempt
        );

        if ("FAIL-TEST".equals(event.sku())) {
            throw new IllegalStateException(
                    "Fallo controlado para FAIL-TEST"
            );
        }

        if (!store.saveIfAbsent(event)) {
            log.info(
                    "EVENTO_DUPLICADO_IGNORADO eventId={} sku={}",
                    event.eventId(), event.sku()
            );
            return;
        }

        log.info(
                "RECOMENDACION_PENDIENTE eventId={} sku={} location={} available={} minimum={} action=Revisar compra o transferencia",
                event.eventId(), event.sku(), event.location(),
                event.available(), event.minimum()
        );
    }
}