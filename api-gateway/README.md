# 🚪 API Gateway

The **API Gateway** is the single entry point for all external traffic in the microservices quiz application. Instead of clients knowing the host and port of every individual service, they only talk to the gateway — which routes each request to the appropriate downstream service based on the URL path.

Built with **Spring Cloud Gateway**, it integrates seamlessly with Eureka for service discovery, meaning it resolves service locations dynamically without any hardcoded URLs.

---

## 📌 Role in the Architecture

```
         Client / Postman / Browser
                    │
         All requests → localhost:8765
                    │
       ┌────────────▼─────────────────────┐
       │           API Gateway            │
       │         Port: 8765               │
       │                                  │
       │  /question-service/** ──────────►│ ──► QUESTION-SERVICE (8080)
       │  /quiz-service/**     ──────────►│ ──► QUIZ-SERVICE (8090)
       │                                  │
       │  (Resolves via Eureka registry)  │
       └──────────────────────────────────┘
```

Without the gateway, a client would need to know:
- `localhost:8080` → question-service
- `localhost:8090` → quiz-service

With the gateway, everything goes to `localhost:8765`, and routing is centralized and configurable.

---

## ⚙️ Configuration

**`src/main/resources/application.properties`**

```properties
spring.application.name=api-gateway
server.port=8765

# Eureka
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/

# Spring Cloud Gateway — Route Definitions
spring.cloud.gateway.routes[0].id=question-service
spring.cloud.gateway.routes[0].uri=lb://QUESTION-SERVICE
spring.cloud.gateway.routes[0].predicates[0]=Path=/question-service/**

spring.cloud.gateway.routes[1].id=quiz-service
spring.cloud.gateway.routes[1].uri=lb://QUIZ-SERVICE
spring.cloud.gateway.routes[1].predicates[0]=Path=/quiz-service/**
```

### Breaking Down the Config

| Property | Meaning |
|---|---|
| `routes[0].id` | Logical name for the route (any unique string) |
| `routes[0].uri=lb://QUESTION-SERVICE` | `lb://` means load-balanced; resolves `QUESTION-SERVICE` from Eureka |
| `routes[0].predicates[0]=Path=/question-service/**` | Match all requests starting with `/question-service/` |

When a request arrives at `/question-service/question/getAllQuestions`, the gateway strips nothing by default — it forwards the full path to the resolved service. Since the question-service maps its controller to `question/`, the path `/question-service/question/getAllQuestions` is forwarded and the downstream service receives `/question-service/question/getAllQuestions`.

> ⚠️ **Path stripping note:** If you need to strip the prefix, add a `RewritePath` filter. In this project the services are configured to match the gateway's forwarded paths, so no stripping is needed.

---

## 🧩 Main Application Class

```java
@SpringBootApplication
@EnableEurekaClient
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

No special gateway annotation is needed beyond the `spring-cloud-starter-gateway` dependency — Spring Boot's auto-configuration reads the route definitions from `application.properties` automatically.

---

## 📦 Maven Dependencies

```xml
<dependencies>
    <!-- Spring Cloud Gateway (uses Netty, NOT spring-boot-starter-web) -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>

    <!-- Eureka Client for service discovery-based routing -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
</dependencies>
```

> ⚠️ **Critical:** Spring Cloud Gateway is built on **Reactor Netty** (reactive), so you must **NOT** include `spring-boot-starter-web` (which uses the blocking Servlet stack). These two are incompatible. The gateway works without it.

---

## 🔀 Routing Table

| Incoming Path | Forwarded To | Resolved Service |
|---|---|---|
| `localhost:8765/question-service/**` | `lb://QUESTION-SERVICE/**` | question-service (8080) |
| `localhost:8765/quiz-service/**` | `lb://QUIZ-SERVICE/**` | quiz-service (8090) |

---

## 🧪 Sample Requests Through the Gateway

**All requests go to port 8765 — the gateway's port.**

```bash
# Question Service — via gateway
curl localhost:8765/question-service/question/getAllQuestions

# Quiz Service — via gateway
curl --location 'localhost:8765/quiz-service/quiz/generate' \
--header 'Content-Type: application/json' \
--data '{"category": "Java", "numQuestions": 5, "title": "Java Quiz"}'
```

You can also hit the services directly (bypassing the gateway) during development:
```bash
# Direct — not recommended for production
curl localhost:8080/question/getAllQuestions
curl localhost:8090/quiz/getQuiz/1
```

---

## 📁 Project Structure

```
api-gateway/
├── src/
│   └── main/
│       ├── java/com/.../apigateway/
│       │   └── ApiGatewayApplication.java
│       └── resources/
│           └── application.properties    ← All routing config lives here
└── pom.xml
```

The API Gateway is intentionally minimal — its entire responsibility is routing. No controllers, no services, no entities.

---

## 🔑 Key Concepts Demonstrated

| Concept | Implementation |
|---|---|
| **API Gateway Pattern** | Single entry point; clients don't know service locations |
| **URI-Based Routing** | Routes matched by path prefix (`/question-service/**`) |
| **Dynamic Service Resolution** | `lb://SERVICE-NAME` resolves live instances from Eureka |
| **Load Balancing** | `lb://` prefix enables client-side load balancing across multiple instances |
| **Reactive Stack** | Spring Cloud Gateway runs on Reactor Netty — non-blocking, high-throughput |
| **Zero-code Gateway** | Entire routing config is declarative in `application.properties` |

---

## 🔮 Possible Extensions

In a production system, the API Gateway is often extended with:

- **Authentication filters** — validate JWT tokens before forwarding
- **Rate limiting** — `RequestRateLimiter` with Redis to throttle requests
- **Circuit breaker** — Resilience4j integration for fallbacks
- **Request logging** — Custom `GlobalFilter` for auditing
- **CORS configuration** — For browser-based frontend clients
- **SSL termination** — HTTPS at the gateway; HTTP internally

These extensions require no changes to downstream services — the gateway handles them transparently.

---

*Part of the [Microservices Quiz Application](../README.md) — a full Spring Cloud microservices project.*
