/*
 * Copyright 2021-2023 the original author or authors.
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

package dk.cloudcreate.essentials.spring.examples.mongodb.messaging.shipping.domain;

import dk.cloudcreate.essentials.spring.examples.mongodb.messaging.shipping.OrderId;
import dk.cloudcreate.essentials.spring.examples.mongodb.messaging.shipping.commands.RegisterShippingOrder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class ShippingOrder {
    @Id
    private OrderId                    id;
    private ShippingDestinationAddress destinationAddress;
    private boolean                    shipped;

    public ShippingOrder() {
    }

    public ShippingOrder(RegisterShippingOrder cmd) {
        id = cmd.orderId;
        destinationAddress = cmd.destinationAddress;
    }

    /**
     *
     * @return returns true if the order was marked as shipped, otherwise it returns false
     */
    public boolean markOrderAsShipped() {
        // Idempotency check
        if (!shipped) {
            shipped = true;
            return true;
        }
        return false;
    }
}
