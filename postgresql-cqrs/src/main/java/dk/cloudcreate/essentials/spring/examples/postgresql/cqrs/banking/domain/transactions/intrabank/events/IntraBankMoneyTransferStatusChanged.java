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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.transactions.intrabank.events;

import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.TransactionId;
import dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.domain.transactions.intrabank.TransferLifeCycleStatus;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IntraBankMoneyTransferStatusChanged extends IntraBankMoneyTransferEvent {

    public final TransferLifeCycleStatus status;

    public IntraBankMoneyTransferStatusChanged(@NonNull TransactionId transactionId,
                                               @NonNull TransferLifeCycleStatus status) {
        super(transactionId);
        this.status = status;
    }
}
