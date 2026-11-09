package com.bank.credit.repository;

import com.bank.credit.model.Credit;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreditRepository extends ReactiveMongoRepository<Credit, String> {
    Mono<Credit> findByCreditNumber(String creditNumber);
    Flux<Credit> findByCustomerId(String customerId);
}
