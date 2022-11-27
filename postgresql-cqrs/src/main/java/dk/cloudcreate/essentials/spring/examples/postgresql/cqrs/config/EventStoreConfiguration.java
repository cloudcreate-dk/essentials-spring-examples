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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.command.UnitOfWorkControllingCommandBusInterceptor;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.gap.PostgresqlEventStreamGapHandler;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.spring.SpringTransactionAwareEventStoreUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventTypeOrName;
import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLockManager;
import dk.cloudcreate.essentials.components.foundation.transaction.*;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import dk.cloudcreate.essentials.reactive.LocalEventBus;
import dk.cloudcreate.essentials.reactive.command.LocalCommandBus;
import dk.cloudcreate.essentials.reactive.spring.ReactiveHandlersBeanPostProcessor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.Optional;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypeEventStreamConfigurationFactory.standardSingleTenantConfigurationUsingJackson;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

@Slf4j
@Configuration
public class EventStoreConfiguration {
    @Bean
    public ReactiveHandlersBeanPostProcessor reactiveHandlersBeanPostProcessor() {
        return new ReactiveHandlersBeanPostProcessor();
    }

    /**
     * The {@link LocalEventBus} responsible for publishing {@link PersistedEvents} locally - this is used by the {@link EventStoreLocalEventBus}
     *
     * @return the {@link LocalEventBus} responsible for publishing {@link PersistedEvents} locally
     */
    @Bean
    public LocalEventBus<PersistedEvents> persistedEventsEventBus() {
        return new LocalEventBus<>("PersistedEvents-EventBus",
                                   3,
                                   (failingSubscriber, event, exception) -> log.error(msg("Error for '{}' handling {}", failingSubscriber, event), exception));
    }

    @Bean
    public LocalCommandBus localCommandBus(UnitOfWorkFactory<? extends UnitOfWork> unitOfWorkFactory) {
        return new LocalCommandBus(new UnitOfWorkControllingCommandBusInterceptor(unitOfWorkFactory));
    }

    /**
     * Mapper from the raw Java Event's to {@link PersistableEvent}<br>
     * The {@link PersistableEventMapper} adds additional information such as:
     * event-id, event-type, event-order, event-timestamp, event-meta-data, correlation-id, tenant-id for each persisted event at a cross-functional level.
     *
     * @return the {@link PersistableEventMapper} to use for all Events
     */
    @Bean
    public PersistableEventMapper persistableEventMapper() {
        return (aggregateId, aggregateTypeConfiguration, event, eventOrder) ->
                PersistableEvent.builder()
                                .setEvent(event)
                                .setAggregateType(aggregateTypeConfiguration.aggregateType)
                                .setAggregateId(aggregateId)
                                .setEventTypeOrName(EventTypeOrName.with(event.getClass()))
                                .setEventOrder(eventOrder)
                                .build();
    }

    /**
     * Define the {@link EventStoreUnitOfWorkFactory} which is required for the {@link EventStore}
     * in order handle events associated with a given transaction.<br>
     * The {@link SpringTransactionAwareEventStoreUnitOfWorkFactory} supports joining {@link UnitOfWork}'s
     * with the underlying Spring managed Transaction (i.e. supports methods annotated with @Transactional)
     *
     * @param jdbi               the jdbi instance
     * @param transactionManager the Spring Transactional manager as we allow Spring to demarcate the transaction
     * @return The {@link EventStoreUnitOfWorkFactory}
     */
    @Bean
    public EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> eventStoreUnitOfWorkFactory(Jdbi jdbi,
                                                                                                   PlatformTransactionManager transactionManager) {
        return new SpringTransactionAwareEventStoreUnitOfWorkFactory(jdbi, transactionManager);
    }

    @Bean
    public EventStoreSubscriptionManager eventStoreSubscriptionManager(EventStore eventStore, FencedLockManager fencedLockManager, Jdbi jdbi) {
        return EventStoreSubscriptionManager.builder()
                                            .setEventStore(eventStore)
                                            .setEventStorePollingBatchSize(10)
                                            .setEventStorePollingInterval(Duration.ofMillis(100))
                                            .setFencedLockManager(fencedLockManager)
                                            .setSnapshotResumePointsEvery(Duration.ofSeconds(10))
                                            .setDurableSubscriptionRepository(new PostgresqlDurableSubscriptionRepository(jdbi))
                                            .build();
    }

