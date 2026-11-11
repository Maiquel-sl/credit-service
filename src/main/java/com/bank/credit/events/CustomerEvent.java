package com.bank.credit.events;

import java.time.LocalDateTime;

public record CustomerEvent(
    String eventType,   // CREATED, UPDATED
    String customerId,
    String type,        // PERSONAL, ENTERPRISE
    String status,      // ACTIVE, INACTIVE
    LocalDateTime timestamp
) {}
