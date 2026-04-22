# 🧩 Microservices Quiz Application

A fully distributed, production-style **Quiz Application** built from the ground up using **Spring Boot** and **Spring Cloud** microservices architecture. This project demonstrates real-world patterns including service discovery, inter-service communication via Feign Client, API Gateway routing, and independent database-per-service design — all running as loosely coupled, independently deployable services.

---

## 📐 Architecture Overview

```
                        ┌─────────────────────────────┐
                        │       Client / Postman      │
                        └──────────────┬──────────────┘
                                       │ HTTP Request
                                       ▼
                        ┌─────────────────────────────┐
                        │       API Gateway           │
                        │    (Spring Cloud Gateway)   │
                        │       Port: 8765            │
                        └────────┬──────────┬─────────┘
                                 │          │
                   Route:        │          │   Route:
            /question-service/** │         │  /quiz-service/**
                                 │          │
               ┌─────────────────▼──┐  ┌───▼──────────────────┐
               │  Question Service  │  │     Quiz Service     │
               │   Port: 8080       │◄─┤   Port: 8090         │
               │   DB: PostgreSQL   │  │   DB: PostgreSQL     │
               └────────────────────┘  └──────────────────────┘
                        │                        │
                        └───────────┬────────────┘
                                    │ Register & Discover
                                    ▼
                        ┌─────────────────────────────┐
                        │      Service Registry       │
                        │  (Netflix Eureka Server)    │
                        │       Port: 8761            │
                        └─────────────────────────────┘
```

---

## 🧱 Microservices Breakdown

| Service | Port | Role | Key Technologies |
|---|---|---|---|
| `service-registry` | 8761 | Eureka Server — service discovery hub | Spring Cloud Netflix Eureka |
| `question-service` | 8080 | Manages quiz questions (CRUD + quiz-generation logic) | Spring Boot, Spring Data JPA, PostgreSQL |
| `quiz-service` | 8090 | Manages quizzes, delegates to question-service via Feign | Spring Boot, Feign Client, PostgreSQL |
| `api-gateway` | 8765 | Single entry point — routes all traffic to downstream services | Spring Cloud Gateway |

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17+ |
| Framework | Spring Boot 3.x |
| Service Discovery | Spring Cloud Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Inter-Service Communication | OpenFeign (Feign Client) |
| Persistence | Spring Data JPA / Hibernate |
| Database | PostgreSQL |
| Build Tool | Maven |
| API Testing | Postman / cURL |

---

## 🗂️ Project Structure

```
MicroserviceTutorials/
├── service-registry/          # Eureka Server
│   └── src/main/
│       ├── java/.../ServiceRegistryApplication.java
│       └── resources/application.properties
│
├── question-service/          # Questions microservice
│   └── src/main/
│       ├── java/.../
│       │   ├── controller/QuestionController.java
│       │   ├── service/QuestionService.java
│       │   ├── dao/QuestionDao.java
│       │   ├── model/Question.java
│       │   └── model/QuestionWrapper.java
│       └── resources/application.properties
│
├── quiz-service/              # Quiz microservice
│   └── src/main/
│       ├── java/.../
│       │   ├── controller/QuizController.java
│       │   ├── service/QuizService.java
│       │   ├── dao/QuizDao.java
│       │   ├── model/Quiz.java
│       │   ├── feign/QuizInterface.java   ← Feign client
│       │   └── model/QuestionWrapper.java
│       └── resources/application.properties
│
├── api-gateway/               # Spring Cloud Gateway
│   └── src/main/
│       └── resources/application.properties
│
└── question-table-data.sql    # Seed data for PostgreSQL
```

---

## ⚙️ Prerequisites

- **Java 17+**
- **Maven 3.6+**
- **PostgreSQL 14+**
- Ports `8761`, `8080`, `8090`, `8765` available

---

## 🚀 Getting Started

### 1. Database Setup

```sql
-- Create databases
CREATE DATABASE questiondb;
CREATE DATABASE quizdb;

-- Seed question data
\c questiondb
-- Run question-table-data.sql
```

Or from terminal:
```bash
psql -U postgres -c "CREATE DATABASE questiondb;"
psql -U postgres -c "CREATE DATABASE quizdb;"
psql -U postgres -d questiondb -f question-table-data.sql
```

### 2. Start Services (in order)

> ⚠️ **Order matters!** Always start the Service Registry first so other services can register.

```bash
# Step 1 — Service Registry (Eureka Server)
cd service-registry
mvn spring-boot:run

# Step 2 — Question Service
cd ../question-service
mvn spring-boot:run

# Step 3 — Quiz Service
cd ../quiz-service
mvn spring-boot:run

# Step 4 — API Gateway
cd ../api-gateway
mvn spring-boot:run
```

### 3. Verify Eureka Dashboard

Open your browser at:
```
http://localhost:8761
```

You should see all three client services (`QUESTION-SERVICE`, `QUIZ-SERVICE`, `API-GATEWAY`) registered and healthy.

---

## 📡 API Reference (via API Gateway — Port 8765)

All requests go through the API Gateway. No direct service URLs needed on the client side.

