package com.bank.credit.controller;

import com.bank.credit.model.Credit;
import com.bank.credit.model.CreditCard;
import com.bank.credit.service.CreditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@WebFluxTest(CreditController.class)
class CreditControllerTest {

    @Autowired 
    private WebTestClient webTestClient;

    @MockitoBean 
    private CreditService creditService;

    @Test
    @DisplayName("GET /api/credits/cards/number/{cardNumber} - Should return credit card payload with HTTP 200")
    void testGetCardByNumberFound() {
        CreditCard card = new CreditCard();
        card.setCardNumber("4557000000000000");
        card.setCreditLimit(new BigDecimal("1500.00"));

        Mockito.when(creditService.getCreditCardByNumber("4557000000000000")).thenReturn(Mono.just(card));

        webTestClient.get()
                .uri("/api/credits/cards/number/4557000000000000")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.cardNumber").isEqualTo("4557000000000000")
                .jsonPath("$.creditLimit").isEqualTo(1500.00);
    }

    @Test
    @DisplayName("GET /api/credits/customer/{customerId} - Should return list of active credits")
    void testGetCreditsByCustomer() {
        Credit credit = new Credit();
        credit.setCustomerId("cust-555");

        Mockito.when(creditService.getCreditsByCustomerId("cust-555")).thenReturn(Flux.just(credit));

        webTestClient.get()
                .uri("/api/credits/customer/cust-555")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Credit.class)
                .hasSize(1);
    }
}
