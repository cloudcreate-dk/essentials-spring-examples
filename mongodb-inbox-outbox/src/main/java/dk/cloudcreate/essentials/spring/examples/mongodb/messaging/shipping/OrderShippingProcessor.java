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

package dk.cloudcreate.essentials.spring.examples.mongodb.messaging.shipping;

import dk.cloudcreate.essentials.reactive.*;
import dk.cloudcreate.essentials.reactive.command.AnnotatedCommandHandler;
import dk.cloudcreate.essentials.spring.examples.mongodb.messaging.shipping.commands.*;
import dk.cloudcreate.essentials.spring.examples.mongodb.messaging.shipping.domain.*;
import dk.cloudcreate.essentials.spring.examples.mongodb.messaging.shipping.domain.events.*;
import lombok.NonNull;
import org.slf4j.*;
import org.springframework.stereotype.Service;

@Service
public class OrderShippingProcessor extends AnnotatedCommandHandler {
    private static Logger log = LoggerFactory.getLogger(OrderShippingProcessor.class);

    private final ShippingOrders   shippingOrders;
    private final EventBus eventBus;

    public OrderShippingProcessor(@NonNull ShippingOrders shippingOrders,
                                  @NonNull EventBus eventBus) {
        this.shippingOrders = shippingOrders;
        this.eventBus = eventBus;
    }

    // Automatically runs in a transaction as it's forwarded by the DurableLocalCommandBus
    @Handler
    void handle(RegisterShippingOrder cmd) {
        var existingOrder = shippingOrders.findOrder(cmd.orderId);
        if (existingOrder.isEmpty()) {
            log.debug("===> Requesting New ShippingOrder '{}'", cmd.orderId);
            shippingOrders.registerNewOrder(new ShippingOrder(cmd));
            eventBus.publish(new ShippingOrderRegistered(cmd));
        }
    }

    // Automatically runs in a transaction as it's forwarded by the Inbox
    @Handler
    void handle(ShipOrder cmd) {
        log.debug("===> Initiating Shipping of Order '{}'", cmd.orderId);
        var existingOrder = shippingOrders.getOrder(cmd.orderId);
        if (existingOrder.markOrderAsShipped()) {
            eventBus.publish(new OrderShipped(cmd.orderId));
        }
    }
}
