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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task.domain;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.EventHandler;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.modern.AggregateRoot;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task.commands.AddComment;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task.commands.CreateTask;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task.domain.events.CommentAdded;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task.domain.events.TaskCreated;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.task.domain.events.TaskEvent;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@ToString
public class Task extends AggregateRoot<TaskId, TaskEvent, Task> {

    private Set<Comment> comments;

    public Task(TaskId aggregateId) {
        super(aggregateId);
    }

    public Task(TaskId aggregateId, CreateTask cmd) {
        super(aggregateId);
        apply(new TaskCreated(aggregateId,
                cmd.comment(),
                LocalDateTime.now()
        ));
    }

    public void addComment(AddComment cmd) {
        Comment comment = new Comment(cmd.taskId(), cmd.content(), cmd.createdAt());
        if (!comments.contains(comment)) {
            apply(new CommentAdded(cmd.taskId(), cmd.content(), cmd.createdAt()));
        }
    }

    public Set<Comment> getComments() {
        return comments;
    }

    @EventHandler
    private void on(TaskCreated event) {
        comments = new HashSet<>();
    }

    @EventHandler
    private void on(CommentAdded event) {
        comments.add(new Comment(event.getTaskId(), event.getContent(), event.getCreatedAt()));
    }
}
