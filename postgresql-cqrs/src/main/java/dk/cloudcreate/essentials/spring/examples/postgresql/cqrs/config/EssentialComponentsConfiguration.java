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

import dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql.PostgresqlFencedLockManager;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.EventStoreSqlLogger;
import dk.cloudcreate.essentials.components.foundation.Lifecycle;
import dk.cloudcreate.essentials.components.foundation.fencedlock.FencedLockManager;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.messaging.queue.DurableQueues;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWorkFactory;
import dk.cloudcreate.essentials.components.foundation.transaction.jdbi.*;
import dk.cloudcreate.essentials.components.queue.postgresql.PostgresqlDurableQueues;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.springframework.beans.BeansException;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.context.event.*;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;
import java.time.Duration;

@Configuration
@Slf4j
public class EssentialComponentsConfiguration implements ApplicationListener<ApplicationContextEvent>, ApplicationContextAware {
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Essential Jackson module which adds support for serializing and deserializing any Essentials types (note: Map keys still needs to be explicitly defined - see doc)
     *
     * @return the Essential Jackson module which adds support for serializing and deserializing any Essentials types
     */
    @Bean
    public com.fasterxml.jackson.databind.Module essentialJacksonModule() {
        return new EssentialTypesJacksonModule();
    }

    /**
     * {@link Jdbi} is the JDBC API used by the {@link PostgresqlEventStore}, {@link PostgresqlFencedLockManager} and {@link PostgresqlDurableQueues}
     *
     * @param dataSource the Spring managed datasource
     * @return the {@link Jdbi} instance
     */
    @Bean
    public Jdbi jdbi(DataSource dataSource) {
        var jdbi = Jdbi.create(new TransactionAwareDataSourceProxy(dataSource));
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new EventStoreSqlLogger());
        return jdbi;
    }

    /**
     * The {@link PostgresqlFencedLockManager} that coordinates distributed locks
     *
     * @param jdbi              the jbdi instance
     * @param unitOfWorkFactory the {@link UnitOfWorkFactory}
     * @return The {@link PostgresqlFencedLockManager}
     */
    @Bean
    public FencedLockManager fencedLockManager(Jdbi jdbi, HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory) {
        return PostgresqlFencedLockManager.builder()
                                          .setJdbi(jdbi)
                                          .setUnitOfWorkFactory(unitOfWorkFactory)
                                          .setLockTimeOut(Duration.ofSeconds(3))
                                          .setLockConfirmationInterval(Duration.ofSeconds(1))
                                          .buildAndStart();
    }

    /**
     * The {@link PostgresqlDurableQueues} that handles messaging and supports the {@link Inboxes}/{@link Outboxes} implementations
     *
     * @param unitOfWorkFactory the {@link UnitOfWorkFactory}
     * @return the {@link PostgresqlDurableQueues}
     */
    @Bean
    public DurableQueues durableQueues(HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory) {
        return new PostgresqlDurableQueues(unitOfWorkFactory);
    }

    /**
     * The {@link Inboxes} instance using the provided {@link DurableQueues} implementation for message durability and retry
     *
     * @param durableQueues     the {@link DurableQueues} implementation responsible for message durability and retry
     * @param fencedLockManager the distributed locks manager for controlling message consumption across different nodes
     * @return the {@link Inboxes} instance using the provided {@link DurableQueues} implementation for message durability and retry
     */
    @Bean
    public Inboxes inboxes(DurableQueues durableQueues, FencedLockManager fencedLockManager) {
        return Inboxes.durableQueueBasedInboxes(durableQueues,
                                                fencedLockManager);
    }

    /**
     * The {@link Outboxes} instance using the provided {@link DurableQueues} implementation for message durability and retry
     *
     * @param durableQueues     the {@link DurableQueues} implementation responsible for message durability and retry
     * @param fencedLockManager the distributed locks manager for controlling message consumption across different nodes
     * @return the {@link Outboxes} instance using the provided {@link DurableQueues} implementation for message durability and retry
     */
    @Bean
    public Outboxes outboxes(DurableQueues durableQueues, FencedLockManager fencedLockManager) {
        return Outboxes.durableQueueBasedOutboxes(durableQueues,
                                                  fencedLockManager);
    }

    /**
     * Callback to ensure Essentials components implementing {@link Lifecycle} are started
     *
     * @param event
     */
    @Override
    public void onApplicationEvent(ApplicationContextEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            applicationContext.getBeansOfType(Lifecycle.class).forEach((beanName, lifecycleBean) -> {
                log.info("Start {} bean '{}' of type '{}'", Lifecycle.class.getSimpleName(), beanName, lifecycleBean.getClass().getName());
                lifecycleBean.start();
            });
        } else if (event instanceof ContextStoppedEvent) {
            applicationContext.getBeansOfType(Lifecycle.class).forEach((beanName, lifecycleBean) -> {
                log.info("Stopping {} bean '{}' of type '{}'", Lifecycle.class.getSimpleName(), beanName, lifecycleBean.getClass().getName());
                lifecycleBean.stop();
            });
        }
    }
}
