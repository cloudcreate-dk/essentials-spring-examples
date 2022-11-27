/*
 * Copyright 2021-2022 the original author or authors.
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

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;
import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.shipping.domain.ShippingOrders;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.shipping.domain.events.OrderShipped;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@Slf4j
public class ShippingEventKafkaPublisher {
    public static final String SHIPPING_EVENTS_TOPIC_NAME = "shipping-events";

    private final Outbox<ExternalOrderShippingEvent> kafkaOutbox;

    public ShippingEventKafkaPublisher(@NonNull Outboxes outboxes,
                                       @NonNull EventStoreSubscriptionManager eventStoreSubscriptionManager,
                                       @NonNull KafkaTemplate<String, Object> kafkaTemplate,
                                       @NonNull UnitOfWorkFactory<?> unitOfWorkFactory) {
        // Setup the outbox to forward to Kafka
        kafkaOutbox = outboxes.getOrCreateOutbox(OutboxConfig.builder()
                                                             .setOutboxName(OutboxName.of("ShippingOrder:KafkaShippingEvents"))
                                                             .setRedeliveryPolicy(RedeliveryPolicy.fixedBackoff(Duration.ofMillis(100), 10))
                                                             .setMessageConsumptionMode(MessageConsumptionMode.SingleGlobalConsumer)
                                                             .setNumberOfParallelMessageConsumers(1)
                                                             .build(),
                                                 e -> {
                                                     log.info("*** Forwarding Outbox {} message to Kafka. Order '{}'", e.getClass().getSimpleName(), e.orderId);
                                                     var producerRecord = new ProducerRecord<String, Object>(SHIPPING_EVENTS_TOPIC_NAME,
                                                                                                             e.orderId.toString(),
                                                                                                             e);
                                                     kafkaTemplate.send(producerRecord);
                                                     log.info("*** Completed sending event {} to Kafka. Order '{}'", e.getClass().getSimpleName(), e.orderId);
                                                 });

        // Subscribe ShippingOrder events and add only OrderShipped event to the Outbox as an ExternalOrderShipped event
        eventStoreSubscriptionManager.subscribeToAggregateEventsAsynchronously(SubscriberId.of("ShippingEventKafkaPublisher-ShippingEvents"),
                                                                               ShippingOrders.AGGREGATE_TYPE,
                                                                               GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                                               Optional.empty(),
                                                                               new PatternMatchingPersistedEventHandler() {
                                                                                   @Override
                                                                                   protected void handleUnmatchedEvent(PersistedEvent event) {
                                                                                       // Ignore any events not explicitly handled - the original logic throws an exception to notify subscribers of unhandled events
                                                                                   }

                                                                                   @SubscriptionEventHandler
                                                                                   void handle(OrderShipped e) {
                                                                                       log.info("*** Received {} for Order '{}' and adding it to the Outbox as a {} message", e.getClass().getSimpleName(), e.orderId, ExternalOrderShipped.class.getSimpleName());
                                                                                       unitOfWorkFactory.usingUnitOfWork(() -> kafkaOutbox.sendMessage(new ExternalOrderShipped(e.orderId)));
                                                                                   }
                                                                               });
    }


}
