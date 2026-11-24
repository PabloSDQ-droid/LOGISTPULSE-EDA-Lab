package edu.usfq.logistpulse.recommendation;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "logistpulse.events";
    public static final String QUEUE = "recommendation.stockout-risk.q";
    public static final String ROUTING_KEY = "inventory.stockout-risk.detected";

    public static final String DLX = "logistpulse.dlx";
    public static final String DLQ = "recommendation.stockout-risk.dlq";
    public static final String DLQ_ROUTING_KEY = "recommendation.stockout-risk.failed";

    @Bean
    TopicExchange logistpulseExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue stockoutRiskQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding stockoutRiskBinding(
            @Qualifier("stockoutRiskQueue") Queue queue,
            TopicExchange logistpulseExchange) {
        return BindingBuilder.bind(queue)
                .to(logistpulseExchange)
                .with(ROUTING_KEY);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue deadLetterQueue() {
        return new Queue(DLQ, true);
    }

    @Bean
    Binding deadLetterBinding(
            @Qualifier("deadLetterQueue") Queue queue,
            DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(queue)
                .to(deadLetterExchange)
                .with(DLQ_ROUTING_KEY);
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);

        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .maxAttempts(3)
                        .backOffPolicy(new org.springframework.retry.backoff.FixedBackOffPolicy())
                        .recoverer(new RejectAndDontRequeueRecoverer())
                        .build()
        );

        return factory;
    }
}