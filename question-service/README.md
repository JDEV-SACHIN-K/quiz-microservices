# ❓ Question Service

The **Question Service** is the data backbone of the quiz application. It owns and manages the entire question bank — handling full CRUD operations and exposing specialized endpoints for quiz generation and scoring that are consumed by the Quiz Service via Feign Client.

This service follows the **Database per Service** pattern: it has its own dedicated PostgreSQL database (`questiondb`) with no shared schema or direct DB access from any other service.

---

## 📌 Role in the Architecture

```
                    API Gateway (8765)
                          │
                          │  /question-service/**
                          ▼
              ┌──────────────────────────┐
              │     Question Service     │
              │       Port: 8080         │
              │                          │
              │  ┌────────────────────┐  │
              │  │  QuestionController│  │
              │  └────────┬───────────┘  │
              │           │              │
              │  ┌────────▼───────────┐  │
              │  │  QuestionService   │  │
              │  └────────┬───────────┘  │
              │           │              │
              │  ┌────────▼───────────┐  │
              │  │   QuestionDao      │  │
              │  │  (JPA Repository)  │  │
              │  └────────┬───────────┘  │
              │           │              │
              └───────────┼──────────────┘
                          │
                          ▼
                   PostgreSQL (questiondb)
```

The Quiz Service calls this service internally via **Feign Client** to generate question sets and calculate scores. External clients interact via the API Gateway.

---

## ⚙️ Configuration

**`src/main/resources/application.properties`**

```properties
spring.application.name=question-service
server.port=8080

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/questiondb
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

### `Question.java`

```java
@Entity
@Data
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String questionTitle;
    private String option1;
    private String option2;
    private String option3;
    private String option4;
    private String correctAnswer;
    private String category;
    private String difficultyLevel;
}
```

### `QuestionWrapper.java`

A lightweight DTO returned to the Quiz Service and clients — it strips the `correctAnswer` field so users don't see answers during the quiz:

```java
@Data
@AllArgsConstructor
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

Used when submitting answers for scoring:

```java
@Data
public class Response {
    private Integer rid;   // question id
    private String response; // user's selected answer
}
```

---

## 🗄️ Repository — `QuestionDao.java`

```java
@Repository
public interface QuestionDao extends JpaRepository<Question, Integer> {
    List<Question> findByCategory(String category);
}
```

Spring Data JPA auto-generates the SQL. The custom `findByCategory` method translates to `SELECT * FROM question WHERE category = ?`.

---

## 🔧 Service Layer — `QuestionService.java`

Key business logic lives here:

```java
@Service
public class QuestionService {

    @Autowired
    QuestionDao questionDao;

    public ResponseEntity<List<Question>> getAllQuestions() {
        return new ResponseEntity<>(questionDao.findAll(), HttpStatus.OK);
    }

    public ResponseEntity<List<Question>> getQuestionsByCategory(String category) {
        return new ResponseEntity<>(questionDao.findByCategory(category), HttpStatus.OK);
    }

    public ResponseEntity<String> addQuestion(Question question) {
        questionDao.save(question);
        return new ResponseEntity<>("success", HttpStatus.CREATED);
    }

    // Generate N random question IDs for a category (called by Quiz Service)
    public ResponseEntity<List<Integer>> getQuestionsForQuiz(String categoryName, Integer numQuestions) {
        List<Integer> questions = questionDao.findRandomQuestionsByCategory(categoryName, numQuestions);
        return new ResponseEntity<>(questions, HttpStatus.OK);
    }

    // Fetch full question wrappers by IDs (strips correct answer)
    public ResponseEntity<List<QuestionWrapper>> getQuestionsFromId(List<Integer> questionIds) {
        List<QuestionWrapper> wrappers = new ArrayList<>();
        for (Integer id : questionIds) {
            Question q = questionDao.findById(id).get();
            wrappers.add(new QuestionWrapper(q.getId(), q.getQuestionTitle(),
                    q.getOption1(), q.getOption2(), q.getOption3(), q.getOption4()));
        }
        return new ResponseEntity<>(wrappers, HttpStatus.OK);
    }

    // Calculate score from submitted answers
    public ResponseEntity<Integer> getScore(List<Response> responses) {
        int score = 0;
        for (Response response : responses) {
            Question question = questionDao.findById(response.getRid()).get();
            if (question.getCorrectAnswer().equals(response.getResponse()))
                score++;
        }
        return new ResponseEntity<>(score, HttpStatus.OK);
    }
}
```

