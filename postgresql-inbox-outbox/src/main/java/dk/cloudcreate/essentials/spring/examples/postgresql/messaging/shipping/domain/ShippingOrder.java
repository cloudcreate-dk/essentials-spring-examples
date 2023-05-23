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

package dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping.domain;

import dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping.OrderId;
import dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping.commands.RegisterShippingOrder;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingOrder {
    @Id
    @EqualsAndHashCode.Include
    private OrderId                    id;
    private boolean                    shipped;
    @Embedded
    private ShippingDestinationAddress destinationAddress;

    public ShippingOrder(RegisterShippingOrder cmd) {
        this.id = cmd.orderId;
        destinationAddress = cmd.destinationAddress;
    }

    public boolean markOrderAsShipped() {
        // Idempotency check
        if (!shipped) {
            shipped = true;
            return true;
        }
        return false;
    }
}
