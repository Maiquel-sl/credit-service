package com.bank.credit.messaging;

import com.bank.credit.events.CreditTransactionEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import java.util.function.Supplier;

@Component
public class CreditEventPublisher {

    private final Sinks.Many<CreditTransactionEvent> sink = 
            Sinks.many().multicast().onBackpressureBuffer();

    public void publish(CreditTransactionEvent event) {
        sink.tryEmitNext(event);
    }

    @Bean
    public Supplier<Flux<CreditTransactionEvent>> creditEventProducer() {
        return sink::asFlux;
    }
}