# 📋 Quiz Service

The **Quiz Service** is the orchestrator of the quiz application. It is responsible for creating quizzes, fetching quiz question sets, and evaluating user answers — but it does **not** own the question data. Instead, it communicates with the **Question Service** using a **Feign Client** to delegate question-related operations, keeping itself focused purely on quiz lifecycle management.

This service is a textbook example of **inter-service communication** via **OpenFeign** in a Spring Cloud microservices architecture.

---

## 📌 Role in the Architecture

```
             API Gateway (8765)
                    │
                    │  /quiz-service/**
                    ▼
       ┌────────────────────────────────┐
       │         Quiz Service           │
       │           Port: 8090           │
       │                                │
       │  ┌──────────────────────────┐  │
       │  │     QuizController       │  │
       │  └────────────┬─────────────┘  │
       │               │                │
       │  ┌────────────▼─────────────┐  │
       │  │      QuizService         │  │
       │  └──────┬──────────┬────────┘  │
       │         │          │           │
       │  ┌──────▼──┐  ┌────▼────────┐  │
       │  │ QuizDao │  │QuizInterface│  │
       │  │ (JPA)   │  │(Feign Client│  │
       │  └──────┬──┘  └────┬────────┘  │
       │         │          │           │
       └─────────┼──────────┼───────────┘
                 │          │
                 ▼          ▼
          PostgreSQL    Question Service
           (quizdb)      (via Eureka)
```

---

## ⚙️ Configuration

**`src/main/resources/application.properties`**

```properties
spring.application.name=quiz-service
server.port=8090

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/quizdb
spring.datasource.username=postgres
spring.datasource.password=your_password
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# Eureka
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

---

## 🗃️ Data Model

### `Quiz.java`

```java
@Entity
@Data
public class Quiz {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String title;

    @ElementCollection
    private List<Integer> questionIds;  // Stores IDs from question-service
}
```

Note: The Quiz entity stores only question **IDs** — not the full question objects. Full question data is fetched on demand from `question-service` via Feign. This enforces the service boundary.

### `QuestionWrapper.java`

A local DTO mirroring the one in question-service, used to deserialize Feign responses:

```java
@Data
public class QuestionWrapper {
    private Integer id;
    private String questionTitle;
    private String option1;
    private String option2;
    private String option3;
    private String option4;
}
```

### `Response.java`

Used for answer submission:

```java
@Data
public class Response {
    private Integer rid;      // question id
    private String response;  // user's answer
}
```

---

## 🔗 Feign Client — `QuizInterface.java`

This is the heart of inter-service communication. The Feign Client provides a **declarative HTTP client** — you write a Java interface, and Spring Cloud generates the implementation at runtime, handling service discovery via Eureka automatically.

```java
@FeignClient("QUESTION-SERVICE")
public interface QuizInterface {

    @GetMapping("question/generate")
    ResponseEntity<List<Integer>> getQuestionsForQuiz(
            @RequestParam String category,
            @RequestParam Integer numQuestions);

    @PostMapping("question/getQuestions")
    ResponseEntity<List<QuestionWrapper>> getQuestionsFromId(@RequestBody List<Integer> questionIds);

    @PostMapping("question/getScore")
    ResponseEntity<Integer> getScore(@RequestBody List<Response> responses);
}
```

**How it works:**
1. `@FeignClient("QUESTION-SERVICE")` — resolves to any healthy instance registered under that name in Eureka.
2. No hardcoded URLs — if question-service restarts on a new port, Feign still finds it.
3. No manual HTTP code — Feign handles serialization, deserialization, and retry.

---

## 🔧 Service Layer — `QuizService.java`

```java
@Service
public class QuizService {

    @Autowired
    QuizDao quizDao;

    @Autowired
    QuizInterface quizInterface;

    public ResponseEntity<String> createQuiz(String category, int numQ, String title) {
        // 1. Ask question-service for random question IDs
        List<Integer> questions = quizInterface
                .getQuestionsForQuiz(category, numQ).getBody();

        // 2. Build and persist the quiz
        Quiz quiz = new Quiz();
        quiz.setTitle(title);
        quiz.setQuestionIds(questions);
        quizDao.save(quiz);

        return new ResponseEntity<>("Success", HttpStatus.CREATED);
    }

