package com.bank.credit.service;

import com.bank.credit.events.CreditCardCreatedEvent;
import com.bank.credit.events.CreditTransactionEvent;
import com.bank.credit.messaging.CreditEventPublisher;
import com.bank.credit.model.Credit;
import com.bank.credit.model.CreditCard;
import com.bank.credit.model.CreditTransaction;
import com.bank.credit.model.CustomerReplica;
import com.bank.credit.repository.CreditCardRepository;
import com.bank.credit.repository.CreditRepository;
import com.bank.credit.repository.CreditTransactionRepository;
import com.bank.credit.repository.CustomerReplicaRepository;
import com.bank.credit.service.impl.CreditServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class CreditServiceImplTest {

    @Mock
    private CreditRepository creditRepository;
    @Mock
    private CreditCardRepository cardRepository;
    @Mock
    private CreditTransactionRepository transactionRepository;
    @Mock
    private CustomerReplicaRepository customerReplicaRepository;
    @Mock
    private CreditEventPublisher eventPublisher;

    @InjectMocks
    private CreditServiceImpl creditService;

    private CustomerReplica activePersonalCustomer;
    private Credit personalCredit;
    private CreditCard creditCard;

    @BeforeEach
    void setUp() {
        activePersonalCustomer = new CustomerReplica();
        activePersonalCustomer.setCustomerId("cust-vip-01");
        activePersonalCustomer.setType("PERSONAL");
        activePersonalCustomer.setStatus("ACTIVE");

        personalCredit = new Credit();
        personalCredit.setId("crd-id-001");
        personalCredit.setCustomerId("cust-vip-01");
        personalCredit.setAmountGranted(new BigDecimal("5000.00"));
        personalCredit.setBalanceOwed(new BigDecimal("5000.00"));
        personalCredit.setCurrency("PEN");

        creditCard = new CreditCard();
        creditCard.setId("cc-id-001");
        creditCard.setCustomerId("cust-vip-01");
        creditCard.setCreditLimit(new BigDecimal("8000.00"));
        creditCard.setAvailableBalance(new BigDecimal("8000.00"));
        creditCard.setBalanceOwed(BigDecimal.ZERO);
        creditCard.setCurrency("PEN");
    }

    @Test
    @DisplayName("Debe otorgar crédito personal validando réplica local y sin exigir cuenta bancaria")
    void testCreatePersonalCreditSuccess() {
        Mockito.when(customerReplicaRepository.findById("cust-vip-01"))
                .thenReturn(Mono.just(activePersonalCustomer));
        Mockito.when(creditRepository.findByCustomerId("cust-vip-01"))
                .thenReturn(Flux.empty());
        Mockito.when(creditRepository.save(any(Credit.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(creditService.createCredit(personalCredit))
                .expectNextMatches(saved -> saved.getCreditNumber() != null &&
                        saved.getCreditNumber().startsWith("CRD-") &&
                        "PERSONAL_CREDIT".equals(saved.getType()) &&
                        "ACTIVE".equals(saved.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Debe rechazar segundo crédito para un cliente PERSONAL")
    void testCreateSecondPersonalCreditFails() {
        Mockito.when(customerReplicaRepository.findById("cust-vip-01"))
                .thenReturn(Mono.just(activePersonalCustomer));
        Mockito.when(creditRepository.findByCustomerId("cust-vip-01"))
                .thenReturn(Flux.just(personalCredit));

        StepVerifier.create(creditService.createCredit(personalCredit))
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException &&
                        ex.getMessage().contains("único crédito"))
                .verify();
    }

    @Test
    @DisplayName("Debe crear tarjeta de crédito y emitir CreditCardCreatedEvent a Kafka para habilitar cuentas VIP/PYME")
    void testCreateCreditCardEmitsKafkaEvent() {
        Mockito.when(customerReplicaRepository.findById("cust-vip-01"))
                .thenReturn(Mono.just(activePersonalCustomer));
        Mockito.when(cardRepository.save(any(CreditCard.class)))
                .thenAnswer(inv -> {
                    CreditCard saved = inv.getArgument(0);
                    saved.setId("cc-generated-99");
                    return Mono.just(saved);
                });

        StepVerifier.create(creditService.createCreditCard(creditCard))
                .expectNextMatches(saved -> saved.getCardNumber() != null &&
                        saved.getCardNumber().startsWith("4557") &&
                        "ACTIVE".equals(saved.getStatus()))
                .verifyComplete();

        // Verifica que se publicó el evento hacia Kafka para sincronizar
        // account-service
        Mockito.verify(eventPublisher).publishCardCreated(
                argThat((CreditCardCreatedEvent event) -> "cc-generated-99".equals(event.cardId()) &&
                        "cust-vip-01".equals(event.customerId()) &&
                        "ACTIVE".equals(event.status())));
    }

    @Test
    @DisplayName("Debe procesar consumo con tarjeta de crédito y emitir CreditTransactionEvent hacia Kafka")
    void testExecuteCardConsumptionEmitsTransactionEvent() {
        creditCard.setCardNumber("4557111122223333");
        creditCard.setCustomerType("PERSONAL");

        Mockito.when(cardRepository.findByCardNumber("4557111122223333"))
                .thenReturn(Mono.just(creditCard));
        Mockito.when(cardRepository.save(any(CreditCard.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        Mockito.when(transactionRepository.save(any(CreditTransaction.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(creditService.executeCardOperation(
                "4557111122223333", "CARD_CONSUMPTION", new BigDecimal("450.00"), "Compra pasajes aéreos"))
                .expectNextMatches(tx -> tx.getTransactionNumber().startsWith("TXN-CC-") &&
                        creditCard.getAvailableBalance().compareTo(new BigDecimal("7550.00")) == 0 &&
                        creditCard.getBalanceOwed().compareTo(new BigDecimal("450.00")) == 0)
                .verifyComplete();

        // Verifica la publicación del evento contable hacia transaction-service
        Mockito.verify(eventPublisher)
                .publish(argThat((CreditTransactionEvent event) -> "CREDIT_CARD".equals(event.productType()) &&
                        "4557111122223333".equals(event.productIdentifier()) &&
                        "CARD_CONSUMPTION".equals(event.operationType())));
    }

    @Test
    @DisplayName("Debe procesar pago de cuota de crédito y reducir deuda")
    void testExecuteCreditPaymentSuccess() {
        personalCredit.setCreditNumber("CRD-11223344");
        personalCredit.setType("PERSONAL_CREDIT");

        Mockito.when(creditRepository.findByCreditNumber("CRD-11223344"))
                .thenReturn(Mono.just(personalCredit));
        Mockito.when(creditRepository.save(any(Credit.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        Mockito.when(transactionRepository.save(any(CreditTransaction.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier
                .create(creditService.executeCreditPayment("CRD-11223344", new BigDecimal("1000.00"), "Pago cuota 1"))
                .expectNextMatches(tx -> "CREDIT_PAYMENT".equals(tx.getType()) &&
                        personalCredit.getBalanceOwed().compareTo(new BigDecimal("4000.00")) == 0)
                .verifyComplete();

        Mockito.verify(eventPublisher).publish(any(CreditTransactionEvent.class));
    }
}
