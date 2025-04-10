/*
 * Copyright 2021-2025 the original author or authors.
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

package dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping;

import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.messaging.*;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.reactive.Handler;
import dk.cloudcreate.essentials.reactive.command.*;
import dk.cloudcreate.essentials.shared.time.StopWatch;
import dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping.commands.*;
import dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping.domain.ShippingOrders;
import lombok.Getter;
import org.slf4j.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoadOrderShippingProcessor extends AnnotatedCommandHandler implements Lifecycle {

    private static final Logger         log = LoggerFactory.getLogger(LoadOrderShippingProcessor.class);
    private final        Inboxes        inboxes;
    private final        CommandBus     commandBus;
    private final        ShippingOrders shippingOrders;
    @Getter
    private              Inbox          inbox;

    @Getter
    private AtomicInteger receivedRecreateShippingOrderView = new AtomicInteger(0);

    private volatile boolean started = false;

    public LoadOrderShippingProcessor(Inboxes inboxes,
                                      CommandBus commandBus,
                                      ShippingOrders shippingOrders) {
        this.inboxes = inboxes;
        this.commandBus = commandBus;
        this.shippingOrders = shippingOrders;
    }

    @Handler
    public void handle(RecreateShippingOrderViews cmd) {
        StopWatch     stopWatch = StopWatch.start();
        AtomicInteger count     = new AtomicInteger(0);
        log.info("Received RecreateShippingOrderViews '{}'", cmd);
        var ids = shippingOrders.findAllOrderIds();
        ids.forEach(orderId -> {
            //log.debug("Publishing order id '{}'", orderId);
            count.getAndIncrement();
            publishToInbox(new RecreateShippingOrderView(OrderId.of(orderId)));
        });

        log.info("Took '{}' to publish '{}' shipping orders count '{}'", stopWatch.stop(), ids.size(), count.get());
    }

    public void publishToInbox(RecreateShippingOrderView cmd) {
        inbox.addMessageReceived(cmd);
    }

    @Handler
    public void handle(RecreateShippingOrderView cmd) {
        log.debug("---------------------> Received RecreateShippingOrderView '{}' with id '{}'", cmd, cmd.id());
        receivedRecreateShippingOrderView.getAndIncrement();
    }

    @Override
    public void start() {
        if (!started) {
            log.info("Starting LoadOrderShippingProcessor with {} threads", 20);
            started = true;
            inbox = inboxes.getOrCreateInbox(InboxConfig.builder()
                                                        .inboxName(InboxName.of("load-test"))
                                                        .redeliveryPolicy(RedeliveryPolicy.fixedBackoff()
                                                                                          .setRedeliveryDelay(Duration.ZERO)
                                                                                          .setDeliveryErrorHandler(new MessageDeliveryErrorHandler.NeverRetry())
                                                                                          .setMaximumNumberOfRedeliveries(0)
                                                                                          .build())
                                                        .messageConsumptionMode(MessageConsumptionMode.SingleGlobalConsumer)
                                                        .numberOfParallelMessageConsumers(20)
                                                        .build(),
                                             commandBus
                                            );
        }
    }

    @Override
    public void stop() {
        if (started) {
            started = false;
            inbox.stopConsuming();
        }
    }

    @Override
    public boolean isStarted() {
        return false;
    }
}
