package com.bank.credit.messaging;

import com.bank.credit.events.CreditCardCreatedEvent;
import com.bank.credit.events.CreditOverdueEvent;
import com.bank.credit.events.CreditTransactionEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import java.util.function.Supplier;

@Component
public class CreditEventPublisher {

    private final Sinks.Many<CreditTransactionEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
    private final Sinks.Many<CreditCardCreatedEvent> cardSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Sinks.Many<CreditOverdueEvent> overdueSink = Sinks.many().multicast().onBackpressureBuffer();

    public void publish(CreditTransactionEvent event) {
        sink.tryEmitNext(event);
    }

    public void publishCardCreated(CreditCardCreatedEvent event) {
        cardSink.tryEmitNext(event);
    }

    public void publishOverdueStatus(CreditOverdueEvent event) {
        overdueSink.tryEmitNext(event);
    }

    @Bean
    public Supplier<Flux<CreditTransactionEvent>> creditEventProducer() {
        return sink::asFlux;
    }

    @Bean
    public Supplier<Flux<CreditCardCreatedEvent>> creditCardCreatedProducer() {
        return cardSink::asFlux;
    }

    @Bean
    public Supplier<Flux<CreditOverdueEvent>> creditOverdueProducer() {
        return overdueSink::asFlux;
    }
}