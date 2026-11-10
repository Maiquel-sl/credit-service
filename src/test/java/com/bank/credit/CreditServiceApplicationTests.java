package com.bank.credit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.data.mongodb.uri=mongodb://localhost:27017/credit-service-db"
})
class CreditServiceApplicationTests {

    @Test
    @DisplayName("Should verify that the credit-service runtime application context boots up successfully")
    void contextLoads() {
        // Confirms all reactive beans, repositories, and custom endpoints assemble accurately.
    }
}