---

## 🌐 REST API — `QuestionController.java`

```java
@RestController
@RequestMapping("question")
public class QuestionController {

    @Autowired
    QuestionService questionService;

    @GetMapping("getAllQuestions")
    public ResponseEntity<List<Question>> getAllQuestions() { ... }

    @GetMapping("category/{category}")
    public ResponseEntity<List<Question>> getQuestionsByCategory(@PathVariable String category) { ... }

    @PostMapping("addQuestion")
    public ResponseEntity<String> addQuestion(@RequestBody Question question) { ... }

    @PutMapping("update/{id}")
    public ResponseEntity<String> updateQuestion(@PathVariable int id, @RequestBody Question question) { ... }

    @DeleteMapping("delete/{id}")
    public ResponseEntity<String> deleteQuestion(@PathVariable int id) { ... }

    // Internal endpoint — called by Quiz Service via Feign
    @GetMapping("generate")
    public ResponseEntity<List<Integer>> getQuestionsForQuiz(
            @RequestParam String category,
            @RequestParam Integer numQuestions) { ... }

    // Internal endpoint — called by Quiz Service via Feign
    @PostMapping("getQuestions")
    public ResponseEntity<List<QuestionWrapper>> getQuestionsFromId(@RequestBody List<Integer> questionIds) { ... }

    // Internal endpoint — called by Quiz Service via Feign
    @PostMapping("getScore")
    public ResponseEntity<Integer> getScore(@RequestBody List<Response> responses) { ... }
}
```

---

## 📡 API Endpoints

All accessed via API Gateway at `localhost:8765/question-service/`

| Method | Path | Description | Body |
|---|---|---|---|
| `GET` | `question/getAllQuestions` | Get all questions | — |
| `GET` | `question/category/{category}` | Filter questions by category | — |
| `POST` | `question/addQuestion` | Add a new question | `Question` JSON |
| `PUT` | `question/update/{id}` | Update question by ID | `Question` JSON |
| `DELETE` | `question/delete/{id}` | Delete question by ID | — |
| `GET` | `question/generate?category=X&numQuestions=N` | Get N random question IDs | — |
| `POST` | `question/getQuestions` | Get question wrappers by list of IDs | `List<Integer>` |
| `POST` | `question/getScore` | Calculate score from responses | `List<Response>` |

---

## 🧪 Sample Requests

**Get all questions:**
```bash
curl localhost:8765/question-service/question/getAllQuestions
```

**Add a question:**
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

**Update a question:**
```bash
curl --location --request PUT 'localhost:8765/question-service/question/update/1' \
--header 'Content-Type: application/json' \
--data '{
    "questionTitle": "What is the size of int in Java (in bytes)?",
    "option1": "1",
    "option2": "2",
    "option3": "4",
    "option4": "8",
    "correctAnswer": "4",
    "category": "Java",
    "difficultyLevel": "Easy"
}'
```

**Delete a question:**
```bash
curl --location --request DELETE 'localhost:8765/question-service/question/delete/22'
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
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

---

## 📁 Project Structure

```
question-service/
├── src/
│   └── main/
│       ├── java/com/.../questionservice/
│       │   ├── QuestionServiceApplication.java
│       │   ├── controller/
│       │   │   └── QuestionController.java
│       │   ├── dao/
│       │   │   └── QuestionDao.java
│       │   ├── model/
│       │   │   ├── Question.java
│       │   │   ├── QuestionWrapper.java
│       │   │   └── Response.java
│       │   └── service/
│       │       └── QuestionService.java
│       └── resources/
│           └── application.properties
└── pom.xml
```

---

## 🔑 Key Concepts Demonstrated

| Concept | Implementation |
|---|---|
| **RESTful API Design** | Proper HTTP verbs (GET/POST/PUT/DELETE) and status codes |
| **Spring Data JPA** | Repository pattern with auto-generated queries |
| **Database per Service** | Dedicated `questiondb` — no shared schema |
| **DTO Pattern** | `QuestionWrapper` hides `correctAnswer` from quiz consumers |
| **Service Layer Pattern** | Controller delegates to service; service delegates to DAO |
| **Eureka Client** | Auto-registers with service registry on startup |
| **Internal API Contract** | Dedicated endpoints for inter-service calls (generate, getQuestions, getScore) |

---

*Part of the [Microservices Quiz Application](../README.md) — a full Spring Cloud microservices project.*
