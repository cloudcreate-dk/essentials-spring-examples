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

package dk.cloudcreate.essentials.spring.examples.mongodb.messaging.shipping.adapters.kafka.incoming;


import dk.cloudcreate.essentials.spring.examples.mongodb.messaging.shipping.OrderId;

public class OrderAccepted extends OrderEvent {
    private long orderNumber;

    public OrderAccepted() {
    }

    public OrderAccepted(OrderId id, long orderNumber) {
        super(id);
        this.orderNumber = orderNumber;
    }

    public long getOrderNumber() {
        return orderNumber;
    }

    @Override
    public String toString() {
        return "OrderAccepted{" +
                "orderId=" + getId() + ", " +
                "orderNumber=" + orderNumber +
                '}';
    }
}
