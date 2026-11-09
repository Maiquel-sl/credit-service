package com.bank.credit.service;

import com.bank.credit.model.Credit;
import com.bank.credit.model.CreditCard;
import com.bank.credit.model.CreditTransaction;
import java.math.BigDecimal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreditService {
    Mono<Credit> createCredit(Credit credit);
    Mono<CreditCard> createCreditCard(CreditCard card);
    Flux<Credit> getCreditsByCustomerId(String customerId);
    Mono<CreditCard> getCreditCardByNumber(String cardNumber);
    Mono<CreditTransaction> executeCreditPayment(String creditNumber, BigDecimal amount, String description);
    Mono<CreditTransaction> executeCardOperation(String cardNumber, String type, BigDecimal amount, String description);
}
