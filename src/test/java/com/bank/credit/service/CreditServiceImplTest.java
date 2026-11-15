package com.bank.credit.service;

import com.bank.credit.events.CreditCardCreatedEvent;
import com.bank.credit.events.CreditOverdueEvent;
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
                personalCredit.setCreditNumber("CRD-11223344");
                personalCredit.setType("PERSONAL_CREDIT");
                personalCredit.setAmountGranted(new BigDecimal("5000.00"));
                personalCredit.setBalanceOwed(new BigDecimal("5000.00"));
                personalCredit.setCurrency("PEN");
                personalCredit.setStatus("ACTIVE");

                creditCard = new CreditCard();
                creditCard.setId("cc-id-001");
                creditCard.setCustomerId("cust-vip-01");
                creditCard.setCustomerType("PERSONAL");
                creditCard.setCardNumber("4557111122223333");
                creditCard.setCreditLimit(new BigDecimal("8000.00"));
                creditCard.setAvailableBalance(new BigDecimal("8000.00"));
                creditCard.setBalanceOwed(BigDecimal.ZERO);
                creditCard.setCurrency("PEN");
                creditCard.setStatus("ACTIVE");
        }

        @Test
        @DisplayName("Debe otorgar crédito personal validando réplica local y ausencia de deuda vencida")
        void testCreatePersonalCreditSuccess() {
                Mockito.when(customerReplicaRepository.findById("cust-vip-01"))
                                .thenReturn(Mono.just(activePersonalCustomer));
                Mockito.when(creditRepository.findByCustomerId("cust-vip-01")).thenReturn(Flux.empty());
                Mockito.when(cardRepository.findByCustomerId("cust-vip-01")).thenReturn(Flux.empty());
                Mockito.when(creditRepository.save(any(Credit.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

                StepVerifier.create(creditService.createCredit(personalCredit))
                                .expectNextMatches(saved -> saved.getCreditNumber() != null &&
                                                saved.getCreditNumber().startsWith("CRD-") &&
                                                "ACTIVE".equals(saved.getStatus()))
                                .verifyComplete();
        }

        @Test
        @DisplayName("Debe rechazar nuevo crédito si el cliente posee una deuda vencida (OVERDUE)")
        void testCreateCreditBlockedByOverdueDebt() {
                Credit overdueCredit = new Credit();
                overdueCredit.setCreditNumber("CRD-OLD-999");
                overdueCredit.setCustomerId("cust-vip-01");
                overdueCredit.setStatus("OVERDUE");

                Mockito.when(customerReplicaRepository.findById("cust-vip-01"))
                                .thenReturn(Mono.just(activePersonalCustomer));
                Mockito.when(creditRepository.findByCustomerId("cust-vip-01")).thenReturn(Flux.just(overdueCredit));
                Mockito.when(cardRepository.findByCustomerId("cust-vip-01")).thenReturn(Flux.empty());

                StepVerifier.create(creditService.createCredit(personalCredit))
                                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException &&
                                                ex.getMessage().contains("deuda vencida"))
                                .verify();
        }

        @Test
        @DisplayName("Debe crear tarjeta de crédito y emitir CreditCardCreatedEvent a Kafka")
        void testCreateCreditCardEmitsKafkaEvent() {
                Mockito.when(customerReplicaRepository.findById("cust-vip-01"))
                                .thenReturn(Mono.just(activePersonalCustomer));
                Mockito.when(creditRepository.findByCustomerId("cust-vip-01")).thenReturn(Flux.empty());
                Mockito.when(cardRepository.findByCustomerId("cust-vip-01")).thenReturn(Flux.empty());
                Mockito.when(cardRepository.save(any(CreditCard.class))).thenAnswer(inv -> {
                        CreditCard saved = inv.getArgument(0);
                        saved.setId("cc-generated-99");
                        return Mono.just(saved);
                });

                StepVerifier.create(creditService.createCreditCard(creditCard))
                                .expectNextMatches(saved -> saved.getCardNumber().startsWith("4557"))
                                .verifyComplete();

                Mockito.verify(eventPublisher).publishCardCreated(
                                argThat((CreditCardCreatedEvent event) -> "cc-generated-99".equals(event.cardId()) &&
                                                "cust-vip-01".equals(event.customerId())));
        }

        @Test
        @DisplayName("Debe marcar crédito como OVERDUE y emitir CreditOverdueEvent hacia Kafka")
        void testReportCreditOverdueEmitsKafkaEvent() {
                Mockito.when(creditRepository.findByCreditNumber("CRD-11223344")).thenReturn(Mono.just(personalCredit));
                Mockito.when(creditRepository.save(any(Credit.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

                StepVerifier.create(creditService.reportCreditOverdue("CRD-11223344"))
                                .expectNextMatches(updated -> "OVERDUE".equals(updated.getStatus()))
                                .verifyComplete();

                Mockito.verify(eventPublisher).publishOverdueStatus(
                                argThat((CreditOverdueEvent event) -> "cust-vip-01".equals(event.customerId())
                                                && event.hasOverdueDebt()));
        }

        @Test
        @DisplayName("Debe procesar consumo con tarjeta de crédito (sin payerCustomerId)")
        void testExecuteCardConsumptionSuccess() {
                Mockito.when(cardRepository.findByCardNumber("4557111122223333")).thenReturn(Mono.just(creditCard));
                Mockito.when(cardRepository.save(any(CreditCard.class)))
                                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                Mockito.when(transactionRepository.save(any(CreditTransaction.class)))
                                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

                StepVerifier.create(creditService.executeCardOperation(
                                "4557111122223333", "CARD_CONSUMPTION", new BigDecimal("450.00"), "Compra pasajes",
                                null))
                                .expectNextMatches(tx -> "Compra pasajes".equals(tx.getDescription()) &&
                                                creditCard.getAvailableBalance()
                                                                .compareTo(new BigDecimal("7550.00")) == 0)
                                .verifyComplete();

                Mockito.verify(eventPublisher).publish(any(CreditTransactionEvent.class));
        }

        @Test
        @DisplayName("Debe registrar trazabilidad en la descripción cuando un tercero paga un crédito")
        void testExecuteThirdPartyCreditPaymentAddsAuditNote() {
                Mockito.when(creditRepository.findByCreditNumber("CRD-11223344")).thenReturn(Mono.just(personalCredit));
                Mockito.when(creditRepository.save(any(Credit.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                Mockito.when(transactionRepository.save(any(CreditTransaction.class)))
                                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

                String thirdPartyPayerId = "cust-third-party-99";

                StepVerifier.create(creditService.executeCreditPayment(
                                "CRD-11223344", new BigDecimal("1000.00"), "Pago cuota 1", thirdPartyPayerId))
                                .expectNextMatches(tx -> "CREDIT_PAYMENT".equals(tx.getType()) &&
                                                tx.getDescription().contains(
                                                                "[Pagado por tercero - Cliente ID: cust-third-party-99]")
                                                &&
                                                personalCredit.getBalanceOwed()
                                                                .compareTo(new BigDecimal("4000.00")) == 0)
                                .verifyComplete();

                // Verifica que el evento a Kafka mantenga al dueño original del crédito y lleve
                // la descripción con el tercero
                Mockito.verify(eventPublisher).publish(argThat((CreditTransactionEvent event) -> "cust-vip-01"
                                .equals(event.customerId()) &&
                                event.description()
                                                .contains("[Pagado por tercero - Cliente ID: cust-third-party-99]")));
        }

        @Test
        @DisplayName("Debe registrar trazabilidad cuando un tercero abona a una tarjeta de crédito")
        void testExecuteThirdPartyCreditCardPaymentAddsAuditNote() {
                creditCard.setAvailableBalance(new BigDecimal("7000.00"));
                creditCard.setBalanceOwed(new BigDecimal("1000.00"));

                Mockito.when(cardRepository.findByCardNumber("4557111122223333")).thenReturn(Mono.just(creditCard));
                Mockito.when(cardRepository.save(any(CreditCard.class)))
                                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                Mockito.when(transactionRepository.save(any(CreditTransaction.class)))
                                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                StepVerifier.create(creditService.executeCardOperation(
                                "4557111122223333", "CARD_PAYMENT", new BigDecimal("500.00"), "Abono tarjeta",
                                "cust-third-party-99"))
                                .expectNextMatches(tx -> tx.getDescription()
                                                .contains("[Pagado por tercero - Cliente ID: cust-third-party-99]") &&
                                                creditCard.getBalanceOwed().compareTo(new BigDecimal("500.00")) == 0)
                                .verifyComplete();
        }
}