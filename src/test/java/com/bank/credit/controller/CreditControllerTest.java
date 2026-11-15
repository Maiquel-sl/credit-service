package com.bank.credit.controller;

import com.bank.credit.exception.GlobalExceptionHandler;
import com.bank.credit.model.Credit;
import com.bank.credit.model.CreditCard;
import com.bank.credit.model.CreditTransaction;
import com.bank.credit.service.CreditService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@WebFluxTest(CreditController.class)
@Import(GlobalExceptionHandler.class)
class CreditControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CreditService creditService;

    private Credit mockCredit;
    private CreditCard mockCard;
    private CreditTransaction mockTransaction;

    @BeforeEach
    void setUp() {
        mockCredit = new Credit();
        mockCredit.setId("crd-id-001");
        mockCredit.setCreditNumber("CRD-11223344");
        mockCredit.setCustomerId("cust-vip-01");
        mockCredit.setCustomerType("PERSONAL");
        mockCredit.setType("PERSONAL_CREDIT");
        mockCredit.setAmountGranted(new BigDecimal("5000.00"));
        mockCredit.setBalanceOwed(new BigDecimal("5000.00"));
        mockCredit.setCurrency("PEN");
        mockCredit.setStatus("ACTIVE");

        mockCard = new CreditCard();
        mockCard.setId("cc-id-001");
        mockCard.setCardNumber("4557111122223333");
        mockCard.setCustomerId("cust-vip-01");
        mockCard.setCustomerType("PERSONAL");
        mockCard.setCreditLimit(new BigDecimal("8000.00"));
        mockCard.setAvailableBalance(new BigDecimal("8000.00"));
        mockCard.setBalanceOwed(BigDecimal.ZERO);
        mockCard.setCurrency("PEN");
        mockCard.setStatus("ACTIVE");
        mockCard.setExpirationDate("11/31");

        mockTransaction = new CreditTransaction();
        mockTransaction.setId("tx-id-001");
        mockTransaction.setTransactionNumber("TXN-CC-99887766");
        mockTransaction.setProductType("CREDIT_CARD");
        mockTransaction.setProductIdentifier("4557111122223333");
        mockTransaction.setType("CARD_CONSUMPTION");
        mockTransaction.setAmount(new BigDecimal("450.00"));
        mockTransaction.setDescription("Compra pasajes aéreos");
        mockTransaction.setTimestamp(LocalDateTime.now());
    }

    @Test
    @DisplayName("POST /api/credits - Debe otorgar un crédito y devolver 201 Created")
    void testCreateCreditSuccess() {
        Mockito.when(creditService.createCredit(any(Credit.class)))
                .thenReturn(Mono.just(mockCredit));

        webTestClient.post()
                .uri("/api/credits")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mockCredit)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.creditNumber").isEqualTo("CRD-11223344")
                .jsonPath("$.type").isEqualTo("PERSONAL_CREDIT");
    }

    @Test
    @DisplayName("POST /api/credits - Debe devolver 400 Bad Request si cliente personal solicita segundo crédito")
    void testCreateSecondPersonalCreditReturnsBadRequest() {
        Mockito.when(creditService.createCredit(any(Credit.class)))
                .thenReturn(Mono
                        .error(new IllegalArgumentException("Un cliente personal solo puede tener un único crédito")));

        webTestClient.post()
                .uri("/api/credits")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mockCredit)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("Un cliente personal solo puede tener un único crédito");
    }

    @Test
    @DisplayName("POST /api/credits/cards - Debe emitir tarjeta de crédito (habilitando perfil VIP/PYME) y devolver 201 Created")
    void testCreateCreditCardSuccess() {
        Mockito.when(creditService.createCreditCard(any(CreditCard.class)))
                .thenReturn(Mono.just(mockCard));

        webTestClient.post()
                .uri("/api/credits/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mockCard)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.cardNumber").isEqualTo("4557111122223333")
                .jsonPath("$.creditLimit").isEqualTo(8000.00);
    }

    @Test
    @DisplayName("GET /api/credits/customer/{customerId} - Debe listar los créditos del cliente")
    void testGetCreditsByCustomer() {
        Mockito.when(creditService.getCreditsByCustomerId("cust-vip-01"))
                .thenReturn(Flux.just(mockCredit));

        webTestClient.get()
                .uri("/api/credits/customer/cust-vip-01")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$.creditNumber").isEqualTo("CRD-11223344");
    }

    @Test
    @DisplayName("GET /api/credits/cards/number/{cardNumber} - Debe retornar tarjeta de crédito si existe")
    void testGetCardByNumberSuccess() {
        Mockito.when(creditService.getCreditCardByNumber("4557111122223333"))
                .thenReturn(Mono.just(mockCard));

        webTestClient.get()
                .uri("/api/credits/cards/number/4557111122223333")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.cardNumber").isEqualTo("4557111122223333")
                .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("GET /api/credits/cards/number/{cardNumber} - Debe devolver 404 Not Found si la tarjeta no existe")
    void testGetCardByNumberNotFound() {
        Mockito.when(creditService.getCreditCardByNumber("4557999988887777"))
                .thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/credits/cards/number/4557999988887777")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("POST /api/credits/payment - Debe procesar pago de cuota de crédito")
    void testExecuteCreditPaymentSuccess() {
        mockTransaction.setProductType("CREDIT");
        mockTransaction.setProductIdentifier("CRD-11223344");
        mockTransaction.setType("CREDIT_PAYMENT");

        Mockito.when(creditService.executeCreditPayment(
                eq("CRD-11223344"),
                eq(new BigDecimal("1000.00")),
                eq("Pago cuota 1")))
                .thenReturn(Mono.just(mockTransaction));

        Map<String, Object> payload = Map.of(
                "creditNumber", "CRD-11223344",
                "amount", 1000.00,
                "description", "Pago cuota 1");

        webTestClient.post()
                .uri("/api/credits/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.type").isEqualTo("CREDIT_PAYMENT")
                .jsonPath("$.productIdentifier").isEqualTo("CRD-11223344");
    }

    @Test
    @DisplayName("POST /api/credits/cards/operation - Debe procesar consumo con tarjeta de crédito")
    void testExecuteCardOperationSuccess() {
        Mockito.when(creditService.executeCardOperation(
                eq("4557111122223333"),
                eq("CARD_CONSUMPTION"),
                eq(new BigDecimal("450.00")),
                eq("Compra pasajes aéreos")))
                .thenReturn(Mono.just(mockTransaction));

        Map<String, Object> payload = Map.of(
                "cardNumber", "4557111122223333",
                "type", "CARD_CONSUMPTION",
                "amount", 450.00,
                "description", "Compra pasajes aéreos");

        webTestClient.post()
                .uri("/api/credits/cards/operation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.type").isEqualTo("CARD_CONSUMPTION")
                .jsonPath("$.amount").isEqualTo(450.00);
    }

    @Test
    @DisplayName("POST /api/credits/cards/operation - Debe devolver 400 Bad Request si excede la línea disponible")
    void testExecuteCardOperationInsufficientLimitReturnsBadRequest() {
        Mockito.when(creditService.executeCardOperation(
                eq("4557111122223333"),
                eq("CARD_CONSUMPTION"),
                eq(new BigDecimal("9500.00")),
                eq("Compra excedida")))
                .thenReturn(Mono.error(new IllegalArgumentException("Línea de crédito insuficiente para el consumo")));

        Map<String, Object> payload = Map.of(
                "cardNumber", "4557111122223333",
                "type", "CARD_CONSUMPTION",
                "amount", 9500.00,
                "description", "Compra excedida");

        webTestClient.post()
                .uri("/api/credits/cards/operation").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("Línea de crédito insuficiente para el consumo");
    }

}
