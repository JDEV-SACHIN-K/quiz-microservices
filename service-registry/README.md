# 🗂️ Service Registry — Eureka Server

The **Service Registry** is the backbone of this microservices system. It acts as a dynamic phone book for all running services, enabling them to register themselves and discover each other by name rather than by hardcoded IP addresses or ports.

Built using **Spring Cloud Netflix Eureka Server**, this service is the first component that must be started before any other service in the system.

---

## 📌 Role in the Architecture

```
                    ┌──────────────────────────────┐
                    │      Service Registry         │
                    │   (Netflix Eureka Server)     │
                    │        Port: 8761             │
                    └───────────┬──────────────────┘
                                │
          ┌─────────────────────┼──────────────────────┐
          │                     │                      │
          ▼                     ▼                      ▼
  ┌────────────────┐  ┌─────────────────┐  ┌───────────────────┐
  │ Question Svc   │  │   Quiz Service  │  │   API Gateway     │
  │  (registers)   │  │   (registers)   │  │   (registers)     │
  └────────────────┘  └─────────────────┘  └───────────────────┘
```

- All services **register** on startup with their hostname, port, and health status.
- All services **discover** each other by logical name (e.g., `QUESTION-SERVICE`) instead of `localhost:8080`.
- If a service goes down, Eureka stops routing traffic to it automatically.

---

## ⚙️ Configuration

**`src/main/resources/application.properties`**

```properties
spring.application.name=service-registry
server.port=8761

# Prevent the Eureka server from registering itself as a client
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

### Why `register-with-eureka=false`?

By default, a Eureka server also tries to act as a Eureka client (registering itself). In a single-server setup this is unnecessary and causes harmless but noisy errors. Setting both flags to `false` ensures the registry only serves — it does not self-register.

---

## 🧩 Main Application Class

```java
@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }
}
```

The `@EnableEurekaServer` annotation is all that's needed to turn a standard Spring Boot app into a fully functional discovery server. Spring Cloud auto-configures the Eureka HTTP endpoints.

---

## 📦 Maven Dependencies

**`pom.xml`**

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>
```

Spring Cloud BOM manages the version compatibility with your Spring Boot version.

---

## 🚀 Running the Service

```bash
cd service-registry
mvn spring-boot:run
```

**Always start this service first.**

Once running, visit the Eureka dashboard:

```
http://localhost:8761
```

You'll see the dashboard listing all registered instances. Initially it will be empty — once `question-service`, `quiz-service`, and `api-gateway` start up, they'll appear here automatically within a few seconds.

---

## 🖥️ Eureka Dashboard

The dashboard at `http://localhost:8761` shows:

- **Instances currently registered** — name, status, and uptime of each service
- **General Info** — server uptime, total memory, etc.
- **Instance info** — which instances are `UP`, `DOWN`, or `OUT_OF_SERVICE`

When all services are running, you should see:

| Application | AMIs | Availability Zones | Status |
|---|---|---|---|
| API-GATEWAY | n/a | (1) | UP (1) |
| QUESTION-SERVICE | n/a | (1) | UP (1) |
| QUIZ-SERVICE | n/a | (1) | UP (1) |

---

## 🔑 Concepts Demonstrated

| Concept | Detail |
|---|---|
| **Service Discovery** | Central registry where all services register and are discovered |
| **Self-Preservation Mode** | Eureka won't immediately evict services during network partitions |
| **Heartbeat Mechanism** | Clients send heartbeats every 30s; Eureka marks them DOWN if missed |
| **Client-Side Load Balancing** | Callers get a list of instances and choose one (used with Feign) |

---

## 🔗 How Other Services Connect

Each client service includes this in its `application.properties`:

```properties
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

And this dependency:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

Spring Boot auto-registers the service on startup using the `spring.application.name` as the service ID.

---

## 📁 Project Structure

```
service-registry/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/.../serviceregistry/
│       │       └── ServiceRegistryApplication.java
│       └── resources/
│           └── application.properties
└── pom.xml
```

---

*Part of the [Microservices Quiz Application](../README.md) — a full Spring Cloud microservices project.*
