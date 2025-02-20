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

package dk.cloudcreate.essentials.spring.examples.postgresql.cqrs;

import dk.cloudcreate.essentials.components.foundation.messaging.*;
import dk.cloudcreate.essentials.components.foundation.reactive.command.DurableLocalCommandBus;
import dk.cloudcreate.essentials.components.foundation.reactive.command.DurableLocalCommandBusBuilder;
import dk.cloudcreate.essentials.reactive.command.*;
import dk.cloudcreate.essentials.shared.Exceptions;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;

@SpringBootApplication
@Slf4j
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    /**
     * Custom {@link RedeliveryPolicy} used by the {@link DurableLocalCommandBus} that is autoconfigured by the springboot starter
     * @return The {@link RedeliveryPolicy} used for {@link DurableLocalCommandBusBuilder#setCommandQueueRedeliveryPolicy(RedeliveryPolicy)}
     */
    @Bean
    RedeliveryPolicy durableLocalCommandBusRedeliveryPolicy() {
        return RedeliveryPolicy.exponentialBackoff()
                               .setInitialRedeliveryDelay(Duration.ofMillis(200))
                               .setFollowupRedeliveryDelay(Duration.ofMillis(200))
                               .setFollowupRedeliveryDelayMultiplier(1.1d)
                               .setMaximumFollowupRedeliveryDelayThreshold(Duration.ofSeconds(3))
                               .setMaximumNumberOfRedeliveries(20)
                               .setDeliveryErrorHandler(
                                       MessageDeliveryErrorHandler.stopRedeliveryOn(
                                               ConstraintViolationException.class,
                                               HttpClientErrorException.BadRequest.class))
                               .build();
    }


    /**
     * Custom {@link SendAndDontWaitErrorHandler} used by the {@link DurableLocalCommandBus} that is autoconfigured by the springboot starter
     * @return The {@link SendAndDontWaitErrorHandler} used for {@link DurableLocalCommandBusBuilder#setSendAndDontWaitErrorHandler(SendAndDontWaitErrorHandler)}
     */
    @Bean
    SendAndDontWaitErrorHandler sendAndDontWaitErrorHandler() {
        return (exception, commandMessage, commandHandler) -> {
            // Example of not retrying HttpClientErrorException.Unauthorized at all -
            // if this exception is encountered then the failure is logged, but the command is never retried
            // nor marked as a dead-letter/poison message
            if (exception instanceof HttpClientErrorException.Unauthorized) {
                log.error("Unauthorized exception", exception);
            } else {
                Exceptions.sneakyThrow(exception);
            }
        };
    }
}
