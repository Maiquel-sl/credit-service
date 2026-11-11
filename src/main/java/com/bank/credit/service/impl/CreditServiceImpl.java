package com.bank.credit.service.impl;

import com.bank.credit.events.CreditTransactionEvent;
import com.bank.credit.messaging.CreditEventPublisher;
import com.bank.credit.model.*;
import com.bank.credit.repository.*;
import com.bank.credit.service.CreditService;
import com.bank.credit.util.ReactiveFallbackHandler;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CreditServiceImpl implements CreditService {

    private final CreditRepository creditRepository;
    private final CreditCardRepository cardRepository;
    private final CreditTransactionRepository transactionRepository;
    private final CustomerReplicaRepository customerReplicaRepository;
    private final CreditEventPublisher eventPublisher;

    public CreditServiceImpl(CreditRepository creditRepository,
                             CreditCardRepository cardRepository,
                             CreditTransactionRepository transactionRepository,
                             CustomerReplicaRepository customerReplicaRepository,
                             CreditEventPublisher eventPublisher) {
        this.creditRepository = creditRepository;
        this.cardRepository = cardRepository;
        this.transactionRepository = transactionRepository;
        this.customerReplicaRepository = customerReplicaRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Mono<Credit> createCredit(Credit credit) {
        // 1. Validación local contra la réplica de clientes (sin obligación de tener cuenta bancaria)
        return customerReplicaRepository.findById(credit.getCustomerId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Cliente no encontrado en la réplica local")))
                .flatMap(customer -> {
                    if (!"ACTIVE".equals(customer.getStatus())) {
                        return Mono.error(new IllegalArgumentException("El cliente se encuentra inactivo"));
                    }

                    String verifiedType = customer.getType();
                    credit.setCustomerType(verifiedType);

                    return creditRepository.findByCustomerId(credit.getCustomerId())
                            .collectList()
                            .flatMap(existingCredits -> {
                                // Regla de negocio: Solo un crédito por cliente personal
                                if ("PERSONAL".equals(verifiedType) && !existingCredits.isEmpty()) {
                                    return Mono.error(new IllegalArgumentException(
                                            "Un cliente personal solo puede tener un único crédito"));
                                }

                                credit.setType("PERSONAL".equals(verifiedType) ? "PERSONAL_CREDIT" : "BUSINESS_CREDIT");
                                credit.setCreditNumber("CRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                                credit.setBalanceOwed(credit.getAmountGranted());
                                credit.setStatus("ACTIVE");
                                credit.setCreatedAt(LocalDateTime.now());
                                credit.setUpdatedAt(LocalDateTime.now());
                                return creditRepository.save(credit);
                            });
                });
    }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Mono<CreditCard> createCreditCard(CreditCard card) {
        // Validación local contra la réplica de clientes
        return customerReplicaRepository.findById(card.getCustomerId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Cliente no encontrado en la réplica local")))
                .flatMap(customer -> {
                    if (!"ACTIVE".equals(customer.getStatus())) {
                        return Mono.error(new IllegalArgumentException("El cliente se encuentra inactivo"));
                    }

                    card.setCustomerType(customer.getType());
                    card.setCardNumber("4557" + UUID.randomUUID().toString().replaceAll("[\\D]", "").substring(0, 12));
                    card.setAvailableBalance(card.getCreditLimit());
                    card.setBalanceOwed(BigDecimal.ZERO);
                    card.setStatus("ACTIVE");
                    card.setExpirationDate("11/31");
                    card.setCreatedAt(LocalDateTime.now());
                    card.setUpdatedAt(LocalDateTime.now());
                    return cardRepository.save(card);
                });
    }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericFluxFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Flux<Credit> getCreditsByCustomerId(String customerId) { return creditRepository.findByCustomerId(customerId); }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Mono<CreditCard> getCreditCardByNumber(String cardNumber) { return cardRepository.findByCardNumber(cardNumber); }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Mono<CreditTransaction> executeCreditPayment(String creditNumber, BigDecimal amount, String description) {
        return creditRepository.findByCreditNumber(creditNumber)
                .switchIfEmpty(Mono.error(new RuntimeException("Crédito no encontrado")))
                .flatMap(credit -> {
                    credit.setBalanceOwed(credit.getBalanceOwed().subtract(amount));
                    credit.setUpdatedAt(LocalDateTime.now());

                    CreditTransaction tx = new CreditTransaction();
                    tx.setTransactionNumber("TXN-CRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                    tx.setProductType("CREDIT");
                    tx.setProductIdentifier(credit.getCreditNumber());
                    tx.setType("CREDIT_PAYMENT");
                    tx.setAmount(amount);
                    tx.setDescription(description);
                    tx.setTimestamp(LocalDateTime.now());

                    return creditRepository.save(credit)
                    .then(transactionRepository.save(tx))
                    .doOnSuccess(savedTx -> eventPublisher.publish(new CreditTransactionEvent(
                                    savedTx.getTransactionNumber(),
                                    credit.getCustomerId(),
                                    credit.getId(),
                                    "CREDIT",
                                    credit.getType(),
                                    credit.getCreditNumber(),
                                    savedTx.getType(),
                                    savedTx.getAmount(),
                                    credit.getCurrency(),
                                    savedTx.getDescription(),
                                    savedTx.getTimestamp()
                            )));
                });
    }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Mono<CreditTransaction> executeCardOperation(String cardNumber, String type, BigDecimal amount, String description) {
        return cardRepository.findByCardNumber(cardNumber)
                .switchIfEmpty(Mono.error(new RuntimeException("Tarjeta de crédito no encontrada")))
                .flatMap(card -> {
                    // Manejo de consumos o pagos de tarjetas con Pattern Matching
                    switch (type) {
                        case "CARD_CONSUMPTION" -> {
                            if (card.getAvailableBalance().compareTo(amount) < 0) {
                                throw new IllegalArgumentException("Línea de crédito insuficiente para el consumo");
                            }
                            card.setAvailableBalance(card.getAvailableBalance().subtract(amount));
                            card.setBalanceOwed(card.getBalanceOwed().add(amount));
                        }
                        case "CARD_PAYMENT" -> {
                            card.setAvailableBalance(card.getAvailableBalance().add(amount));
                            card.setBalanceOwed(card.getBalanceOwed().subtract(amount));
                        }
                        default -> throw new IllegalArgumentException("Tipo de operación de tarjeta inválida");
                    }
                    card.setUpdatedAt(LocalDateTime.now());

                    CreditTransaction tx = new CreditTransaction();
                    tx.setTransactionNumber("TXN-CC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                    tx.setProductType("CREDIT_CARD");
                    tx.setProductIdentifier(card.getCardNumber());
                    tx.setType(type);
                    tx.setAmount(amount);
                    tx.setDescription(description);
                    tx.setTimestamp(LocalDateTime.now());

                    // Determina el subtipo dinámicamente según el perfil del cliente
                    String subType = "PERSONAL".equals(card.getCustomerType()) 
                            ? "PERSONAL_CREDIT_CARD" 
                            : "BUSINESS_CREDIT_CARD";

                    return cardRepository.save(card)
                    .then(transactionRepository.save(tx))
                    .doOnSuccess(savedTx -> eventPublisher.publish(new CreditTransactionEvent(
                                    savedTx.getTransactionNumber(),
                                    card.getCustomerId(),
                                    card.getId(),
                                    "CREDIT_CARD",
                                    subType,
                                    card.getCardNumber(),
                                    savedTx.getType(),
                                    savedTx.getAmount(),
                                    card.getCurrency(),
                                    savedTx.getDescription(),
                                    savedTx.getTimestamp()
                            )));
                });
    }

    // =========================================================================
    // MÉTODOS FALLBACK GENÉRICOS PARA TODA LA CLASE
    // =========================================================================

    @SuppressWarnings("unused")
    private <T> Mono<T> genericMonoFallback(Throwable ex) {
        return ReactiveFallbackHandler.handleMonoFallback("CreditService", ex);
    }

    @SuppressWarnings("unused")
    private <T> Flux<T> genericFluxFallback(Throwable ex) {
        return ReactiveFallbackHandler.handleFluxFallback("CreditService", ex);
    }
}
