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

import dk.cloudcreate.essentials.types.CharSequenceType;

import java.util.UUID;

/**
 * Account identifier
 */
public class AccountId extends CharSequenceType<AccountId> {

    public AccountId(CharSequence value) {
        super(value);
    }

    public static AccountId random() {
        return new AccountId(UUID.randomUUID().toString());
    }

    public static AccountId of(CharSequence id) {
        return new AccountId(id);
    }
}