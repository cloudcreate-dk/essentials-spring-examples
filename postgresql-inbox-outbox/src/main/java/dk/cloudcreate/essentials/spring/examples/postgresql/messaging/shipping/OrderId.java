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

package dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping;

import dk.cloudcreate.essentials.types.CharSequenceType;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public class OrderId extends CharSequenceType<OrderId> {
    /**
     * Required as otherwise JPA/Hibernate complains with "dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping.OrderId has no persistent id property"
     * as it has problems with supporting SingleValueType immutable objects for identifier fields (as SingleValueType doesn't contain the necessary JPA annotations)
     */
    private String orderId;

    public OrderId(String value) {
        super(value);
        orderId = value.toString();
    }

    public OrderId(CharSequence value) {
        super(value);
        orderId = value.toString();
    }

    /**
     * Is required by JPA
     */
    protected OrderId() {
        super("null");
    }

    public static OrderId random() {
        return new OrderId(UUID.randomUUID().toString());
    }

    public static OrderId of(String id) {
        return new OrderId(id);
    }
}