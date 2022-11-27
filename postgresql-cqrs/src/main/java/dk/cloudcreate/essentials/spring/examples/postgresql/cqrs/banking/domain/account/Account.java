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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.account;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.EventHandler;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.modern.AggregateRoot;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.*;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.account.events.*;
import dk.cloudcreate.essentials.types.Amount;
import lombok.NonNull;

public class Account extends AggregateRoot<AccountId, AccountEvent, Account> {
    private Amount balance;

    /**
     * Used for rehydration
     */
    public Account(AccountId aggregateId) {
        super(aggregateId);
    }

    public Account(@NonNull AccountId accountId,
                   @NonNull AccountNumber accountNumber) {
        super(accountId);
        apply(new AccountOpened(accountId,
                                accountNumber));
    }

    public void depositToday(@NonNull Amount depositAmount,
                             @NonNull TransactionId transactionId) {
        deposit(depositAmount,
                ValueDate.today(),
                transactionId);
    }

    public void deposit(@NonNull Amount depositAmount,
                        @NonNull ValueDate withValueDate,
                        @NonNull TransactionId transactionId) {
        apply(new AccountDeposited(aggregateId(),
                                   depositAmount,
                                   withValueDate,
                                   transactionId));
    }

    public void withdrawToday(@NonNull Amount withdrawAmount,
                              @NonNull TransactionId transactionId,
                              @NonNull AllowOverdrawingBalance allowOverdrawingBalance) {
        withdraw(withdrawAmount,
                 ValueDate.today(),
                 transactionId,
                 allowOverdrawingBalance);
    }

    public void withdraw(@NonNull Amount withdrawAmount,
                         @NonNull ValueDate withValueDate,
                         @NonNull TransactionId transactionId,
                         @NonNull AllowOverdrawingBalance allowOverdrawingBalance) {
        if (allowOverdrawingBalance.disallowed() && balance.subtract(withdrawAmount).isLessThan(Amount.ZERO)) {
            throw new InsufficientFundsException(aggregateId(),
                                                 balance,
                                                 withdrawAmount);
        }

        apply(new AccountWithdrawn(aggregateId(),
                                   withdrawAmount,
                                   withValueDate,
                                   transactionId));
    }

    public Amount getBalance() {
        return balance;
    }

    @EventHandler
    private void on(AccountOpened e) {
        balance = Amount.ZERO;
    }

    @EventHandler
    private void on(AccountWithdrawn e) {
        balance = balance.subtract(e.withdrawAmount);
    }

    @EventHandler
    private void on(AccountDeposited e) {
        balance = balance.add(e.depositedAmount);
    }
}
