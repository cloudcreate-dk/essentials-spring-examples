/*
 * Copyright 2021-2024 the original author or authors.
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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.ConfigurableEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateEventStreamConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.foundation.reactive.command.DurableLocalCommandBus;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.TestApplication;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task.commands.CreateTask;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task.domain.TaskId;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task.domain.events.*;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;


@Slf4j
@Testcontainers
@DirtiesContext
@SpringBootTest(classes = TestApplication.class)
public class TaskProcessorIT {

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("test")
            .withPassword("test")
            .withUsername("test");

    @Autowired
    private TaskProcessor                                                             eventProcessor;
    @Autowired
    private ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    }

    @Autowired
    private   DurableLocalCommandBus                                      commandBus;
    @Autowired
    protected EventStoreEventBus                                          eventStoreEventBus;
    @Autowired
    protected EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> unitOfWorkFactory;

    protected List<PersistedEvents> collectedEvents = new ArrayList<>();

    protected void collectPersistedEvents() {
        eventStoreEventBus.addSyncSubscriber(event -> {
            if (event instanceof PersistedEvents persistedEvents) {
                if (persistedEvents.commitStage == CommitStage.BeforeCommit && !persistedEvents.events.isEmpty()) {
                    log.info("Collected persisted events : {}", persistedEvents.events);
                    collectedEvents.add(persistedEvents);
                }
            }
        });
    }

    /**
     * This test involves creating Task aggregate with CreateTask command and with a second command AddComment
     * add comment to the just created Task aggregate using the same unit of work. This flow is impl. in TaskProcessor
     */
    @Test
    public void create_task_and_comment_in_same_unit_of_work() {
        collectPersistedEvents();
        Awaitility.waitAtMost(Duration.ofMillis(300)).until(() -> eventProcessor.isActive());

        TaskId taskId  = TaskId.random();
        String comment = "This is a good comment!";

        CreateTask cmd = new CreateTask(taskId, comment);
        System.out.println("Sending Command ---->");
        commandBus.send(cmd);
        System.out.println("Command Sent <----");

        assertThat(collectedEvents).hasSize(2);
        assertThat(collectedEvents.get(0).events).hasSize(1);
        assertThat(collectedEvents.get(1).events).hasSize(1);
        var taskCreated = collectedEvents.get(0).events.get(0).event().deserialize();
        assertThat(taskCreated).isExactlyInstanceOf(TaskCreated.class);
        assertThat(collectedEvents.get(0).events.get(0).aggregateId()).isEqualTo(taskId);
        var commentAdded = collectedEvents.get(1).events.get(0).event().deserialize();
        assertThat(commentAdded).isExactlyInstanceOf(CommentAdded.class);
        assertThat(collectedEvents.get(1).events.get(0).aggregateId()).isEqualTo(taskId);

        var task = unitOfWorkFactory.withUnitOfWork(() -> eventProcessor.getTaskEventStoreRepository().getTask(taskId));
        assertThat(task.getComments()).hasSize(1);
    }
}
