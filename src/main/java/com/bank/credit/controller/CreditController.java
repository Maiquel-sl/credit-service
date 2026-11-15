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
    public Mono<Credit> createCredit(@RequestBody Credit credit) {
        return creditService.createCredit(credit);
    }

    @PostMapping("/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CreditCard> createCreditCard(@RequestBody CreditCard card) {
        return creditService.createCreditCard(card);
    }

    @GetMapping("/customer/{customerId}")
    public Flux<Credit> getCreditsByCustomer(@PathVariable String customerId) {
        return creditService.getCreditsByCustomerId(customerId);
    }

    @GetMapping("/cards/number/{cardNumber}")
    public Mono<ResponseEntity<CreditCard>> getCardByNumber(@PathVariable String cardNumber) {
        return creditService.getCreditCardByNumber(cardNumber)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/payment")
    public Mono<CreditTransaction> executeCreditPayment(@RequestBody Map<String, Object> payload) {
        String creditNumber = (String) payload.get("creditNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        String description = payload.get("description") != null ? payload.get("description").toString() : null;
        String payerCustomerId = payload.get("payerCustomerId") != null ? payload.get("payerCustomerId").toString()
                : null;

        return creditService.executeCreditPayment(creditNumber, amount, description, payerCustomerId);
    }

    @PostMapping("/cards/operation")
    public Mono<CreditTransaction> executeCardOperation(@RequestBody Map<String, Object> payload) {
        String cardNumber = (String) payload.get("cardNumber");
        String type = (String) payload.get("type");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        String description = payload.get("description") != null ? payload.get("description").toString() : null;
        String payerCustomerId = payload.get("payerCustomerId") != null ? payload.get("payerCustomerId").toString()
                : null;

        return creditService.executeCardOperation(cardNumber, type, amount, description, payerCustomerId);
    }

    @PatchMapping("/{creditNumber}/overdue")
    public Mono<ResponseEntity<Credit>> reportCreditOverdue(@PathVariable String creditNumber) {
        return creditService.reportCreditOverdue(creditNumber)
                .map(ResponseEntity::ok);
    }
}
