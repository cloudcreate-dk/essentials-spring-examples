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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.foundation.messaging.RedeliveryPolicy;
import dk.cloudcreate.essentials.components.foundation.messaging.eip.store_and_forward.*;
import dk.cloudcreate.essentials.components.foundation.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.foundation.types.SubscriberId;
import dk.cloudcreate.essentials.reactive.*;
import dk.cloudcreate.essentials.reactive.command.*;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.commands.RequestIntraBankMoneyTransfer;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.account.*;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.account.events.*;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.transactions.TransactionException;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.transactions.intrabank.*;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.transactions.intrabank.events.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

@Service
@Slf4j
public class TransferMoneyProcessor extends AnnotatedCommandHandler {
    private final Accounts                                                    accounts;
    private final IntraBankMoneyTransfers                                     intraBankMoneyTransfers;
    private final LocalCommandBus                                             commandBus;
    private final EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> unitOfWorkFactory;
    private final Outbox<Object>                                              moneyTransferEventsOutbox;

    public TransferMoneyProcessor(@NonNull Accounts accounts,
                                  @NonNull IntraBankMoneyTransfers intraBankMoneyTransfers,
                                  @NonNull LocalCommandBus commandBus,
                                  @NonNull EventStoreSubscriptionManager eventStoreSubscriptionManager,
                                  @NonNull EventStoreUnitOfWorkFactory<? extends EventStoreUnitOfWork> unitOfWorkFactory,
                                  @NonNull Outboxes outboxes) {
        this.accounts = accounts;
        this.intraBankMoneyTransfers = intraBankMoneyTransfers;
        this.commandBus = commandBus;
        this.unitOfWorkFactory = unitOfWorkFactory;
        this.moneyTransferEventsOutbox = outboxes.getOrCreateForwardingOutbox(OutboxConfig.builder()
                                                                                          .setOutboxName(OutboxName.of("MoneyTransfer - Lifecycle"))
                                                                                          .setMessageConsumptionMode(MessageConsumptionMode.CompetingConsumers)
                                                                                          .setNumberOfParallelMessageConsumers(1)
                                                                                          .setRedeliveryPolicy(RedeliveryPolicy.fixedBackoff(Duration.ofMillis(500), 10))
                                                                                          .build(),
                                                                              new MoneyTransferLifecycleHandler());

        eventStoreSubscriptionManager.subscribeToAggregateEventsInTransaction(SubscriberId.of("TransferMoneyProcessor-AccountEvents"),
                                                                              Accounts.AGGREGATE_TYPE,
                                                                              Optional.empty(),
                                                                              new PatternMatchingTransactionalPersistedEventHandler() {
                                                                                  @SubscriptionEventHandler
                                                                                  void handle(AccountDeposited e, UnitOfWork unitOfWork) {
                                                                                      log.debug("Forwarding {}", e);
                                                                                      moneyTransferEventsOutbox.sendMessage(e);
                                                                                  }

                                                                                  @SubscriptionEventHandler
                                                                                  void handle(AccountWithdrawn e, UnitOfWork unitOfWork) {
                                                                                      log.debug("Forwarding {}", e);
                                                                                      moneyTransferEventsOutbox.sendMessage(e);
                                                                                  }

                                                                                  @SubscriptionEventHandler
                                                                                  void handle(AccountOpened e, UnitOfWork unitOfWork) {
                                                                                      // Ignore
                                                                                  }
                                                                              });

        eventStoreSubscriptionManager.subscribeToAggregateEventsInTransaction(SubscriberId.of("TransferMoneyProcessor-IntraBankMoneyTransferEvent"),
                                                                              IntraBankMoneyTransfers.AGGREGATE_TYPE,
                                                                              Optional.empty(),
                                                                              new PatternMatchingTransactionalPersistedEventHandler() {
                                                                                  @SubscriptionEventHandler
                                                                                  void handle(IntraBankMoneyTransferRequested e, UnitOfWork unitOfWork) {
                                                                                      log.debug("Forwarding {}", e);
                                                                                      moneyTransferEventsOutbox.sendMessage(e);
                                                                                  }

                                                                                  @SubscriptionEventHandler
                                                                                  void handle(IntraBankMoneyTransferStatusChanged e, UnitOfWork unitOfWork) {
                                                                                      log.debug("Forwarding {}", e);
                                                                                      moneyTransferEventsOutbox.sendMessage(e);
                                                                                  }

                                                                                  @SubscriptionEventHandler
                                                                                  void handle(IntraBankMoneyTransferCompleted e, UnitOfWork unitOfWork) {
                                                                                      log.debug("Forwarding {}", e);
                                                                                      moneyTransferEventsOutbox.sendMessage(e);
                                                                                  }
                                                                              });
    }

