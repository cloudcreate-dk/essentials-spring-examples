/*
 * Copyright 2021-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking;

import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.reactive.command.CommandBus;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.*;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.commands.RequestIntraBankMoneyTransfer;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.account.*;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.transactions.intrabank.*;
import dk.cloudcreate.essentials.types.Amount;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {Application.class, TestConfiguration.class})
@Testcontainers
@Slf4j
@DirtiesContext
public class TransferMoneyProcessorIT {

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("test")
            .withPassword("test")
            .withUsername("test");

    @Container
    static org.testcontainers.kafka.KafkaContainer kafkaContainer = new org.testcontainers.kafka.KafkaContainer("apache/kafka-native:latest")
            .withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    @Autowired
    private Accounts accounts;

    @Autowired
    private IntraBankMoneyTransfers moneyTransfers;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private UnitOfWorkFactory<? extends UnitOfWork> unitOfWorkFactory;

    @Autowired
    private DurableQueues durableQueues;

    @Autowired
    private TransferMoneyProcessor transferMoneyProcessor;

    @Test
    void test_request_intrabank_money_transfer() {
        var account1Id                    = AccountId.random();
        var account1BalanceBeforeTransfer = Amount.of("100");
        unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            var account1 = accounts.openNewAccount(account1Id,
                                                   AccountNumber.of("001123456"));
            account1.depositToday(account1BalanceBeforeTransfer, TransactionId.random());
        });

        var account2Id = AccountId.random();
        unitOfWorkFactory.usingUnitOfWork(unitOfWork -> accounts.openNewAccount(account2Id,
                                                                                AccountNumber.of("9876541")));

        var transactionId  = TransactionId.random();
        var transferAmount = Amount.of("10");
        commandBus.sendAndDontWait(new RequestIntraBankMoneyTransfer(transactionId,
                                                                     account1Id,
                                                                     account2Id,
                                                                     transferAmount));

        Awaitility.waitAtMost(Duration.ofSeconds(10))
                  .untilAsserted(() -> {
                      var account2Balance = unitOfWorkFactory.withUnitOfWork(uow -> accounts.getAccount(account2Id).getBalance());
                      assertThat(account2Balance).isEqualTo(transferAmount);
                  });

        var account1Balance = unitOfWorkFactory.withUnitOfWork(uow -> accounts.getAccount(account1Id).getBalance());
        assertThat(account1Balance).isEqualTo(account1BalanceBeforeTransfer.subtract(transferAmount));

        Awaitility.waitAtMost(Duration.ofSeconds(10))
                  .untilAsserted(() -> {
                      var transferStatus = unitOfWorkFactory.withUnitOfWork(uow -> moneyTransfers.getTransfer(transactionId).getStatus());
                      assertThat(transferStatus).isEqualTo(TransferLifeCycleStatus.COMPLETED);
                  });

    }
}