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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.transactions.intrabank;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateRepository;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.ConfigurableEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateEventStreamConfiguration;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.TransactionId;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.transactions.intrabank.events.IntraBankMoneyTransferEvent;
import lombok.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateInstanceFactory.reflectionBasedAggregateRootFactory;

@Component
public class IntraBankMoneyTransfers {
    public static final AggregateType AGGREGATE_TYPE = AggregateType.of("IntraBankMoneyTransfer");

    private final ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration>                       eventStore;
    private final StatefulAggregateRepository<TransactionId, IntraBankMoneyTransferEvent, IntraBankMoneyTransfer> repository;

    public IntraBankMoneyTransfers(@NonNull ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore) {
        this.eventStore = eventStore;
        repository = StatefulAggregateRepository.from(eventStore,
                                                      AGGREGATE_TYPE,
                                                      reflectionBasedAggregateRootFactory(),
                                                      IntraBankMoneyTransfer.class);
    }

    public Optional<IntraBankMoneyTransfer> findTransfer(@NonNull TransactionId transactionId) {
        return repository.tryLoad(transactionId);
    }

    public IntraBankMoneyTransfer getTransfer(@NonNull TransactionId transactionId) {
        return repository.load(transactionId);
    }

    public void requestNewTransfer(@NonNull IntraBankMoneyTransfer transfer) {
        repository.save(transfer);
    }
}
