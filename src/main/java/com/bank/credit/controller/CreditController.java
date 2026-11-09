package com.bank.credit.controller;

import com.bank.credit.model.*;
import com.bank.credit.service.CreditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/credits")
public class CreditController {

    private final CreditService creditService;

    public CreditController(CreditService creditService) {
        this.creditService = creditService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Credit> createCredit(@RequestBody Credit credit) { return creditService.createCredit(credit); }

    @PostMapping("/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CreditCard> createCreditCard(@RequestBody CreditCard card) { return creditService.createCreditCard(card); }

    @GetMapping("/customer/{customerId}")
    public Flux<Credit> getCreditsByCustomer(@PathVariable String customerId) { return creditService.getCreditsByCustomerId(customerId); }

    @GetMapping("/cards/number/{cardNumber}")
    public Mono<ResponseEntity<CreditCard>> getCardByNumber(@PathVariable String cardNumber) {
        return creditService.getCreditCardByNumber(cardNumber)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/payment")
    public Mono<ResponseEntity<CreditTransaction>> executeCreditPayment(@RequestBody Map<String, Object> payload) {
        String creditNumber = (String) payload.get("creditNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        String description = (String) payload.get("description");

        return creditService.executeCreditPayment(creditNumber, amount, description)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/cards/operation")
    public Mono<ResponseEntity<CreditTransaction>> executeCardOperation(@RequestBody Map<String, Object> payload) {
        String cardNumber = (String) payload.get("cardNumber");
        String type = (String) payload.get("type"); // CARD_CONSUMPTION o CARD_PAYMENT
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        String description = (String) payload.get("description");

        return creditService.executeCardOperation(cardNumber, type, amount, description)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class, ex -> Mono.just(ResponseEntity.badRequest().build()));
    }
}
