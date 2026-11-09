package com.bank.credit.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "credits")
public class Credit {
    @Id
    private String id;
    private String creditNumber;
    private String customerId;
    private String customerType; // PERSONAL, ENTERPRISE
    private String type; // PERSONAL_CREDIT, BUSINESS_CREDIT
    private String status; // ACTIVE, INACTIVE
    private BigDecimal amountGranted;
    private BigDecimal balanceOwed;
    private String currency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Getters y Setters de Java 27
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCreditNumber() { return creditNumber; }
    public void setCreditNumber(String creditNumber) { this.creditNumber = creditNumber; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getAmountGranted() { return amountGranted; }
    public void setAmountGranted(BigDecimal amountGranted) { this.amountGranted = amountGranted; }
    public BigDecimal getBalanceOwed() { return balanceOwed; }
    public void setBalanceOwed(BigDecimal balanceOwed) { this.balanceOwed = balanceOwed; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
