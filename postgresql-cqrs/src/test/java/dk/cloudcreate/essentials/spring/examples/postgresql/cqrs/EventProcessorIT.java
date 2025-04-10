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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.ConfigurableEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateEventStreamConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.EventStoreUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.Revision;
import dk.cloudcreate.essentials.components.foundation.messaging.*;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.Inbox;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues;
import dk.cloudcreate.essentials.components.foundation.reactive.command.DurableLocalCommandBus;
import dk.cloudcreate.essentials.components.foundation.types.RandomIdGenerator;
import dk.cloudcreate.essentials.reactive.command.CmdHandler;
import dk.cloudcreate.essentials.types.CharSequenceType;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the EventProcessor using the following scenarios:
 * <ol>
 *   <li>A PlaceOrderCommand causes an OrderPlacedEvent to be persisted.</li>
 *   <li>The OrderPlacedEvent is handled by a @MessageHandler which synchronously sends a ConfirmOrderCommand via getCommandBus().send(...),
 *       whose handler then persists an OrderConfirmedEvent.</li>
 *   <li>A FailingCommand sent via getInbox().sendAndDontWait(...) always fails.</li>
 * </ol>
 */
@SpringBootTest(classes = {Application.class, TestConfiguration.class})
@Testcontainers
@DirtiesContext
public class EventProcessorIT {
    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("test")
            .withPassword("test")
            .withUsername("test");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    }


    @Autowired
    private ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;

    @Autowired
    private DurableLocalCommandBus commandBus;

    @Autowired
    private EventStoreUnitOfWorkFactory<?> unitOfWorkFactory;

    @Autowired
    private DurableQueues durableQueues;

    @Autowired
    private TestOrderEventProcessor testProcessor;


    // -------------------------------------------------------------------------------------------------------------------

    /**
     * Scenario 1 & 2:
     * Sending a PlaceOrderCommand should cause an OrderPlacedEvent to be persisted.
     * Its MessageHandler then synchronously sends a ConfirmOrderCommand which persists an OrderConfirmedEvent.
     */
    @Test
    void testPlaceOrderAndConfirmationFlow() throws Exception {
        var orderId      = OrderId.random();
        var orderDetails = "Test order details";
        var placeCmd     = new PlaceOrderCommand(orderId, orderDetails);

        // Send the command via the command bus
        commandBus.send(placeCmd);

        // Fetch the aggregate event stream for this order
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .until(() -> unitOfWorkFactory.withUnitOfWork(() -> {
                      var streamOpt = eventStore.fetchStream(TestOrderEventProcessor.TEST_ORDERS, orderId);
                      return streamOpt.isPresent() && streamOpt.get().eventList().size() == 2;
                  }));

        // We expect two events: first the OrderPlacedEvent then the OrderConfirmedEvent
        var events = unitOfWorkFactory.withUnitOfWork(() -> eventStore.fetchStream(TestOrderEventProcessor.TEST_ORDERS, orderId).get().eventList());
        assertThat(events).hasSize(2);
        var event0 = events.get(0);
        var event1 = events.get(1);
        assertThat(event0.event().getEventTypeAsJavaClass()).isPresent().isEqualTo(Optional.of(OrderPlacedEvent.class));
        assertThat(event1.event().getEventTypeAsJavaClass()).isPresent().isEqualTo(Optional.of(OrderConfirmedEvent.class));
    }

    /**
     * Scenario 3A:
     * Send a FailingCommandSentUsingAsyncAndDontWaitViaCommandBus (asynchronous, fire-and-forget) via the CommandBus. Its handler always throws an exception.
     */
    @Test
    void testFailingCommandSendViaCommandBus_sendAndDontWait_EventuallyEndsUpInAsADeadLetterMessage() throws Exception {
        var orderId    = OrderId.random();
        var failingCmd = new FailingCommandSentUsingAsyncAndDontWaitViaCommandBus(orderId, "Intentional failure for testing");

        // Verify we don't have any dead-letter messages to begin with
        var queueName = commandBus.getCommandQueueName();
        assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(0);

        // Send the failing command using the command bus (asynchronous, fire-and-forget)
        commandBus.sendAndDontWait(failingCmd);

        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> {
                      assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(1);
                  });

        // Verify we have one dead-letter messages
        var deadLetterMessages = durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 10);
        assertThat(deadLetterMessages).hasSize(1);
        assertThat(deadLetterMessages.get(0).getPayload()).isInstanceOf(FailingCommandSentUsingAsyncAndDontWaitViaCommandBus.class);
    }

    /**
     * Scenario 3B:
     * Send a FailingCommandSentViaInbox via the event processors Inbox. Its handler always throws an exception.
     */
    @Test
    void testFailingCommandSentViaInboxEventuallyEndsUpInAsADeadLetterMessage() throws Exception {
        var orderId    = OrderId.random();
        var failingCmd = new FailingCommandSentViaInbox(orderId, "Intentional failure for testing");

        // Verify we don't have any dead-letter messages to begin with
        var queueName = testProcessor.getInboxForTesting().name().asQueueName();
        assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(0);

        // Send the failing command using the processor's inbox (asynchronous, fire-and-forget)
        testProcessor.getInboxForTesting().addMessageReceived(failingCmd);

        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> {
                      assertThat(durableQueues.getTotalDeadLetterMessagesQueuedFor(queueName)).isEqualTo(1);
                  });

        // Verify we have one dead-letter messages
        var deadLetterMessages = durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 10);
        assertThat(deadLetterMessages).hasSize(1);
        assertThat(deadLetterMessages.get(0).getPayload()).isInstanceOf(FailingCommandSentViaInbox.class);
    }

    // --------------------------
    // Supporting classes for the test
    // --------------------------

    /**
     * OrderId
     */
    public static class OrderId extends CharSequenceType<OrderId> {
        public OrderId(String value) {
            super(value);
        }
        public OrderId(CharSequence value) {
            super(value);
        }

        public static OrderId random() {
            return new OrderId(RandomIdGenerator.generate());
        }

        public static OrderId of(String id) {
            return new OrderId(id);
        }
    }

    // --- Commands ---

    public static class PlaceOrderCommand {
        public final OrderId orderId;
        public final String  orderDetails;

        public PlaceOrderCommand(OrderId orderId, String orderDetails) {
            this.orderId = orderId;
            this.orderDetails = orderDetails;
        }
    }

    public static class ConfirmOrderCommand {
        public final OrderId orderId;

        public ConfirmOrderCommand(OrderId orderId) {
            this.orderId = orderId;
        }
    }

    public static class FailingCommandSentViaInbox {
        public final OrderId orderId;
        public final String  reason;

        public FailingCommandSentViaInbox(OrderId orderId, String reason) {
            this.orderId = orderId;
            this.reason = reason;
        }
    }

    public static class FailingCommandSentUsingAsyncAndDontWaitViaCommandBus {
        public final OrderId orderId;
        public final String  reason;

        public FailingCommandSentUsingAsyncAndDontWaitViaCommandBus(OrderId orderId, String reason) {
            this.orderId = orderId;
            this.reason = reason;
        }
    }

    // --- Events ---

    @Revision(1)
    public static class OrderPlacedEvent {
        public final OrderId orderId;
        public final String  orderDetails;

        public OrderPlacedEvent(OrderId orderId, String orderDetails) {
            this.orderId = orderId;
            this.orderDetails = orderDetails;
        }
    }

    public static class OrderConfirmedEvent {
        public final OrderId orderId;

        public OrderConfirmedEvent(OrderId orderId) {
            this.orderId = orderId;
        }
    }

    // --------------------------
    // Test EventProcessor
    // --------------------------

    /**
     * TestOrderEventProcessor is a simple EventProcessor that:
     * <ul>
     *     <li>Reacts to events for the "TestOrders" aggregate type</li>
     *     <li>Handles PlaceOrderCommand by persisting an OrderPlacedEvent</li>
     *     <li>Handles OrderPlacedEvent (via a @MessageHandler) by sending a ConfirmOrderCommand synchronously</li>
     *     <li>Handles ConfirmOrderCommand by persisting an OrderConfirmedEvent</li>
     *     <li>Handles FailingCommandSentUsingAsyncAndDontWaitViaCommandBus / FailingCommandSentViaInbox by throwing an exception</li>
     * </ul>
     */
    public static class TestOrderEventProcessor extends EventProcessor {
        public static final AggregateType TEST_ORDERS = AggregateType.of("TestOrders");

        private final ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;

        public TestOrderEventProcessor(EventProcessorDependencies eventProcessorDependencies,
                                       ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore) {
            super(eventProcessorDependencies);
            this.eventStore = eventStore;
            eventStore.addAggregateEventStreamConfiguration(TestOrderEventProcessor.TEST_ORDERS,
                                                            AggregateIdSerializer.serializerFor(OrderId.class));
        }

        @Override
        public String getProcessorName() {
            return "TestOrderEventProcessor";
        }

        @Override
        protected List<AggregateType> reactsToEventsRelatedToAggregateTypes() {
            return List.of(TEST_ORDERS);
        }


        public Inbox getInboxForTesting() {
            return super.getInbox();
        }

        @Override
        protected RedeliveryPolicy getInboxRedeliveryPolicy() {
            return RedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 3);
        }

        // ----- Command Handlers -----

        @CmdHandler
        public void handle(PlaceOrderCommand cmd) {
            // Start a new event stream for this order
            var event = new OrderPlacedEvent(cmd.orderId, cmd.orderDetails);
            eventStore.startStream(TEST_ORDERS, cmd.orderId, List.of(event));
        }

        @CmdHandler
        public void handle(ConfirmOrderCommand cmd) {
            // Append the confirmation event to the existing stream
            var event = new OrderConfirmedEvent(cmd.orderId);
            eventStore.appendToStream(TEST_ORDERS, cmd.orderId, event);
        }


        @CmdHandler
        public void handle(FailingCommandSentUsingAsyncAndDontWaitViaCommandBus cmd) {
            System.out.println("*** Handling FailingCommand: " + cmd.reason);
            throw new RuntimeException(cmd.reason);
        }


        // ----- Message Handler -----

        @MessageHandler
        public void handle(FailingCommandSentViaInbox cmd) {
            System.out.println("*** Handling FailingCommand: " + cmd.reason);
            throw new RuntimeException(cmd.reason);
        }

        /**
         * When an OrderPlacedEvent is processed, send a ConfirmOrderCommand synchronously.
         */
        @MessageHandler
        public void onOrderPlaced(OrderPlacedEvent event) {
            getCommandBus().send(new ConfirmOrderCommand(event.orderId));
        }
    }
}