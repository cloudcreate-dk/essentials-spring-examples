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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.shipping.adapters.kafka.outgoing;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor.*;
import dk.cloudcreate.essentials.components.foundation.messaging.MessageHandler;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.OrderedMessage;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.shipping.domain.ShippingOrders;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.shipping.domain.events.OrderShipped;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ShippingEventKafkaPublisher extends EventProcessor {
    public static final String                        SHIPPING_EVENTS_TOPIC_NAME = "shipping-events";
    private final       KafkaTemplate<String, Object> kafkaTemplate;


    public ShippingEventKafkaPublisher(@NonNull EventProcessorDependencies eventProcessorDependencies,
                                       @NonNull KafkaTemplate<String, Object> kafkaTemplate) {
        super(eventProcessorDependencies);
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public String getProcessorName() {
        return "ShippingEventsKafkaPublisher";
    }

    @Override
    protected List<AggregateType> reactsToEventsRelatedToAggregateTypes() {
        return List.of(ShippingOrders.AGGREGATE_TYPE);
    }

    @MessageHandler
    void handle(OrderShipped e, OrderedMessage eventMessage) {
        log.info("*** Received {} for Order '{}' and adding it to the Outbox as a {} message", e.getClass().getSimpleName(), e.orderId, ExternalOrderShipped.class.getSimpleName());
        var externalEvent = new ExternalOrderShipped(e.orderId, eventMessage.getOrder());
        log.info("*** Forwarding {} message to Kafka. Order '{}'", externalEvent.getClass().getSimpleName(), externalEvent.orderId);
        var producerRecord = new ProducerRecord<String, Object>(SHIPPING_EVENTS_TOPIC_NAME,
                                                                externalEvent.orderId.toString(),
                                                                externalEvent);
        kafkaTemplate.send(producerRecord);
        log.info("*** Completed sending event {} to Kafka. Order '{}'", externalEvent.getClass().getSimpleName(), externalEvent.orderId);
    }
}
