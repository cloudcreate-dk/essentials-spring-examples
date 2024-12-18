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

package dk.cloudcreate.essentials.spring.examples.postgresql.messaging;


import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.operations.ConsumeFromQueue;
import dk.cloudcreate.essentials.components.foundation.reactive.command.DurableLocalCommandBus;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.types.CorrelationId;
import dk.cloudcreate.essentials.shared.time.StopWatch;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@Testcontainers
@DirtiesContext
public class DurableQueuesLoadIT {
    @Container
    static  PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("test")
            .withPassword("test")
            .withUsername("test");
    @Container
    static  org.testcontainers.kafka.KafkaContainer       kafkaContainer = new org.testcontainers.kafka.KafkaContainer("apache/kafka-native:latest")
            .withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094");


    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);

        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    private DurableQueueConsumer   consumer;
    @Autowired
    private DurableLocalCommandBus commandBus;

    @Autowired
    private UnitOfWorkFactory<?> unitOfWorkFactory;

    @Autowired
    private DurableQueues durableQueues;

    @AfterEach
    void cleanup() {
        if (consumer != null) {
            consumer.cancel();
        }
    }

    @Test
    void queue_a_large_number_of_messages() {
        // Given
        var queueName = QueueName.of("TestQueue");
        var now       = Instant.now();

        var msgHandler = new RecordingQueuedMessageHandler();
        consumer = durableQueues.consumeFromQueue(ConsumeFromQueue.builder()
                                                                  .setQueueName(queueName)
                                                                  .setRedeliveryPolicy(RedeliveryPolicy.fixedBackoff(Duration.ofMillis(100), 0))
                                                                  .setParallelConsumers(1)
                                                                  .setConsumerName("TestConsumer")
                                                                  .setQueueMessageHandler(msgHandler)
                                                                  .build());

        var count     = 20000;
        var stopwatch = StopWatch.start();
        unitOfWorkFactory.usingUnitOfWork(uow -> {
            IntStream.range(0, count).forEach(i -> {
                durableQueues.queueMessage(queueName,
                                           Message.of(("Message" + i),
                                                      MessageMetaData.of("correlation_id", CorrelationId.random(),
                                                                         "trace_id", UUID.randomUUID().toString())));
            });
        });
        System.out.println(msg("-----> {} Queueing {} messages took {}", Instant.now(), count, stopwatch.stop()));

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(count);
        var nextMessages = durableQueues.queryForMessagesSoonReadyForDelivery(queueName,
                                                                              now,
                                                                              10);
        assertThat(nextMessages).hasSize(10);


        Awaitility.waitAtMost(Duration.ofSeconds(20))
                  .untilAsserted(() -> {
                      System.out.println("-----> " + Instant.now() + " messages received: " + msgHandler.messagesReceived.get());
                      assertThat(msgHandler.messagesReceived.get()).isGreaterThan(10);
                  });
        consumer.cancel();
        consumer = null;

    }

    static class RecordingQueuedMessageHandler implements QueuedMessageHandler {
        AtomicLong messagesReceived = new AtomicLong();

        @Override
        public void handle(QueuedMessage message) {
            messagesReceived.getAndIncrement();
        }
    }
}