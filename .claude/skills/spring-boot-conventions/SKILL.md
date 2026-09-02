---
name: spring-boot-conventions
description: Use whenever creating or structuring a new feature, entity, controller, service, or repository in this Spring Boot project. Ensures consistent project structure and layering.
---

This project follows package-by-feature, not package-by-layer.

Structure for a new feature (e.g. "order"):
com.project.order/
Order.java (JPA entity)
OrderRepository.java (Spring Data JPA interface)
OrderService.java (business logic — this is where logic lives, not the controller)
OrderController.java (thin — delegates to OrderService, no business logic here)

Rules:
- Controllers stay thin: validate input, call the service, return the view/response. No business logic.
- Services own business logic and orchestrate repository calls.
- Repositories extend JpaRepository<Entity, IdType> — don't hand-write basic CRUD.
- Every new service method that has non-trivial logic gets at least one JUnit test.
- Thymeleaf templates live in src/main/resources/templates/<feature>/, named after the controller action (e.g. list.html, form.html).
