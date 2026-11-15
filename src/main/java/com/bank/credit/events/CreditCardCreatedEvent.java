package com.bank.credit.events;

import java.time.LocalDateTime;

public record CreditCardCreatedEvent(
    String cardId,
    String customerId,
    String status,
    LocalDateTime timestamp
) {}
