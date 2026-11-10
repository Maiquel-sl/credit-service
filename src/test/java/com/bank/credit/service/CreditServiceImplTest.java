package com.bank.credit.service;

import com.bank.credit.model.Credit;
import com.bank.credit.model.CreditCard;
import com.bank.credit.model.CreditTransaction;
import com.bank.credit.repository.CreditCardRepository;
import com.bank.credit.repository.CreditRepository;
import com.bank.credit.repository.CreditTransactionRepository;
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

@ExtendWith(MockitoExtension.class)
class CreditServiceImplTest {

    @Mock private CreditRepository creditRepository;
    @Mock private CreditCardRepository cardRepository;
    @Mock private CreditTransactionRepository transactionRepository;

    @InjectMocks private CreditServiceImpl creditService;

    private Credit personalCredit;
    private CreditCard mockCard;

    @BeforeEach
    void setUp() {
        personalCredit = new Credit();
        personalCredit.setId("crd-001");
        personalCredit.setCustomerId("cust-personal-123");
        personalCredit.setCustomerType("PERSONAL");
        personalCredit.setType("PERSONAL_CREDIT");
        personalCredit.setAmountGranted(new BigDecimal("5000.00"));
        personalCredit.setBalanceOwed(new BigDecimal("5000.00"));

        mockCard = new CreditCard();
        mockCard.setId("card-001");
        mockCard.setCardNumber("4557111122223333");
        mockCard.setCustomerId("cust-personal-123");
        mockCard.setCreditLimit(new BigDecimal("3000.00"));
        mockCard.setAvailableBalance(new BigDecimal("3000.00"));
        mockCard.setBalanceOwed(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should fail when a personal customer tries to obtain a second credit")
    void testCreateCreditPersonalDuplicateDisallowed() {
        // Arrange
        Mockito.when(creditRepository.findByCustomerId("cust-personal-123"))
                .thenReturn(Flux.just(personalCredit));

        // Act
        Mono<Credit> result = creditService.createCredit(personalCredit);

        // Assert
        StepVerifier.create(result)
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("Should successfully provision credit when personal limits are respected")
    void testCreateCreditPersonalSuccess() {
        // Arrange
        Mockito.when(creditRepository.findByCustomerId("cust-personal-123")).thenReturn(Flux.empty());
        Mockito.when(creditRepository.save(any(Credit.class))).thenReturn(Mono.just(personalCredit));

        // Act
        Mono<Credit> result = creditService.createCredit(personalCredit);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(credit -> "ACTIVE".equals(credit.getStatus()) && credit.getCreditNumber() != null)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should block credit card transactions exceeding the assigned limit")
    void testExecuteCardOperationInsufficientLimit() {
        // Arrange
        Mockito.when(cardRepository.findByCardNumber("4557111122223333")).thenReturn(Mono.just(mockCard));

        // Act
        Mono<CreditTransaction> result = creditService.executeCardOperation(
                "4557111122223333", "CARD_CONSUMPTION", new BigDecimal("3500.00"), "Luxury Item Purchase");

        // Assert
        StepVerifier.create(result)
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
