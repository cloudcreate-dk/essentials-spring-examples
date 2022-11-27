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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.shipping.adapters.kafka.incoming;

import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.reactive.command.LocalCommandBus;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.shipping.commands.ShipOrder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@Slf4j
public class OrderEventsKafkaListener {
    public static final String ORDER_EVENTS_TOPIC_NAME = "order-events";

    private final Inbox<ShipOrder> orderEventsInbox;

    public OrderEventsKafkaListener(@NonNull Inboxes inboxes,
                                    @NonNull LocalCommandBus commandBus) {
        // Setup the Inbox to forward messages to the commandBus
        orderEventsInbox = inboxes.getOrCreateInbox(InboxConfig.builder()
                                                               .inboxName(InboxName.of("OrderService:OrderEvents"))
                                                               .redeliveryPolicy(RedeliveryPolicy.fixedBackoff(Duration.ofMillis(100),
                                                                                                               10))
                                                               .messageConsumptionMode(MessageConsumptionMode.SingleGlobalConsumer)
                                                               .numberOfParallelMessageConsumers(5)
                                                               .build(),
                                                    commandBus);
    }

    @KafkaListener(topics = ORDER_EVENTS_TOPIC_NAME, groupId = "order-processing", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handle(OrderEvent event) {
        if (event instanceof OrderAccepted) {
            log.info("*** Since Order '{}' is Accepted we can start Shipping the Order. Forwarding {} to CommandBus",
                     event.getId(),
                     ShipOrder.class.getSimpleName());
            // Since the method is annotated using @Transactional then orderEventsInbox.addMessageReceived can be called without an explicit unit of work
            // otherwise it would have to be wrapped in a unitOfWorkFactory.usingUnitOfWork(() -> orderEventsInbox.addMessageReceived(new ShipOrder(event.getId())));
            orderEventsInbox.addMessageReceived(new ShipOrder(event.getId()));
        } else {
            log.debug("Ignoring {}: {}", event.getClass().getSimpleName(), event);
        }
    }
}
