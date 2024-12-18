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

package dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping.domain;

import dk.cloudcreate.essentials.spring.examples.postgresql.messaging.shipping.OrderId;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.*;

public interface ShippingOrders extends JpaRepository<ShippingOrder, String> {
    default Optional<ShippingOrder> findOrder(@NonNull OrderId orderId) {
        return findById(orderId.toString());
    }

    default ShippingOrder getOrder(@NonNull OrderId orderId) {
        return findOrder(orderId).get();
    }

    default void registerNewOrder(@NonNull ShippingOrder order) {
        save(order);
    }

    List<ShippingOrder> findByShipped(boolean shippedStatus);
    List<ShippingOrder> findByIdIn(List<String> ids);

    @Query("SELECT id FROM ShippingOrder")
    List<String> findAllOrderIds();
}
