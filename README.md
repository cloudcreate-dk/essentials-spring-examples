# Essentials Spring Example

## License
Essentials is released under version 2.0 of the [Apache License](https://www.apache.org/licenses/LICENSE-2.0)

> **NOTE:**  
> **The Essentials libraries are WORK-IN-PROGRESS**

# Security

Several of the components, as well as their subcomponents and/or supporting classes, allows the user of the components to provide customized:
- table names
- column names
- collection names
- etc.

By using naming conventions for Postgresql table/column/index names and MongoDB Collection names, Essentials attempts to provide an initial layer of defense intended to reduce the risk of malicious input.    
**However, Essentials does not offer exhaustive protection, nor does it assure the complete security of the resulting SQL and Mongo Queries/Updates against injection threats.**
> The responsibility for implementing protective measures against malicious API input and configuration values lies exclusively with the users/developers using the Essentials components and its supporting classes.  
> Users must ensure thorough sanitization and validation of API input parameters, SQL table/column/index names as well as MongoDB collection names.

**Insufficient attention to these practices may leave the application vulnerable to attacks, endangering the security and integrity of the database.**

> Please see the **Security** notices for Essentials `components/README.md`, as well as **Security** notices for the individual components, to familiarize yourself with the security
> risks related to using the Essentials Components:
> - `foundation-types/README.md`
> - `components/postgresql-distributed-fenced-lock/README.md`
> - `components/springdata-mongo-distributed-fenced-lock/README.md`
> - `components/postgresql-queue/README.md`
> - `components/springdata-mongo-queue/README.md`
> - `components/postgresql-event-store/README.md`
> - `components/eventsourced-aggregates/README.md`
> - `components/spring-boot-starter-postgresql/README.md`
> - `components/spring-boot-starter-postgresql-event-store/README.md`
> - `components/spring-boot-starter-mongodb/README.md`

## Versions

| Essentials version                                                                           | Java compatibility | Spring Boot compatibility | Notes                      |
|----------------------------------------------------------------------------------------------|--------------------|---------------------------|----------------------------|
| [0.9.*](https://github.com/cloudcreate-dk/essentials-spring-examples/tree/java11)            | 11-16              | 2.7.x                     | No longer being maintained |
| [0.20.*](https://github.com/cloudcreate-dk/essentials-spring-examples/tree/springboot_3_0_x) | 17+                | 3.0.x                     | No longer being maintained |
| [0.30.*](https://github.com/cloudcreate-dk/essentials-spring-examples/tree/springboot_3_1_x) | 17+                | 3.1.x                     | No longer being maintained |
| [0.40.*](https://github.com/cloudcreate-dk/essentials-spring-examples/tree/main)             | 17+                | 3.2.x                     | Under active development   |

Examples of how to use the Essentials and Essentials Components together with Spring and Spring Boot:

- [mongodb-inbox-outbox](mongodb-inbox-outbox/README.md) - MongoDB focused Inbox/Outbox example that integrates with Kafka
- [postgresql-inbox-outbox](postgresql-inbox-outbox/README.md) - Postgresql focused Inbox/Outbox example that integrates with Kafka
- [postgresql-cqrs](postgresql-cqrs/README.md) - Postgresql focused CQRS and EventSourced aggregate examples
