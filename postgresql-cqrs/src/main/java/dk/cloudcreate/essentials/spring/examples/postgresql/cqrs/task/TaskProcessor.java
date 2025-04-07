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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.processor.*;
import dk.cloudcreate.essentials.components.foundation.messaging.MessageHandler;
import dk.cloudcreate.essentials.reactive.command.CmdHandler;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task.commands.*;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task.domain.Task;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task.domain.events.TaskCreated;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.Objects.nonNull;

@Slf4j
@Service
public class TaskProcessor extends InTransactionEventProcessor {

    private final TaskEventStoreRepository taskEventStoreRepository;

    protected TaskProcessor(TaskEventStoreRepository taskEventStoreRepository,
                            EventProcessorDependencies eventProcessorDependencies) {
        super(eventProcessorDependencies, true);
        this.taskEventStoreRepository = taskEventStoreRepository;
    }

    public TaskEventStoreRepository getTaskEventStoreRepository() {
        return taskEventStoreRepository;
    }

    @Override
    public String getProcessorName() {
        return "TaskProcessor";
    }

    @Override
    protected List<AggregateType> reactsToEventsRelatedToAggregateTypes() {
        return List.of(TaskEventStoreRepository.AGGREGATE_TYPE);
    }

    @CmdHandler
    public void handle(CreateTask cmd) {
        log.info("Creating task with command '{}'", cmd);
        taskEventStoreRepository.createTask(cmd.taskId(), cmd);

    }

    @CmdHandler
    public void handle(AddComment cmd) {
        log.info("Adding comment '{}'", cmd);
        Task task = taskEventStoreRepository.findTask(cmd.taskId()).orElseThrow();
        task.addComment(cmd);
    }

    @MessageHandler
    void handle(TaskCreated event) {
        if (nonNull(event.getComment())) {
            log.info("Task '{}' contains comment adding comment command", event);
            commandBus.send(new AddComment(event.getTaskId(), event.getComment(), event.getCreatedAt()));
        }
    }
}
