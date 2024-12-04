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

import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues;
import dk.cloudcreate.essentials.components.foundation.reactive.command.DurableLocalCommandBus;
import dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping.OrderShippingProcessor;
import dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping.adapters.kafka.outgoing.ShippingEventKafkaPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

@Testcontainers
@DirtiesContext
@SpringBootTest(classes = TestApplication.class)
public class AbstractIntegrationTest {

    @Container
    protected static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("test")
            .withPassword("test")
            .withUsername("test");

    @Container
    static    KafkaContainer                                kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));
    protected KafkaMessageListenerContainer<String, Object> kafkaListenerContainer;

    @DynamicPropertySource
    protected static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);

        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    @Autowired
    protected KafkaTemplate<String, Object> kafkaTemplate;


    @Autowired
    protected OrderShippingProcessor orderShippingProcessor;

    @Autowired
    protected ShippingEventKafkaPublisher shippingEventKafkaPublisher;

    @Autowired
    protected DurableLocalCommandBus commandBus;

    @Autowired
    protected DurableQueues durableQueues;

    @Autowired
    protected ConsumerFactory<String, Object> kafkaConsumerFactory;

    protected List<ConsumerRecord<String, Object>> shippingRecordsReceived;


}