### Question Service Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/question-service/question/getAllQuestions` | Fetch all questions |
| `GET` | `/question-service/question/category/{category}` | Filter by category |
| `POST` | `/question-service/question/addQuestion` | Add a new question |
| `PUT` | `/question-service/question/update/{id}` | Update a question |
| `DELETE` | `/question-service/question/delete/{id}` | Delete a question |
| `GET` | `/question-service/question/generate?category=X&limit=N` | Generate N random question IDs |
| `POST` | `/question-service/question/getQuestions` | Fetch questions by list of IDs |
| `POST` | `/question-service/question/getScore` | Calculate score from responses |

### Quiz Service Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/quiz-service/quiz/generate` | Create a new quiz |
| `GET` | `/quiz-service/quiz/getQuiz/{id}` | Get quiz questions by ID |
| `POST` | `/quiz-service/quiz/getScore` | Submit answers and get score |

---

## 🧪 Sample cURL Commands

**Create a Quiz:**
```bash
curl --location 'localhost:8765/quiz-service/quiz/generate' \
--header 'Content-Type: application/json' \
--data '{
    "category": "Java",
    "limit": 5,
    "title": "Java Basics"
}'
```

**Get Quiz Questions:**
```bash
curl --location 'localhost:8765/quiz-service/quiz/getQuiz/1'
```

**Submit Answers & Get Score:**
```bash
curl --location --request POST 'localhost:8765/quiz-service/quiz/getScore' \
--header 'Content-Type: application/json' \
--data '[
    {"rid": 1, "response": "A programming language"},
    {"rid": 3, "response": "A blueprint for creating objects"}
]'
```

**Add a Question:**
```bash
curl --location 'localhost:8765/question-service/question/addQuestion' \
--header 'Content-Type: application/json' \
--data '{
    "questionTitle": "What is the default scope of a Spring Bean?",
    "option1": "singleton",
    "option2": "prototype",
    "option3": "request",
    "option4": "session",
    "correctAnswer": "singleton",
    "category": "Java",
    "difficultyLevel": "Medium"
}'
```

---

## 🔑 Key Microservices Concepts Demonstrated

| Concept | Implementation |
|---|---|
| **Service Discovery** | All services register with Eureka; resolved by name, not hardcoded IP |
| **API Gateway Pattern** | Single entry point; routes by URI prefix to downstream services |
| **Feign Client (Declarative HTTP)** | Quiz-service calls question-service without RestTemplate boilerplate |
| **Database per Service** | `questiondb` for question-service, `quizdb` for quiz-service — full data isolation |
| **Loose Coupling** | Services communicate by name over HTTP; no shared code or JAR dependencies |
| **Independent Deployability** | Each service can be started, stopped, and scaled independently |

---

## 📬 Data Flow: Quiz Creation

```
Client
  │
  │  POST /quiz-service/quiz/generate {category, limit, title}
  ▼
API Gateway (8765)
  │  Routes to quiz-service
  ▼
Quiz Service (8090)
  │  Feign call → question-service/question/generate?category=X&limit=N
  ▼
Question Service (8080)
  │  Queries PostgreSQL → returns List<Integer> of question IDs
  ▼
Quiz Service (8090)
  │  Saves Quiz entity with question IDs to quizdb
  ▼
Client ← 201 Created
```

---

## 📬 Data Flow: Quiz Submission & Scoring

```
Client
  │
  │  POST /quiz-service/quiz/getScore  [List<Response>]
  ▼
API Gateway (8765)
  │  Routes to quiz-service
  ▼
Quiz Service (8090)
  │  Feign call → question-service/question/getScore [List<Response>]
  ▼
Question Service (8080)
  │  Compares responses to correctAnswer in DB → returns Integer score
  ▼
Quiz Service (8090)
  │  Returns score to client
  ▼
Client ← Score: X/N
```

---

## 🗃️ Database Schema

**question-service (`questiondb`)**

```sql
CREATE TABLE question (
    id          SERIAL PRIMARY KEY,
    category    VARCHAR(100),
    difficulty_level VARCHAR(50),
    option1     VARCHAR(255),
    option2     VARCHAR(255),
    option3     VARCHAR(255),
    option4     VARCHAR(255),
    question_title VARCHAR(500),
    correct_answer VARCHAR(255)
);
```

**quiz-service (`quizdb`)**

```sql
CREATE TABLE quiz (
    id    SERIAL PRIMARY KEY,
    title VARCHAR(255)
);

CREATE TABLE quiz_questions (
    quiz_id     INT REFERENCES quiz(id),
    questions   INT
);
```

---

## 📚 Individual Service READMEs

For deep-dives into each service, see:

- [`service-registry/README.md`](./service-registry/README.md) — Eureka Server setup
- [`question-service/README.md`](./question-service/README.md) — Question CRUD + generation logic
- [`quiz-service/README.md`](./quiz-service/README.md) — Quiz orchestration + Feign
- [`api-gateway/README.md`](./api-gateway/README.md) — Gateway routing configuration

---

## 🧠 Skills Demonstrated

- Designing and implementing a **microservices architecture** from scratch
- Setting up **Eureka-based service discovery** with client-side load balancing
- Building a **Spring Cloud Gateway** with URI-based routing rules
- Using **OpenFeign** for declarative, type-safe inter-service REST calls
- Applying the **Database per Service** pattern with PostgreSQL
- Writing clean **RESTful API** controllers with proper HTTP semantics
- Structuring multi-module Maven projects for independent builds and deployments
- Implementing **orchestration logic** across service boundaries (quiz scoring via delegation)

---
## ⚖️ License

MIT License

Copyright (c) 2026 Sachin K

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
