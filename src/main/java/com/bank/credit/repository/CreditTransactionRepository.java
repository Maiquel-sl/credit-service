package com.bank.credit.repository;

import com.bank.credit.model.CreditTransaction;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface CreditTransactionRepository extends ReactiveMongoRepository<CreditTransaction, String> {
}
