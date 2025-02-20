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
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateEventStreamConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor.*;
import dk.cloudcreate.essentials.components.foundation.messaging.*;
import dk.cloudcreate.essentials.components.foundation.reactive.command.*;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;

@org.springframework.boot.test.context.TestConfiguration
public class TestConfiguration {
    /**
     * Override the {@link RedeliveryPolicy} used by the {@link DurableLocalCommandBus} for integration tests only
     *
     * @return The {@link RedeliveryPolicy} used for {@link DurableLocalCommandBusBuilder#setCommandQueueRedeliveryPolicy(RedeliveryPolicy)}
     */
    @Bean
    @Primary
    RedeliveryPolicy overrideDurableLocalCommandBusRedeliveryPolicy() {
        return RedeliveryPolicy.exponentialBackoff()
                               .setInitialRedeliveryDelay(Duration.ofMillis(200))
                               .setFollowupRedeliveryDelay(Duration.ofMillis(200))
                               .setFollowupRedeliveryDelayMultiplier(1.1d)
                               .setMaximumFollowupRedeliveryDelayThreshold(Duration.ofSeconds(3))
                               .setMaximumNumberOfRedeliveries(3)
                               .setDeliveryErrorHandler(
                                       MessageDeliveryErrorHandler.stopRedeliveryOn(
                                               ConstraintViolationException.class,
                                               HttpClientErrorException.BadRequest.class))
                               .build();
    }

    /**
     * {@link EventProcessor} tested in {@link EventProcessorIT}
     */
    @Bean
    EventProcessorIT.TestOrderEventProcessor testOrderEventProcessor(EventProcessorDependencies eventProcessorDependencies,
                                                                     ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore) {
        return new EventProcessorIT.TestOrderEventProcessor(eventProcessorDependencies, eventStore);
    }
}
