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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.shipping.domain;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateRepository;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.ConfigurableEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateEventStreamConfiguration;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.shipping.OrderId;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.shipping.domain.events.ShippingEvent;
import lombok.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateInstanceFactory.reflectionBasedAggregateRootFactory;

@Component
public class ShippingOrders {
    public static final AggregateType AGGREGATE_TYPE = AggregateType.of("ShippingOrders");

    private final ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;
    private final StatefulAggregateRepository<OrderId, ShippingEvent, ShippingOrder>        repository;

    public ShippingOrders(@NonNull ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore) {
        this.eventStore = eventStore;
        repository = StatefulAggregateRepository.from(eventStore,
                                                      AGGREGATE_TYPE,
                                                      reflectionBasedAggregateRootFactory(),
                                                      ShippingOrder.class);
    }

    public Optional<ShippingOrder> findOrder(@NonNull OrderId orderId) {
        return repository.tryLoad(orderId);
    }

    public ShippingOrder getOrder(@NonNull OrderId orderId) {
        return repository.load(orderId);
    }

    public void registerNewOrder(@NonNull ShippingOrder order) {
        repository.save(order);
    }
}
