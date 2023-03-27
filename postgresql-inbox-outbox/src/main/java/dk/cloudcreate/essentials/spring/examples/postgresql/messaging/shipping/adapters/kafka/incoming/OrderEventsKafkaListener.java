/*
 * Copyright 2021-2023 the original author or authors.
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

package dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping.adapters.kafka.incoming;

import dk.cloudcreate.essentials.reactive.command.CommandBus;
import dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping.commands.ShipOrder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class OrderEventsKafkaListener {
    public static final String ORDER_EVENTS_TOPIC_NAME = "order-events";

    private final CommandBus commandBus;

    public OrderEventsKafkaListener(@NonNull CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @KafkaListener(topics = ORDER_EVENTS_TOPIC_NAME, groupId = "order-processing", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handle(OrderEvent event) {
        if (event instanceof OrderAccepted) {
            log.info("*** Since Order '{}' is Accepted we can start Shipping the Order. Forwarding {} to CommandBus",
                     event.getId(),
                     ShipOrder.class.getSimpleName());
            commandBus.sendAndDontWait(new ShipOrder(event.getId()));
        } else {
            log.debug("Ignoring {}: {}", event.getClass().getSimpleName(), event);
        }
    }
}
