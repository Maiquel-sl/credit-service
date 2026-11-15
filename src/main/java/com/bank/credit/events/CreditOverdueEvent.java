package com.bank.credit.events;

import java.time.LocalDateTime;

public record CreditOverdueEvent(
        String customerId,
        boolean hasOverdueDebt,
        String productIdentifier,
        LocalDateTime timestamp) {
}