package com.bank.credit.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "credit_transactions")
public class CreditTransaction {
    @Id
    private String id;
    private String transactionNumber;
    private String productType; // CREDIT, CREDIT_CARD
    private String productIdentifier; // creditNumber o cardNumber
    private String type; // CREDIT_PAYMENT, CARD_CONSUMPTION, CARD_PAYMENT
    private BigDecimal amount;
    private String description;
    private LocalDateTime timestamp;

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTransactionNumber() { return transactionNumber; }
    public void setTransactionNumber(String transactionNumber) { this.transactionNumber = transactionNumber; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public String getProductIdentifier() { return productIdentifier; }
    public void setProductIdentifier(String productIdentifier) { this.productIdentifier = productIdentifier; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