    public ResponseEntity<List<QuestionWrapper>> getQuizQuestions(Integer id) {
        Quiz quiz = quizDao.findById(id).get();
        List<Integer> questionIds = quiz.getQuestionIds();

        // Delegate to question-service for full question objects
        return quizInterface.getQuestionsFromId(questionIds);
    }

    public ResponseEntity<Integer> calculateResult(Integer id, List<Response> responses) {
        // Fully delegate scoring to question-service
        return quizInterface.getScore(responses);
    }
}
```

---

## 🌐 REST API — `QuizController.java`

```java
@RestController
@RequestMapping("quiz")
public class QuizController {

    @Autowired
    QuizService quizService;

    @PostMapping("generate")
    public ResponseEntity<String> createQuiz(@RequestBody QuizDto quizDto) {
        return quizService.createQuiz(
                quizDto.getCategoryName(),
                quizDto.getNumQuestions(),
                quizDto.getTitle());
    }

    @GetMapping("getQuiz/{id}")
    public ResponseEntity<List<QuestionWrapper>> getQuizQuestions(@PathVariable Integer id) {
        return quizService.getQuizQuestions(id);
    }

    @PostMapping("getScore")
    public ResponseEntity<Integer> getResult(
            @PathVariable Integer id,
            @RequestBody List<Response> responses) {
        return quizService.calculateResult(id, responses);
    }
}
```

---

## 📡 API Endpoints

All accessed via API Gateway at `localhost:8765/quiz-service/`

| Method | Path | Description | Body |
|---|---|---|---|
| `POST` | `quiz/generate` | Create a new quiz | `{category, numQuestions, title}` |
| `GET` | `quiz/getQuiz/{id}` | Fetch quiz questions (no answers) | — |
| `POST` | `quiz/getScore` | Submit answers and get score | `List<Response>` |

---

## 🧪 Sample Requests

**Create a quiz:**
```bash
curl --location 'localhost:8765/quiz-service/quiz/generate' \
--header 'Content-Type: application/json' \
--data '{
    "category": "Java",
    "numQuestions": 5,
    "title": "Java Fundamentals Quiz"
}'
```

**Get quiz questions (to display to the user):**
```bash
curl --location 'localhost:8765/quiz-service/quiz/getQuiz/1'
```

Response — note no `correctAnswer` field (stripped by question-service's `QuestionWrapper`):
```json
[
    {
        "id": 3,
        "questionTitle": "What does JVM stand for?",
        "option1": "Java Virtual Machine",
        "option2": "Java Visual Manager",
        "option3": "Java Variable Method",
        "option4": "Java Verified Module"
    }
]
```

**Submit answers and get score:**
```bash
curl --location --request POST 'localhost:8765/quiz-service/quiz/getScore' \
--header 'Content-Type: application/json' \
--data '[
    {"rid": 3, "response": "Java Virtual Machine"},
    {"rid": 7, "response": "A blueprint for creating objects"}
]'
```

Response:
```json
2
```

---

## 📦 Maven Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

---

## 📁 Project Structure

```
quiz-service/
├── src/
│   └── main/
│       ├── java/com/.../quizservice/
│       │   ├── QuizServiceApplication.java
│       │   ├── controller/
│       │   │   └── QuizController.java
│       │   ├── dao/
│       │   │   └── QuizDao.java
│       │   ├── feign/
│       │   │   └── QuizInterface.java       ← Feign Client
│       │   ├── model/
│       │   │   ├── Quiz.java
│       │   │   ├── QuestionWrapper.java
│       │   │   └── Response.java
│       │   └── service/
│       │       └── QuizService.java
│       └── resources/
│           └── application.properties
└── pom.xml
```

---

## 🔑 Key Concepts Demonstrated

| Concept | Implementation |
|---|---|
| **Feign Client** | Declarative REST client — zero boilerplate HTTP code |
| **Service Discovery via Feign** | `@FeignClient("QUESTION-SERVICE")` resolves by Eureka name |
| **Orchestration Pattern** | Quiz service orchestrates across question-service for quiz creation and scoring |
| **Service Boundary Respect** | Quiz service never queries questiondb directly; always goes through question-service's API |
| **DTO / Contract Isolation** | Local `QuestionWrapper` mirrors the contract without coupling to question-service's internals |
| **Database per Service** | Dedicated `quizdb` stores only quiz metadata and question ID references |
| **Lazy Data Fetching** | Stores only question IDs; fetches full data on demand |

---

*Part of the [Microservices Quiz Application](../README.md) — a full Spring Cloud microservices project.*
