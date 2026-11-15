package com.bank.credit.service.impl;

import com.bank.credit.events.CreditCardCreatedEvent;
import com.bank.credit.events.CreditOverdueEvent;
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
        // 1. Validación local contra la réplica de clientes (sin obligación de tener
        // cuenta bancaria)
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
                                // Validar que no tenga deudas vencidas antes de otorgar crédito
                                return validateNoOverdueDebt(credit.getCustomerId())
                                        .then(Mono.defer(() -> {
                                            credit.setType("PERSONAL".equals(verifiedType) ? "PERSONAL_CREDIT"
                                                    : "BUSINESS_CREDIT");
                                            credit.setCreditNumber(
                                                    "CRD-" + UUID.randomUUID().toString().substring(0, 8)
                                                            .toUpperCase());
                                            credit.setBalanceOwed(credit.getAmountGranted());
                                            credit.setStatus("ACTIVE");
                                            credit.setCreatedAt(LocalDateTime.now());
                                            credit.setUpdatedAt(LocalDateTime.now());
                                            return creditRepository.save(credit);
                                        }));
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

                    // Validar que no tenga deudas vencidas antes de emitir tarjeta
                    return validateNoOverdueDebt(card.getCustomerId())
                            .then(Mono.defer(() -> {
                                card.setCustomerType(customer.getType());
                                card.setCardNumber(
                                        "4557" + UUID.randomUUID().toString().replaceAll("[\\D]", "").substring(0, 12));
                                card.setAvailableBalance(card.getCreditLimit());
                                card.setBalanceOwed(BigDecimal.ZERO);
                                card.setStatus("ACTIVE");
                                card.setExpirationDate("11/31");
                                card.setCreatedAt(LocalDateTime.now());
                                card.setUpdatedAt(LocalDateTime.now());

                                return cardRepository.save(card)
                                        .doOnSuccess(savedCard -> eventPublisher.publishCardCreated(
                                                new CreditCardCreatedEvent(savedCard.getId(), savedCard.getCustomerId(),
                                                        "ACTIVE",
                                                        LocalDateTime.now())));
                            }));
                });
    }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericFluxFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Flux<Credit> getCreditsByCustomerId(String customerId) {
        return creditRepository.findByCustomerId(customerId);
    }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Mono<CreditCard> getCreditCardByNumber(String cardNumber) {
        return cardRepository.findByCardNumber(cardNumber);
    }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Mono<CreditTransaction> executeCreditPayment(
            String creditNumber, BigDecimal amount, String description, String payerCustomerId) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new IllegalArgumentException("El monto del pago debe ser mayor a cero"));
        }

        return creditRepository.findByCreditNumber(creditNumber.trim())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Crédito no encontrado")))
                .flatMap(credit -> {
                    BigDecimal newBalanceOwed = credit.getBalanceOwed().subtract(amount);
                    if (newBalanceOwed.compareTo(BigDecimal.ZERO) < 0) {
                        newBalanceOwed = BigDecimal.ZERO;
                    }
                    credit.setBalanceOwed(newBalanceOwed);
                    if (newBalanceOwed.compareTo(BigDecimal.ZERO) == 0) {
                        credit.setStatus("PAID");
                    }
                    credit.setUpdatedAt(LocalDateTime.now());

                    // Trazabilidad si el pago lo realiza un cliente tercero
                    String baseDesc = (description != null && !description.isBlank()) ? description
                            : "Pago de cuota de crédito";
                    String finalDescription = buildThirdPartyDescription(baseDesc, credit.getCustomerId(),
                            payerCustomerId);

                    CreditTransaction tx = new CreditTransaction();
                    tx.setTransactionNumber("TXN-CRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                    tx.setProductType("CREDIT");
                    tx.setProductIdentifier(credit.getCreditNumber());
                    tx.setType("CREDIT_PAYMENT");
                    tx.setAmount(amount);
                    tx.setDescription(finalDescription);
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
                                    savedTx.getTimestamp())));
                });
    }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Mono<CreditTransaction> executeCardOperation(
            String cardNumber, String type, BigDecimal amount, String description, String payerCustomerId) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new IllegalArgumentException("El monto de la operación debe ser mayor a cero"));
        }

        return cardRepository.findByCardNumber(cardNumber.trim())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Tarjeta de crédito no encontrada")))
                .flatMap(card -> {
                    if (!"ACTIVE".equals(card.getStatus()) && !"OVERDUE".equals(card.getStatus())) {
                        return Mono.error(new IllegalArgumentException("La tarjeta de crédito se encuentra bloqueada"));
                    }

                    String baseDesc = (description != null && !description.isBlank()) ? description
                            : "Operación con tarjeta de crédito";
                    String finalDescription = baseDesc;

                    if ("CARD_CONSUMPTION".equals(type)) {
                        if (!"ACTIVE".equals(card.getStatus())) {
                            return Mono.error(new IllegalArgumentException(
                                    "No se permiten consumos en una tarjeta con deuda vencida"));
                        }
                        if (card.getAvailableBalance().compareTo(amount) < 0) {
                            return Mono.error(
                                    new IllegalArgumentException("Línea de crédito insuficiente para el consumo"));
                        }
                        card.setAvailableBalance(card.getAvailableBalance().subtract(amount));
                        card.setBalanceOwed(card.getBalanceOwed().add(amount));
                    } else if ("CARD_PAYMENT".equals(type)) {
                        card.setAvailableBalance(card.getAvailableBalance().add(amount));
                        BigDecimal newOwed = card.getBalanceOwed().subtract(amount);
                        card.setBalanceOwed(newOwed.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newOwed);
                        // Trazabilidad si el abono a la tarjeta lo realiza un tercero
                        finalDescription = buildThirdPartyDescription(baseDesc, card.getCustomerId(), payerCustomerId);
                    } else {
                        return Mono.error(new IllegalArgumentException("Tipo de operación inválida: " + type));
                    }

                    card.setUpdatedAt(LocalDateTime.now());

                    CreditTransaction tx = new CreditTransaction();
                    tx.setTransactionNumber("TXN-CC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                    tx.setProductType("CREDIT_CARD");
                    tx.setProductIdentifier(card.getCardNumber());
                    tx.setType(type);
                    tx.setAmount(amount);
                    tx.setDescription(finalDescription);
                    tx.setTimestamp(LocalDateTime.now());

                    return cardRepository.save(card)
                            .then(transactionRepository.save(tx))
                            .doOnSuccess(savedTx -> eventPublisher.publish(new CreditTransactionEvent(
                                    savedTx.getTransactionNumber(),
                                    card.getCustomerId(),
                                    card.getId(),
                                    "CREDIT_CARD",
                                    card.getCustomerType() + "_CREDIT_CARD",
                                    card.getCardNumber(),
                                    savedTx.getType(),
                                    savedTx.getAmount(),
                                    card.getCurrency(),
                                    savedTx.getDescription(),
                                    savedTx.getTimestamp())));
                });
    }

    // Endpoint/Método para reportar morosidad (OVERDUE) y notificar a
    // account-service por Kafka
    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Mono<Credit> reportCreditOverdue(String creditNumber) {
        return creditRepository.findByCreditNumber(creditNumber.trim())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Crédito no encontrado")))
                .flatMap(credit -> {
                    credit.setStatus("OVERDUE");
                    credit.setUpdatedAt(LocalDateTime.now());
                    return creditRepository.save(credit)
                            .doOnSuccess(saved -> eventPublisher.publishOverdueStatus(
                                    new CreditOverdueEvent(saved.getCustomerId(), true, saved.getCreditNumber(),
                                            LocalDateTime.now())));
                });
    }

    private Mono<Void> validateNoOverdueDebt(String customerId) {
        Mono<Boolean> hasOverdueCredits = creditRepository.findByCustomerId(customerId)
                .any(c -> "OVERDUE".equals(c.getStatus()));

        Mono<Boolean> hasOverdueCards = cardRepository.findByCustomerId(customerId)
                .any(card -> "OVERDUE".equals(card.getStatus()));

        return Mono.zip(hasOverdueCredits, hasOverdueCards)
                .flatMap(tuple -> {
                    if (tuple.getT1() || tuple.getT2()) {
                        return Mono.error(new IllegalArgumentException(
                                "El cliente posee una deuda vencida en un producto de crédito y no puede adquirir nuevos productos"));
                    }
                    return Mono.empty();
                });
    }

    // Método auxiliar para registrar en la descripción cuando paga un cliente
    // tercero
    private String buildThirdPartyDescription(String baseDescription, String ownerCustomerId, String payerCustomerId) {
        if (payerCustomerId != null && !payerCustomerId.isBlank() && !payerCustomerId.trim().equals(ownerCustomerId)) {
            return baseDescription + " [Pagado por tercero - Cliente ID: " + payerCustomerId.trim() + "]";
        }
        return baseDescription;
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
