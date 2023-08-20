# Essentials components: Postgresql Event Store (CQRS) example

The example uses the `spring-boot-starter-postgresql-event-store` that provides Spring Boot auto-configuration for all Postgresql focused Essentials components as well as the
Postgresql `EventStore`.  
All `@Beans` auto-configured by this library use `@ConditionalOnMissingBean` to allow for easy overriding.


## Transfer Money example

This is a Saga like process, where a user requests an IntraBank Money Transfer, i.e. transfer of money between accounts
that both belong to the same Bank.  
The `TransferMoneyProcessor`, which is an [`EventProcessor`](https://github.com/cloudcreate-dk/essentials-project/tree/main/components/foundation#eventprocessor),
tracks the `IntraBankMoneyTransfer` lifecycle and ensures that the **From** `Account` is withdrawn and the **To** `Account` is deposited.

Flow:

![Intra Bank Money Transfer Flow](img/TransferMoneyFlow.png)

See `dk.cloudcreate.essentials.spring.examples.postgresql.cqrs.banking.TransferMoneyProcessor`

### Test the Transfer Money flow
Run `TransferMoneyProcessorIT` from within your IDE or using Maven `mvn verify -pl :postgresql-cqrs` from the **root** of the `essentials-spring-examples` project folder. 

## Shipping flow example

### Test the Shipping flow
You can either run the `OrderShippingProcessorIT` (see `Shipping flow explained` for details about the example)
from within your IDE or using Maven `mvn verify -pl :postgresql-cqrs` from the **root** of the `essentials-spring-examples` project folder.

### Test the Shipping flow using `curl` from the Terminal
Alternatively you can start the Spring Boot application standalone from the **root** of the `essentials-spring-examples` project folder using
```bash
docker compose up -d
mvn spring-boot:run -pl :postgresql-cqrs
```

The last command will block the current terminal, so to continue you need to open a new Terminal.

#### Initiate the test scenario:
In a new Terminal enter the following command:
```bash
curl -L 'http://localhost:8080/shipping/register-order' \ 
-X POST \
-H 'Accept: application/json' \
-H 'Content-Type: application/json' \
-d '{
  "orderId": "order1",
  "destinationAddress": {
   "recipientName": "John Doe",
   "street": "Test Street 1",
   "zipCode": "1234",
   "city": "Test City"
  }
}'
```
#### Complete the test scenario:
In the same Terminal enter the following command:
```bash
curl -L 'http://localhost:8080/shipping/ship-order' \ 
-H 'Accept: application/json' \
-H 'Content-Type: application/json' \
-d '{
  "orderId": "order1"
}'
```

This will trigger the Shipping process.

#### Stop the test scenario:
- Stop the Spring Boot Application by pressing `Ctrl C`
- Stop Docker:
  ```bash
    docker compose down
  ```

### Shipping flow explained
The `OrderShippingProcessorIT` integration-test coordinates the test flow:

- First a `ShippingOrder` aggregate is created, by sending `RegisterShippingOrder` over the `CommandBus`
  - The `OrderShippingProcessor` is auto registered with the `CommandBus` as a `CommandHandler` because it implements the `CommandHandler` interface through the `AnnotatedCommandHandler` base class
  - The `OrderShippingProcessor.handle(RegisterShippingOrder)` command handler method reacts to the `RegisterShippingOrder` in an existing Transaction/`UnitOfWork`  since the `Inbox` is configured
    with `TransactionalMode.FullyTransactional`.
  - The `OrderShippingProcessor.handle(RegisterShippingOrder)` ensures that the `ShippingOrder` aggregate is stored
  - After the transaction/`UnitOfWork` the `ShippingOrderRegistered` event is published via the `EventStoreSubscriptionManager`
- Next we simulate that the **OrderService** publishes a `OrderAccepted` event via Kafka, which the `OrderEventsKafkaListener` is listening for
- The `OrderEventsKafkaListener` reacts to the `OrderAccepted` and converts it into a `ShipOrder` command.
  - Afterwards the `ShipOrder` command is added to the `shipOrdersInbox` of type `Inbox`
  - After the transaction/`UnitOfWork` the `OrderAccepted` event is published via the `EventStoreSubscriptionManager`
- Asynchronously the `shipOrdersInbox` will forward the `ShipOrder` command to the `CommandBus`
  - Note: the `Order` and `ShippingOrder` are correlated/linked through the `OrderId` (aggregates reference each other using id's)
- The `OrderShippingProcessor.handle(ShipOrder)` command handler method reacts to the `ShipOrder` command
  - ![Handling a Kafka Message using an Inbox](https://github.com/cloudcreate-dk/essentials-project/blob/main/components/foundation/images/inbox.png)
  - It loads the corresponding `ShippingOrder` instance and performs an idempotency check - if the order is already **marked-as-shipped**
    - This idempotency check is necessary as we're using in Messaging we deal with At-Least-Once message delivery guarantee and delivery of the `ShipOrder` command can end up
      being delivered by the `Inbox` multiple times
  - If **marking** the `ShippingOrder` as **shipped** succeeds, then after the transaction/`UnitOfWork` completes, then the `OrderShipped` event is published via the `EventStoreSubscriptionManager`
- The `ShippingEventKafkaPublisher` is auto registered with the `EventStoreSubscriptionManager` asynchronously
  - The `ShippingEventKafkaPublisher`'s `eventStoreSubscriptionManager.subscribeToAggregateEventsAsynchronously` event handler converts the `OrderShipped` event to an external
    event `ExternalOrderShipped`
  - The `ExternalOrderShipped` is then added to the `kafkaOutbox` of type `Outbox`, that the `ShippingEventKafkaPublisher` has configured
- Asynchronously the `kafkaOutbox` will call its Message consumer (in this case a lambda) which uses a `KafkaTemplate` to publish the `ExternalOrderShipped` to a Kafka Topic
  - ![Publishing a Kafka Message using an Outbox](https://github.com/cloudcreate-dk/essentials-project/blob/main/components/foundation/images/outbox.png)

## Application Setup
The following Essentials components are auto configured:
`EssentialsComponentsConfiguration` auto-configures:

- Jackson/FasterXML JSON modules:
    - `EssentialTypesJacksonModule`
    - `EssentialsImmutableJacksonModule` (if `Objenesis` is on the classpath)
    - `ObjectMapper` with bean name `essentialComponentsObjectMapper` which provides good defaults for JSON serialization
- `Jdbi` to use the provided Spring `DataSource`
- `SpringTransactionAwareJdbiUnitOfWorkFactory` configured to use the Spring provided `PlatformTransactionManager`
    - This `UnitOfWorkFactory` will only be auto-registered if the `SpringTransactionAwareEventStoreUnitOfWorkFactory` is not on the classpath (see `EventStoreConfiguration`)
- `PostgresqlFencedLockManager` using the `essentialComponentsObjectMapper` as JSON serializer
    - Supports additional properties:
  ```
    essentials.fenced-lock-manager.fenced-locks-table-name=fenced_locks
    essentials.fenced-lock-manager.lock-confirmation-interval=5s
    essentials.fenced-lock-manager.lock-time-out=12s
    ```
- `PostgresqlDurableQueues` using the `essentialComponentsObjectMapper` as JSON serializer
    - Supports additional properties:
  ```
  essentials.durable-queues.shared-queue-table-name=durable_queues
  essentials.durable-queues.transactional-mode=fullytransactional
  essentials.durable-queues.polling-delay-interval-increment-factor=0.5
  essentials.durable-queues.max-polling-interval=2s
  # Only relevant if transactional-mode=singleoperationtransaction
  # essentials.durable-queues.message-handling-timeout=5s
  ```
- `Inboxes`, `Outboxes` and `DurableLocalCommandBus` configured to use `PostgresqlDurableQueues`
- `LocalEventBus` with bus-name `default` and Bean name `eventBus`
- `ReactiveHandlersBeanPostProcessor` (for auto-registering `EventHandler` and `CommandHandler` Beans with the `EventBus`'s and `CommandBus` beans found in the `ApplicationContext`)
- Automatically calling `Lifecycle.start()`/`Lifecycle.stop`, on any Beans implementing the `Lifecycle` interface, when the `ApplicationContext` is started/stopped

`EventStoreConfiguration` will also auto-configure the `EventStore`:

- `PostgresqlEventStore` using `PostgresqlEventStreamGapHandler` (using default configuration)
    - You can configure `NoEventStreamGapHandler` using Spring properties:
    - `essentials.event-store.use-event-stream-gap-handler=false`
- `SeparateTablePerAggregateTypePersistenceStrategy` using `IdentifierColumnType.TEXT` for persisting `AggregateId`'s and `JSONColumnType.JSONB` for persisting Event and EventMetadata JSON payloads
    - ColumnTypes can be overridden by using Spring properties:
  ```
       essentials.event-store.identifier-column-type=uuid
       essentials.event-store.json-column-type=jsonb
  ```
- `EventStoreUnitOfWorkFactory` in the form of `SpringTransactionAwareEventStoreUnitOfWorkFactory`
- `EventStoreEventBus` with an internal `LocalEventBus` with bus-name `EventStoreLocalBus`
- `PersistableEventMapper` with basic setup. Override this bean if you need additional meta-data, such as event-id, event-type, event-order, event-timestamp, event-meta-data, correlation-id, tenant-id
  included
- `EventStoreSubscriptionManager` with default `EventStoreSubscriptionManagerProperties` values
    - The default `EventStoreSubscriptionManager` values can be overridden using Spring properties:
    - ```
      essentials.event-store.subscription-manager.event-store-polling-batch-size=5
      essentials.event-store.subscription-manager.snapshot-resume-points-every=2s
      essentials.event-store.subscription-manager.event-store-polling-interval=200
      ```