    /**
     * Setup the strategy for how {@link AggregateType} event-streams should be persisted.
     *
     * @param jdbi                   the jdbi instance
     * @param unitOfWorkFactory      the {@link EventStoreUnitOfWorkFactory}
     * @param persistableEventMapper the mapper from the raw Java Event's to {@link PersistableEvent}<br>
     * @param eventStoreObjectMapper {@link ObjectMapper} responsible for serializing/deserializing the raw Java events to and from JSON
     * @return the strategy for how {@link AggregateType} event-streams should be persisted
     */
    @Bean
    public SeparateTablePerAggregateTypePersistenceStrategy eventStorePersistenceStrategy(Jdbi jdbi,
                                                                                          EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> unitOfWorkFactory,
                                                                                          PersistableEventMapper persistableEventMapper,
                                                                                          ObjectMapper eventStoreObjectMapper) {
        return new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                    unitOfWorkFactory,
                                                                    persistableEventMapper,
                                                                    standardSingleTenantConfigurationUsingJackson(eventStoreObjectMapper,
                                                                                                                  IdentifierColumnType.UUID,
                                                                                                                  JSONColumnType.JSONB));
    }

    /**
     * The Local EventBus where the {@link EventStore} publishes persisted event
     *
     * @param persistedEventsEventBus     The {@link LocalEventBus} responsible for publishing {@link PersistedEvents} locally
     * @param eventStoreUnitOfWorkFactory the {@link EventStoreUnitOfWorkFactory} that is required for the {@link EventStore} in order handle events associated with a given transaction
     * @return the {@link EventStoreLocalEventBus}
     */
    @Bean
    public EventStoreLocalEventBus eventStoreLocalEventBus(LocalEventBus<PersistedEvents> persistedEventsEventBus,
                                                           EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> eventStoreUnitOfWorkFactory) {
        return new EventStoreLocalEventBus(persistedEventsEventBus,
                                           eventStoreUnitOfWorkFactory);
    }

    /**
     * The configurable {@link EventStore} that allows us to persist and load Events associated with different {@link AggregateType}øs
     *
     * @param eventStoreUnitOfWorkFactory the {@link EventStoreUnitOfWorkFactory} that is required for the {@link EventStore} in order handle events associated with a given transaction
     * @param persistenceStrategy         the strategy for how {@link AggregateType} event-streams should be persisted.
     * @param eventStoreLocalEventBus     the Local EventBus where the {@link EventStore} publishes persisted event
     * @return the configurable {@link EventStore}
     */
    @Bean
    public ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore(EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> eventStoreUnitOfWorkFactory,
                                                                                                SeparateTablePerAggregateTypePersistenceStrategy persistenceStrategy,
                                                                                                EventStoreLocalEventBus eventStoreLocalEventBus) {
        return new PostgresqlEventStore<>(eventStoreUnitOfWorkFactory,
                                          persistenceStrategy,
                                          Optional.of(eventStoreLocalEventBus),
                                          eventStore -> new PostgresqlEventStreamGapHandler<>(eventStore, eventStoreUnitOfWorkFactory));

    }

    /**
     * {@link ObjectMapper} responsible for serializing/deserializing the raw Java events to and from JSON
     *
     * @return the {@link ObjectMapper} responsible for serializing/deserializing the raw Java events to and from JSON
     */
    @Bean
    public ObjectMapper eventStoreObjectMapper() {
        var objectMapper = JsonMapper.builder()
                                     .disable(MapperFeature.AUTO_DETECT_GETTERS)
                                     .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
                                     .disable(MapperFeature.AUTO_DETECT_SETTERS)
                                     .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                                     .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                     .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                     .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                                     .enable(MapperFeature.AUTO_DETECT_CREATORS)
                                     .enable(MapperFeature.AUTO_DETECT_FIELDS)
                                     .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
                                     .addModule(new Jdk8Module())
                                     .addModule(new JavaTimeModule())
                                     .addModule(new EssentialTypesJacksonModule())
                                     .addModule(new EssentialsImmutableJacksonModule())
                                     .build();

        objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                                               .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                                               .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));
        return objectMapper;
    }
}
