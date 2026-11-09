package com.bank.credit.service.impl;

import com.bank.credit.model.*;
import com.bank.credit.repository.*;
import com.bank.credit.service.CreditService;
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

    public CreditServiceImpl(CreditRepository creditRepository,
                             CreditCardRepository cardRepository,
                             CreditTransactionRepository transactionRepository) {
        this.creditRepository = creditRepository;
        this.cardRepository = cardRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public Mono<Credit> createCredit(Credit credit) {
        return creditRepository.findByCustomerId(credit.getCustomerId())
                .collectList()
                .flatMap(existingCredits -> {
                    // Regla de negocio: Solo un crédito por persona
                    if ("PERSONAL".equals(credit.getCustomerType()) && !existingCredits.isEmpty()) {
                        return Mono.error(new IllegalArgumentException("Un cliente personal solo puede tener un único crédito"));
                    }
                    credit.setCreditNumber("CRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                    credit.setStatus("ACTIVE");
                    credit.setCreatedAt(LocalDateTime.now());
                    credit.setUpdatedAt(LocalDateTime.now());
                    return creditRepository.save(credit);
                });
    }

    @Override
    public Mono<CreditCard> createCreditCard(CreditCard card) {
        card.setCardNumber("4557" + UUID.randomUUID().toString().replaceAll("[\\D]", "").substring(0, 12));
        card.setAvailableBalance(card.getCreditLimit());
        card.setBalanceOwed(BigDecimal.ZERO);
        card.setStatus("ACTIVE");
        card.setExpirationDate("11/31");
        card.setCreatedAt(LocalDateTime.now());
        card.setUpdatedAt(LocalDateTime.now());
        return cardRepository.save(card);
    }

    @Override
    public Flux<Credit> getCreditsByCustomerId(String customerId) { return creditRepository.findByCustomerId(customerId); }

    @Override
    public Mono<CreditCard> getCreditCardByNumber(String cardNumber) { return cardRepository.findByCardNumber(cardNumber); }

    @Override
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

                    return creditRepository.save(credit).then(transactionRepository.save(tx));
                });
    }

    @Override
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

                    return cardRepository.save(card).then(transactionRepository.save(tx));
                });
    }
}
