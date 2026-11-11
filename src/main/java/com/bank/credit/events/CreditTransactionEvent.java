package com.bank.credit.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreditTransactionEvent(
    String transactionNumber,
    String customerId,
    String productId,
    String productType,       // CREDIT, CREDIT_CARD
    String productSubType,    // PERSONAL_CREDIT, BUSINESS_CREDIT
    String productIdentifier, // creditNumber o cardNumber
    String operationType,     // CREDIT_PAYMENT, CARD_CONSUMPTION, CARD_PAYMENT
    BigDecimal amount,
    String currency,
    String description,
    LocalDateTime timestamp
) {}
