package com.bank.credit.repository;

import com.bank.credit.model.CreditCard;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreditCardRepository extends ReactiveMongoRepository<CreditCard, String> {
    Mono<CreditCard> findByCardNumber(String cardNumber);

    Flux<CreditCard> findByCustomerId(String customerId);
}
