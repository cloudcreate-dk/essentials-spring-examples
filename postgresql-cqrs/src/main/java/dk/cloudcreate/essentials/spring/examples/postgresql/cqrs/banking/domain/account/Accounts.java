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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.account;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateRepository;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.ConfigurableEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateEventStreamConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.account.events.AccountEvent;
import dk.cloudcreate.essentials.types.LongRange;
import lombok.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateInstanceFactory.reflectionBasedAggregateRootFactory;

@Component
public class Accounts {
    public static final AggregateType                                                             AGGREGATE_TYPE = AggregateType.of("Accounts");
    private final       ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore;
    private final       StatefulAggregateRepository<AccountId, AccountEvent, Account>             repository;

    public Accounts(@NonNull ConfigurableEventStore<SeparateTablePerAggregateEventStreamConfiguration> eventStore) {
        this.eventStore = eventStore;
        repository = StatefulAggregateRepository.from(eventStore,
                                                      AGGREGATE_TYPE,
                                                      reflectionBasedAggregateRootFactory(),
                                                      Account.class);
    }

    public boolean hasAccount(@NonNull AccountId accountId) {
        return eventStore.fetchStream(AGGREGATE_TYPE,
                                      accountId,
                                      LongRange.only(EventOrder.FIRST_EVENT_ORDER.longValue()))
                         .isPresent();
    }

    public boolean isAccountMissing(@NonNull AccountId accountId) {
        return !hasAccount(accountId);
    }

    public Optional<Account> findAccount(@NonNull AccountId accountId) {
        return repository.tryLoad(accountId);
    }

    public Account getAccount(@NonNull AccountId accountId) {
        return repository.load(accountId);
    }

    public Account openNewAccount(@NonNull AccountId accountId,
                                  @NonNull AccountNumber accountNumber) {
        var account = new Account(accountId, accountNumber);
        return repository.save(account);
    }
}