    @Handler
    public void handle(@NonNull RequestIntraBankMoneyTransfer cmd) {
        if (accounts.isAccountMissing(cmd.fromAccount)) {
            throw new TransactionException(msg("Couldn't find fromAccount with id '{}'", cmd.fromAccount));
        }
        if (accounts.isAccountMissing(cmd.toAccount)) {
            throw new TransactionException(msg("Couldn't find toAccount with id '{}'", cmd.toAccount));
        }

        var existingTransfer = intraBankMoneyTransfers.findTransfer(cmd.transactionId);
        if (existingTransfer.isEmpty()) {
            log.debug("===> Requesting New Transfer '{}'", cmd.transactionId);
            intraBankMoneyTransfers.requestNewTransfer(new IntraBankMoneyTransfer(cmd));
        }
    }


    private class MoneyTransferLifecycleHandler extends AnnotatedEventHandler<Object> {
        @Handler
        void handle(IntraBankMoneyTransferRequested e) {
            // Note any exceptions thrown will cause the message to be redelivered
            unitOfWorkFactory.usingUnitOfWork(uow -> {
                var transfer = intraBankMoneyTransfers.getTransfer(e.transactionId);
                log.debug("===> Transfer '{}' requested - will withdraw {} from account '{}' related to Transfer '{}'", transfer.aggregateId(), transfer.getAmount(), transfer.getFromAccount(), transfer.aggregateId());
                accounts.getAccount(transfer.getFromAccount())
                        .withdrawToday(transfer.getAmount(),
                                       transfer.aggregateId(),
                                       AllowOverdrawingBalance.NO);
            });
        }

        @Handler
        void handle(IntraBankMoneyTransferStatusChanged e) {
            // Note any exceptions thrown will cause the message to be redelivered
            unitOfWorkFactory.usingUnitOfWork(uow -> {
                var transfer = intraBankMoneyTransfers.getTransfer(e.transactionId);
                if (transfer.getStatus() == TransferLifeCycleStatus.FROM_ACCOUNT_WITHDRAWN) {
                    log.debug("===> Will deposit {} to account '{}' related to Transfer '{}'", transfer.getAmount(), transfer.getToAccount(), transfer.aggregateId());
                    accounts.getAccount(transfer.getToAccount())
                            .depositToday(transfer.getAmount(),
                                          transfer.aggregateId());
                }
            });
        }

        @Handler
        void handle(AccountWithdrawn e) {
            // Note any exceptions thrown will cause the message to be redelivered
            unitOfWorkFactory.usingUnitOfWork(uow -> {
                var matchingTransfer = intraBankMoneyTransfers.findTransfer(e.transactionId);

                matchingTransfer.ifPresent(transfer -> {
                    log.debug("===> Account {} Withdrawn - updating Transfer '{}'", e.accountId, transfer.aggregateId());
                    transfer.markFromAccountAsWithdrawn();
                });
            });
        }

        @Handler
        void handle(AccountDeposited e) {
            // Note any exceptions thrown will cause the message to be redelivered
            unitOfWorkFactory.usingUnitOfWork(uow -> {
                var matchingTransfer = intraBankMoneyTransfers.findTransfer(e.transactionId);
                matchingTransfer.ifPresent(transfer -> {
                    log.debug("===> Account {} Deposited - updating Transfer '{}'", e.accountId, transfer.aggregateId());
                    transfer.markToAccountAsDeposited();
                });
            });
        }
    }
}
