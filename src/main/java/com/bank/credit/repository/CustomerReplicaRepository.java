package com.bank.credit.repository;

import com.bank.credit.model.CustomerReplica;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface CustomerReplicaRepository extends ReactiveMongoRepository<CustomerReplica, String> {
}
