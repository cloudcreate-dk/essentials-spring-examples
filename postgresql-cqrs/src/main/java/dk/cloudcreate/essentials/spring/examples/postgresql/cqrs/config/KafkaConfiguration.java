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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.shipping.OrderShippingProcessor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.*;
import org.springframework.scheduling.concurrent.*;

import java.util.Map;
import java.util.concurrent.Executors;

@Configuration
@EnableKafka
public class KafkaConfiguration {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Autowired
    ObjectMapper objectMapper;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                                            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                                            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config,
                                                 new StringSerializer(),
                                                 new JsonSerializer<>(objectMapper));
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                                            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                                            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config,
                                                 new StringDeserializer(),
                                                 new JsonDeserializer<>(objectMapper)
                                                         .trustedPackages(OrderShippingProcessor.class.getPackageName() + ".*"));
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        var kafkaTemplate = new KafkaTemplate<>(producerFactory());
        kafkaTemplate.setObservationEnabled(true);
        return kafkaTemplate;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setObservationEnabled(true);
        var executorService = Executors.newCachedThreadPool(ThreadFactoryBuilder.builder()
                                                                                .daemon(true)
                                                                                .nameFormat("Kafka-Listener-Task-Executor-%d")
                                                                                .build());
        var taskExecutor = new ConcurrentTaskExecutor(executorService);
        factory.getContainerProperties().setListenerTaskExecutor(taskExecutor);

        var kafkaTaskScheduler = new ThreadPoolTaskScheduler();
        kafkaTaskScheduler.setPoolSize(1);
        kafkaTaskScheduler.setThreadNamePrefix("kafka-scheduler-");
        kafkaTaskScheduler.setThreadFactory(ThreadFactoryBuilder.builder()
                                                                .daemon(true)
                                                                .nameFormat("Kafka-Task-Scheduler-%d")
                                                                .build());
        kafkaTaskScheduler.initialize();
        factory.getContainerProperties().setScheduler(kafkaTaskScheduler);

        return factory;
    }
}
