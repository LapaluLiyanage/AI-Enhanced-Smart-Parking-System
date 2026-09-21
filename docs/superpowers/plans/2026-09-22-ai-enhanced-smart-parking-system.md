# AI-Enhanced Smart Parking System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build, end-to-end, a Spring Boot backend for booking parking slots with real-time availability, JWT auth, automatic reservation expiry, dynamic pricing, and a Gemini-powered AI layer (demand prediction + natural-language booking), committing each stage separately to GitHub.

**Architecture:** Layered Spring Boot app (`controller → service → repository`) on PostgreSQL (Docker Compose locally, Testcontainers for tests). Booking overlap safety uses a pessimistic DB lock + in-transaction overlap check. The AI layer sits behind an `AiAssistantService` interface implemented by `GeminiAssistantService`, calling the Gemini REST API directly (no heavy SDK), so it's mockable in unit tests.

**Tech Stack:** Java 17, Spring Boot 3.2.x (Web, Data JPA, Security, Validation), Maven, PostgreSQL 16, `io.jsonwebtoken:jjwt` for JWT, Spring `RestClient` for Gemini HTTP calls, JUnit 5 + Mockito, Testcontainers (postgresql module), Docker Compose.

**Spec:** `docs/superpowers/specs/2026-09-22-ai-enhanced-smart-parking-system-design.md`

## Global Constraints

- Java 17, Spring Boot 3.2.x, Maven (not Gradle).
- Database: PostgreSQL, run via `docker-compose.yml` for local dev; Testcontainers `postgresql` module for integration tests.
- LLM provider: Google Gemini (`gemini-1.5-flash` model), called via plain HTTPS REST (no `google-genai` SDK dependency), API key from `GEMINI_API_KEY` env var.
- `AiAssistantService` is an interface; all business logic that depends on it must accept it as an injected dependency so it can be mocked in unit tests.
- Booking overlap: pessimistic lock (`SELECT ... FOR UPDATE`) + in-transaction overlap check, per spec — not optimistic locking, not a DB `EXCLUDE` constraint.
- Every domain exception maps to a specific HTTP status via a single `@RestControllerAdvice`.
- One git commit per numbered stage/task below; do **not** add any AI co-author trailer to commit messages (explicit user instruction — this overrides default attribution behavior).
- Secrets (`GEMINI_API_KEY`, `JWT_SECRET`, DB credentials) come from environment variables, never hardcoded.

---

## Task 1: Project Skeleton

**Files:**
- Create: `pom.xml`
- Create: `src/main/resources/application.yml`
- Create: `src/main/java/com/smartparking/SmartParkingApplication.java`
- Create: `src/main/java/com/smartparking/controller/HealthController.java`
- Create: `docker-compose.yml`
- Create: `.gitignore`
- Create: `.env.example`
- Test: `src/test/java/com/smartparking/controller/HealthControllerTest.java`

**Interfaces:**
- Produces: base package `com.smartparking`; `GET /health` returns `200 OK` with body `{"status":"UP"}`.

- [ ] **Step 1: Create `.gitignore`**

```
target/
*.class
.env
.idea/
*.iml
.vscode/
HELP.md
```

- [ ] **Step 2: Create `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
    <relativePath/>
  </parent>

  <groupId>com.smartparking</groupId>
  <artifactId>ai-enhanced-smart-parking-system</artifactId>
  <version>0.1.0</version>
  <name>ai-enhanced-smart-parking-system</name>
  <description>AI-Enhanced Smart Parking System</description>

  <properties>
    <java.version>17</java.version>
    <jjwt.version>0.12.5</jjwt.version>
  </properties>

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
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>${jjwt.version}</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-bom</artifactId>
        <version>1.19.7</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Create `src/main/resources/application.yml`**

```yaml
server:
  port: 8080

spring:
  application:
    name: ai-enhanced-smart-parking-system
  datasource:
    url: jdbc:postgresql://localhost:5432/smart_parking
    username: ${DB_USERNAME:parking_user}
    password: ${DB_PASSWORD:parking_pass}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: true

app:
  jwt:
    secret: ${JWT_SECRET:dev-only-change-me-dev-only-change-me-32bytes}
    expiration-ms: 3600000
  booking:
    pending-expiry-minutes: 10
  pricing:
    base-hourly-rate: 2.50
  ai:
    gemini:
      api-key: ${GEMINI_API_KEY:}
      model: gemini-1.5-flash
      base-url: https://generativelanguage.googleapis.com/v1beta
```

- [ ] **Step 4: Create `docker-compose.yml`**

```yaml
version: "3.8"
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: smart_parking
      POSTGRES_USER: parking_user
      POSTGRES_PASSWORD: parking_pass
    ports:
      - "5432:5432"
    volumes:
      - parking_pgdata:/var/lib/postgresql/data

volumes:
  parking_pgdata:
```

- [ ] **Step 5: Create `.env.example`**

```
DB_USERNAME=parking_user
DB_PASSWORD=parking_pass
JWT_SECRET=change-this-to-a-long-random-secret-at-least-32-bytes
GEMINI_API_KEY=your-gemini-api-key-here
```

- [ ] **Step 6: Create the application entry point**

`src/main/java/com/smartparking/SmartParkingApplication.java`:

```java
package com.smartparking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartParkingApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartParkingApplication.class, args);
    }
}
```

- [ ] **Step 7: Write the failing test for the health endpoint**

`src/test/java/com/smartparking/controller/HealthControllerTest.java`:

```java
package com.smartparking.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"status\":\"UP\"}"));
    }
}
```

Note: this test will fail to even start the Spring context until `HealthController`
exists and a Postgres instance is reachable at the configured URL. Start Postgres
first: `docker compose up -d`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=HealthControllerTest`
Expected: FAIL (compilation error — `HealthController` does not exist yet, or 404).

- [ ] **Step 3: Implement `HealthController`**

`src/main/java/com/smartparking/controller/HealthController.java`:

```java
package com.smartparking.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
```

- [ ] **Step 4: Start Postgres and run the test**

```bash
docker compose up -d
./mvnw test -Dtest=HealthControllerTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add pom.xml .gitignore .env.example docker-compose.yml src
git commit -m "feat: project skeleton with health endpoint and Postgres docker-compose"
git push
```

---

## Task 2: Auth (User entity, JWT, register/login)

**Files:**
- Create: `src/main/java/com/smartparking/entity/User.java`
- Create: `src/main/java/com/smartparking/entity/Role.java`
- Create: `src/main/java/com/smartparking/repository/UserRepository.java`
- Create: `src/main/java/com/smartparking/security/JwtService.java`
- Create: `src/main/java/com/smartparking/security/JwtAuthFilter.java`
- Create: `src/main/java/com/smartparking/security/SecurityConfig.java`
- Create: `src/main/java/com/smartparking/dto/RegisterRequest.java`
- Create: `src/main/java/com/smartparking/dto/LoginRequest.java`
- Create: `src/main/java/com/smartparking/dto/AuthResponse.java`
- Create: `src/main/java/com/smartparking/service/AuthService.java`
- Create: `src/main/java/com/smartparking/controller/AuthController.java`
- Test: `src/test/java/com/smartparking/service/AuthServiceTest.java`
- Test: `src/test/java/com/smartparking/controller/AuthControllerIntegrationTest.java`

**Interfaces:**
- Produces: `User(id, email, passwordHash, role)`; `AuthService.register(String email, String rawPassword)` returns `User`; `AuthService.login(String email, String rawPassword)` returns `String` (JWT); `JwtService.generateToken(User user)` returns `String`; `JwtService.extractEmail(String token)` returns `String`.
- Consumes: none (foundational for later tasks — `BookingController`/`AdminController` will consume `Authentication.getName()` as the user's email and roles as `ROLE_USER`/`ROLE_ADMIN`).

- [ ] **Step 1: Create `Role` enum**

`src/main/java/com/smartparking/entity/Role.java`:

```java
package com.smartparking.entity;

public enum Role {
    USER, ADMIN
}
```

- [ ] **Step 2: Create `User` entity**

`src/main/java/com/smartparking/entity/User.java`:

```java
package com.smartparking.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    protected User() {}

    public User(String email, String passwordHash, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
}
```

- [ ] **Step 3: Create `UserRepository`**

`src/main/java/com/smartparking/repository/UserRepository.java`:

```java
package com.smartparking.repository;

import com.smartparking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

- [ ] **Step 4: Create `JwtService`**

`src/main/java/com/smartparking/security/JwtService.java`:

```java
package com.smartparking.security;

import com.smartparking.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
```

- [ ] **Step 5: Create `JwtAuthFilter`**

`src/main/java/com/smartparking/security/JwtAuthFilter.java`:

```java
package com.smartparking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isTokenValid(token)) {
                String email = jwtService.extractEmail(token);
                String role = jwtService.extractRole(token);
                var authToken = new UsernamePasswordAuthenticationToken(
                        email, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 6: Create `SecurityConfig`**

`src/main/java/com/smartparking/security/SecurityConfig.java`:

```java
package com.smartparking.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health", "/auth/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 7: Create DTOs**

`src/main/java/com/smartparking/dto/RegisterRequest.java`:

```java
package com.smartparking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password) {
}
```

`src/main/java/com/smartparking/dto/LoginRequest.java`:

```java
package com.smartparking.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
```

`src/main/java/com/smartparking/dto/AuthResponse.java`:

```java
package com.smartparking.dto;

public record AuthResponse(String token) {
}
```

- [ ] **Step 8: Write the failing test for `AuthService`**

`src/test/java/com/smartparking/service/AuthServiceTest.java`:

```java
package com.smartparking.service;

import com.smartparking.entity.Role;
import com.smartparking.entity.User;
import com.smartparking.repository.UserRepository;
import com.smartparking.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private AuthService authService() {
        return new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerCreatesUserWithHashedPasswordAndUserRole() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = authService().register("a@b.com", "password123");

        assertThat(created.getEmail()).isEqualTo("a@b.com");
        assertThat(created.getPasswordHash()).isEqualTo("hashed");
        assertThat(created.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> authService().register("a@b.com", "password123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginReturnsJwtForValidCredentials() {
        User user = new User("a@b.com", "hashed", Role.USER);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        String token = authService().login("a@b.com", "password123");

        assertThat(token).isEqualTo("jwt-token");
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User("a@b.com", "hashed", Role.USER);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService().login("a@b.com", "wrong"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AuthServiceTest`
Expected: FAIL (compilation error — `AuthService` does not exist yet).

- [ ] **Step 3: Implement `AuthService`**

`src/main/java/com/smartparking/service/AuthService.java`:

```java
package com.smartparking.service;

import com.smartparking.entity.Role;
import com.smartparking.entity.User;
import com.smartparking.repository.UserRepository;
import com.smartparking.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User register(String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }
        User user = new User(email, passwordEncoder.encode(rawPassword), Role.USER);
        return userRepository.save(user);
    }

    public String login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        return jwtService.generateToken(user);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AuthServiceTest`
Expected: PASS

- [ ] **Step 5: Create `AuthController`**

`src/main/java/com/smartparking/controller/AuthController.java`:

```java
package com.smartparking.controller;

import com.smartparking.dto.AuthResponse;
import com.smartparking.dto.LoginRequest;
import com.smartparking.dto.RegisterRequest;
import com.smartparking.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.email(), request.password());
        return ResponseEntity.status(201).build();
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.email(), request.password());
        return new AuthResponse(token);
    }
}
```

- [ ] **Step 6: Write the integration test for register+login**

`src/test/java/com/smartparking/controller/AuthControllerIntegrationTest.java`:

```java
package com.smartparking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void registerThenLoginReturnsToken() throws Exception {
        String email = "user" + System.nanoTime() + "@example.com";

        mockMvc.perform(post("/auth/register")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Object() {
                    public String email = email;
                    public String password = "password123";
                })))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Object() {
                    public String email = email;
                    public String password = "password123";
                })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
```

- [ ] **Step 7: Run all tests**

```bash
docker compose up -d
./mvnw test
```

Expected: PASS (all tests green)

- [ ] **Step 8: Commit**

```bash
git add src
git commit -m "feat: JWT auth with register/login and role-based security config"
git push
```

---

## Task 3: Location & ParkingSlot

**Files:**
- Create: `src/main/java/com/smartparking/entity/Location.java`
- Create: `src/main/java/com/smartparking/entity/ParkingSlot.java`
- Create: `src/main/java/com/smartparking/entity/SlotStatus.java`
- Create: `src/main/java/com/smartparking/repository/LocationRepository.java`
- Create: `src/main/java/com/smartparking/repository/ParkingSlotRepository.java`
- Create: `src/main/java/com/smartparking/dto/LocationResponse.java`
- Create: `src/main/java/com/smartparking/dto/SlotResponse.java`
- Create: `src/main/java/com/smartparking/controller/LocationController.java`
- Test: `src/test/java/com/smartparking/controller/LocationControllerIntegrationTest.java`

**Interfaces:**
- Produces: `Location(id, name, address, totalSlots)`; `ParkingSlot(id, location, slotNumber, status)`; `ParkingSlotRepository.findByLocationIdAndStatus(Long locationId, SlotStatus status)`; `GET /locations`, `GET /locations/{id}/slots?available=true`.
- Consumes: `SecurityConfig` from Task 2 (these endpoints require authentication, not a specific role).

- [ ] **Step 1: Create `SlotStatus` enum**

`src/main/java/com/smartparking/entity/SlotStatus.java`:

```java
package com.smartparking.entity;

public enum SlotStatus {
    AVAILABLE, RESERVED, OCCUPIED
}
```

- [ ] **Step 2: Create `Location` entity**

`src/main/java/com/smartparking/entity/Location.java`:

```java
package com.smartparking.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private int totalSlots;

    protected Location() {}

    public Location(String name, String address, int totalSlots) {
        this.name = name;
        this.address = address;
        this.totalSlots = totalSlots;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public int getTotalSlots() { return totalSlots; }
}
```

- [ ] **Step 3: Create `ParkingSlot` entity**

`src/main/java/com/smartparking/entity/ParkingSlot.java`:

```java
package com.smartparking.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "parking_slots")
public class ParkingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(nullable = false)
    private int slotNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status;

    protected ParkingSlot() {}

    public ParkingSlot(Location location, int slotNumber, SlotStatus status) {
        this.location = location;
        this.slotNumber = slotNumber;
        this.status = status;
    }

    public Long getId() { return id; }
    public Location getLocation() { return location; }
    public int getSlotNumber() { return slotNumber; }
    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }
}
```

- [ ] **Step 4: Create repositories**

`src/main/java/com/smartparking/repository/LocationRepository.java`:

```java
package com.smartparking.repository;

import com.smartparking.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {
}
```

`src/main/java/com/smartparking/repository/ParkingSlotRepository.java`:

```java
package com.smartparking.repository;

import com.smartparking.entity.ParkingSlot;
import com.smartparking.entity.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {
    List<ParkingSlot> findByLocationIdAndStatus(Long locationId, SlotStatus status);
    List<ParkingSlot> findByLocationId(Long locationId);
}
```

- [ ] **Step 5: Create response DTOs**

`src/main/java/com/smartparking/dto/LocationResponse.java`:

```java
package com.smartparking.dto;

import com.smartparking.entity.Location;

public record LocationResponse(Long id, String name, String address, int totalSlots) {
    public static LocationResponse from(Location location) {
        return new LocationResponse(location.getId(), location.getName(), location.getAddress(), location.getTotalSlots());
    }
}
```

`src/main/java/com/smartparking/dto/SlotResponse.java`:

```java
package com.smartparking.dto;

import com.smartparking.entity.ParkingSlot;
import com.smartparking.entity.SlotStatus;

public record SlotResponse(Long id, Long locationId, int slotNumber, SlotStatus status) {
    public static SlotResponse from(ParkingSlot slot) {
        return new SlotResponse(slot.getId(), slot.getLocation().getId(), slot.getSlotNumber(), slot.getStatus());
    }
}
```

- [ ] **Step 6: Write the failing integration test**

`src/test/java/com/smartparking/controller/LocationControllerIntegrationTest.java`:

```java
package com.smartparking.controller;

import com.smartparking.entity.Location;
import com.smartparking.entity.ParkingSlot;
import com.smartparking.entity.SlotStatus;
import com.smartparking.repository.LocationRepository;
import com.smartparking.repository.ParkingSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LocationControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ParkingSlotRepository slotRepository;

    @Test
    @WithMockUser
    void listsAvailableSlotsForLocation() throws Exception {
        Location location = locationRepository.save(new Location("Mall Parking", "123 Main St", 2));
        slotRepository.save(new ParkingSlot(location, 1, SlotStatus.AVAILABLE));
        slotRepository.save(new ParkingSlot(location, 2, SlotStatus.OCCUPIED));

        mockMvc.perform(get("/locations/{id}/slots?available=true", location.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=LocationControllerIntegrationTest`
Expected: FAIL (compilation error — `LocationController` does not exist).

- [ ] **Step 3: Implement `LocationController`**

`src/main/java/com/smartparking/controller/LocationController.java`:

```java
package com.smartparking.controller;

import com.smartparking.dto.LocationResponse;
import com.smartparking.dto.SlotResponse;
import com.smartparking.entity.SlotStatus;
import com.smartparking.repository.LocationRepository;
import com.smartparking.repository.ParkingSlotRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/locations")
public class LocationController {

    private final LocationRepository locationRepository;
    private final ParkingSlotRepository slotRepository;

    public LocationController(LocationRepository locationRepository, ParkingSlotRepository slotRepository) {
        this.locationRepository = locationRepository;
        this.slotRepository = slotRepository;
    }

    @GetMapping
    public List<LocationResponse> listLocations() {
        return locationRepository.findAll().stream().map(LocationResponse::from).toList();
    }

    @GetMapping("/{id}/slots")
    public List<SlotResponse> listSlots(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean available) {
        var slots = Boolean.TRUE.equals(available)
                ? slotRepository.findByLocationIdAndStatus(id, SlotStatus.AVAILABLE)
                : slotRepository.findByLocationId(id);
        return slots.stream().map(SlotResponse::from).toList();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=LocationControllerIntegrationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: Location and ParkingSlot entities with availability endpoints"
git push
```

---

## Task 4: Booking Core (overlap-safe creation)

**Files:**
- Create: `src/main/java/com/smartparking/entity/Booking.java`
- Create: `src/main/java/com/smartparking/entity/BookingStatus.java`
- Create: `src/main/java/com/smartparking/repository/BookingRepository.java`
- Create: `src/main/java/com/smartparking/exception/SlotUnavailableException.java`
- Create: `src/main/java/com/smartparking/exception/OverlappingBookingException.java`
- Create: `src/main/java/com/smartparking/exception/BookingNotFoundException.java`
- Create: `src/main/java/com/smartparking/exception/InvalidBookingStateException.java`
- Create: `src/main/java/com/smartparking/exception/GlobalExceptionHandler.java`
- Create: `src/main/java/com/smartparking/dto/CreateBookingRequest.java`
- Create: `src/main/java/com/smartparking/dto/BookingResponse.java`
- Create: `src/main/java/com/smartparking/service/BookingService.java`
- Create: `src/main/java/com/smartparking/controller/BookingController.java`
- Test: `src/test/java/com/smartparking/service/BookingServiceTest.java`
- Test: `src/test/java/com/smartparking/service/BookingServiceConcurrencyTest.java`

**Interfaces:**
- Produces: `Booking(id, user, slot, startTime, endTime, status, createdAt, confirmedAt, totalCost)`; `BookingService.createBooking(String userEmail, Long slotId, Instant start, Instant end)` returns `Booking`; `BookingService.cancelBooking(String userEmail, Long bookingId)`; `BookingRepository.findActiveOverlapping(Long slotId, Instant start, Instant end)` (pessimistic write lock).
- Consumes: `UserRepository` (Task 2), `ParkingSlotRepository` (Task 3).

- [ ] **Step 1: Create `BookingStatus` enum**

`src/main/java/com/smartparking/entity/BookingStatus.java`:

```java
package com.smartparking.entity;

public enum BookingStatus {
    PENDING, CONFIRMED, ACTIVE, COMPLETED, EXPIRED, CANCELLED
}
```

- [ ] **Step 2: Create `Booking` entity**

`src/main/java/com/smartparking/entity/Booking.java`:

```java
package com.smartparking.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "slot_id")
    private ParkingSlot slot;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant confirmedAt;

    private BigDecimal totalCost;

    protected Booking() {}

    public Booking(User user, ParkingSlot slot, Instant startTime, Instant endTime) {
        this.user = user;
        this.slot = slot;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = BookingStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public ParkingSlot getSlot() { return slot; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
}
```

- [ ] **Step 3: Create `BookingRepository` with pessimistic-lock overlap query**

`src/main/java/com/smartparking/repository/BookingRepository.java`:

```java
package com.smartparking.repository;

import com.smartparking.entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT b FROM Booking b
        WHERE b.slot.id = :slotId
          AND b.status IN ('PENDING', 'CONFIRMED', 'ACTIVE')
          AND b.startTime < :end
          AND b.endTime > :start
        """)
    List<Booking> findActiveOverlapping(
            @Param("slotId") Long slotId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    List<Booking> findByStatusAndCreatedAtBefore(
            com.smartparking.entity.BookingStatus status, Instant cutoff);
}
```

- [ ] **Step 4: Create domain exceptions**

`src/main/java/com/smartparking/exception/SlotUnavailableException.java`:

```java
package com.smartparking.exception;

public class SlotUnavailableException extends RuntimeException {
    public SlotUnavailableException(String message) { super(message); }
}
```

`src/main/java/com/smartparking/exception/OverlappingBookingException.java`:

```java
package com.smartparking.exception;

public class OverlappingBookingException extends RuntimeException {
    public OverlappingBookingException(String message) { super(message); }
}
```

`src/main/java/com/smartparking/exception/BookingNotFoundException.java`:

```java
package com.smartparking.exception;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(String message) { super(message); }
}
```

`src/main/java/com/smartparking/exception/InvalidBookingStateException.java`:

```java
package com.smartparking.exception;

public class InvalidBookingStateException extends RuntimeException {
    public InvalidBookingStateException(String message) { super(message); }
}
```

- [ ] **Step 5: Create `GlobalExceptionHandler`**

`src/main/java/com/smartparking/exception/GlobalExceptionHandler.java`:

```java
package com.smartparking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(RuntimeException ex, WebRequest req) {
        return build(HttpStatus.NOT_FOUND, ex, req);
    }

    @ExceptionHandler({OverlappingBookingException.class, SlotUnavailableException.class, InvalidBookingStateException.class})
    public ResponseEntity<Object> handleConflict(RuntimeException ex, WebRequest req) {
        return build(HttpStatus.CONFLICT, ex, req);
    }

    @ExceptionHandler(AnomalyDetectedException.class)
    public ResponseEntity<Object> handleAnomaly(RuntimeException ex, WebRequest req) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex, req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleBadRequest(RuntimeException ex, WebRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex, req);
    }

    private ResponseEntity<Object> build(HttpStatus status, RuntimeException ex, WebRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", ex.getMessage());
        body.put("path", req.getDescription(false).replace("uri=", ""));
        return ResponseEntity.status(status).body(body);
    }
}
```

- [ ] **Step 6: Create `AnomalyDetectedException` (used above, implemented fully in Task 10)**

`src/main/java/com/smartparking/exception/AnomalyDetectedException.java`:

```java
package com.smartparking.exception;

public class AnomalyDetectedException extends RuntimeException {
    public AnomalyDetectedException(String message) { super(message); }
}
```

- [ ] **Step 7: Create DTOs**

`src/main/java/com/smartparking/dto/CreateBookingRequest.java`:

```java
package com.smartparking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateBookingRequest(
        @NotNull Long slotId,
        @NotNull @Future Instant startTime,
        @NotNull @Future Instant endTime) {
}
```

`src/main/java/com/smartparking/dto/BookingResponse.java`:

```java
package com.smartparking.dto;

import com.smartparking.entity.Booking;
import com.smartparking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingResponse(
        Long id, Long slotId, Long locationId, Instant startTime, Instant endTime,
        BookingStatus status, BigDecimal totalCost) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getSlot().getId(),
                booking.getSlot().getLocation().getId(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getStatus(),
                booking.getTotalCost());
    }
}
```

- [ ] **Step 8: Write the failing unit test for overlap rejection**

`src/test/java/com/smartparking/service/BookingServiceTest.java`:

```java
package com.smartparking.service;

import com.smartparking.entity.*;
import com.smartparking.exception.OverlappingBookingException;
import com.smartparking.exception.SlotUnavailableException;
import com.smartparking.repository.BookingRepository;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private ParkingSlotRepository slotRepository;
    @Mock private UserRepository userRepository;

    private BookingService service() {
        return new BookingService(bookingRepository, slotRepository, userRepository);
    }

    @Test
    void createBookingSucceedsWhenNoOverlap() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.AVAILABLE);
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(slotRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findActiveOverlapping(1L, start, end)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking booking = service().createBooking("a@b.com", 1L, start, end);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getSlot()).isEqualTo(slot);
    }

    @Test
    void createBookingRejectsOverlap() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.AVAILABLE);
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Booking existing = new Booking(user, slot, start, end);

        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(slotRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findActiveOverlapping(1L, start, end)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service().createBooking("a@b.com", 1L, start, end))
                .isInstanceOf(OverlappingBookingException.class);
    }

    @Test
    void createBookingRejectsOccupiedSlot() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.OCCUPIED);
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(slotRepository.findById(1L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service().createBooking("a@b.com", 1L, start, end))
                .isInstanceOf(SlotUnavailableException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=BookingServiceTest`
Expected: FAIL (compilation error — `BookingService` does not exist).

- [ ] **Step 3: Implement `BookingService`**

`src/main/java/com/smartparking/service/BookingService.java`:

```java
package com.smartparking.service;

import com.smartparking.entity.*;
import com.smartparking.exception.BookingNotFoundException;
import com.smartparking.exception.InvalidBookingStateException;
import com.smartparking.exception.OverlappingBookingException;
import com.smartparking.exception.SlotUnavailableException;
import com.smartparking.repository.BookingRepository;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ParkingSlotRepository slotRepository;
    private final UserRepository userRepository;

    public BookingService(BookingRepository bookingRepository, ParkingSlotRepository slotRepository, UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Booking createBooking(String userEmail, Long slotId, Instant startTime, Instant endTime) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + userEmail));
        ParkingSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new SlotUnavailableException("Slot not found: " + slotId));

        if (slot.getStatus() == SlotStatus.OCCUPIED) {
            throw new SlotUnavailableException("Slot is currently occupied: " + slotId);
        }

        // Pessimistic write lock on overlapping active bookings for this slot,
        // held for the rest of this transaction, so a concurrent request for
        // the same slot/window blocks here until this transaction commits.
        var overlapping = bookingRepository.findActiveOverlapping(slotId, startTime, endTime);
        if (!overlapping.isEmpty()) {
            throw new OverlappingBookingException("Slot " + slotId + " is already booked for that time window");
        }

        Booking booking = new Booking(user, slot, startTime, endTime);
        slot.setStatus(SlotStatus.RESERVED);
        return bookingRepository.save(booking);
    }

    @Transactional
    public void cancelBooking(String userEmail, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("Booking does not belong to this user");
        }
        if (booking.getStatus() == BookingStatus.ACTIVE
                || booking.getStatus() == BookingStatus.COMPLETED
                || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidBookingStateException("Cannot cancel booking in state " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.getSlot().setStatus(SlotStatus.AVAILABLE);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=BookingServiceTest`
Expected: PASS

- [ ] **Step 5: Write the concurrency test**

`src/test/java/com/smartparking/service/BookingServiceConcurrencyTest.java`:

```java
package com.smartparking.service;

import com.smartparking.entity.Location;
import com.smartparking.entity.ParkingSlot;
import com.smartparking.entity.Role;
import com.smartparking.entity.SlotStatus;
import com.smartparking.entity.User;
import com.smartparking.exception.OverlappingBookingException;
import com.smartparking.repository.LocationRepository;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookingServiceConcurrencyTest {

    @Autowired private BookingService bookingService;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ParkingSlotRepository slotRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void onlyOneConcurrentBookingSucceedsForOverlappingWindow() throws InterruptedException {
        Location location = locationRepository.save(new Location("Concurrency Test Lot", "addr", 1));
        ParkingSlot slot = slotRepository.save(new ParkingSlot(location, 1, SlotStatus.AVAILABLE));

        int threadCount = 10;
        List<User> users = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            users.add(userRepository.save(new User(
                    "concurrent" + i + "_" + System.nanoTime() + "@test.com",
                    passwordEncoder.encode("password123"),
                    Role.USER)));
        }

        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            User user = users.get(i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    bookingService.createBooking(user.getEmail(), slot.getId(), start, end);
                    successCount.incrementAndGet();
                } catch (OverlappingBookingException e) {
                    conflictCount.incrementAndGet();
                } catch (InterruptedException ignored) {
                }
            }));
        }

        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            try { f.get(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
    }
}
```

- [ ] **Step 6: Run the concurrency test**

Run: `./mvnw test -Dtest=BookingServiceConcurrencyTest`
Expected: PASS (exactly 1 success, 9 conflicts). If it's flaky, verify the
`findActiveOverlapping` query's `@Lock(PESSIMISTIC_WRITE)` is actually being
applied (check Hibernate SQL logs for `for update`) — MySQL/H2 do not support
row locks the same way; this test requires the real Postgres from
`docker compose up -d`.

- [ ] **Step 7: Create `BookingController`**

`src/main/java/com/smartparking/controller/BookingController.java`:

```java
package com.smartparking.controller;

import com.smartparking.dto.BookingResponse;
import com.smartparking.dto.CreateBookingRequest;
import com.smartparking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody CreateBookingRequest request, Authentication auth) {
        var booking = bookingService.createBooking(
                auth.getName(), request.slotId(), request.startTime(), request.endTime());
        return ResponseEntity.status(201).body(BookingResponse.from(booking));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id, Authentication auth) {
        bookingService.cancelBooking(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 8: Run all tests**

```bash
./mvnw test
```

Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add src
git commit -m "feat: booking core with pessimistic-lock overlap prevention and global exception handling"
git push
```

---

## Task 5: Booking Lifecycle (confirm / check-in / check-out)

**Files:**
- Modify: `src/main/java/com/smartparking/service/BookingService.java`
- Modify: `src/main/java/com/smartparking/controller/BookingController.java`
- Modify: `src/main/java/com/smartparking/repository/BookingRepository.java`
- Test: `src/test/java/com/smartparking/service/BookingServiceTest.java`

**Interfaces:**
- Produces: `BookingService.confirmBooking(String userEmail, Long id)`, `.checkIn(String userEmail, Long id)`, `.checkOut(String userEmail, Long id)` — `checkOut` sets `totalCost` to `null` for now (wired to `PricingService` in Task 7).
- Consumes: `PricingService` — not yet created; `checkOut` in this task computes a placeholder cost of `null` and Task 7 modifies it to call `PricingService`. (This keeps Task 5 self-contained without inventing pricing logic ahead of its own task.)

- [ ] **Step 1: Add failing tests for lifecycle transitions to `BookingServiceTest`**

Append to `src/test/java/com/smartparking/service/BookingServiceTest.java` (inside the class body, before the final closing brace):

```java
    @Test
    void confirmMovesBookingFromPendingToConfirmed() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.RESERVED);
        Booking booking = new Booking(user, slot, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        Booking result = service().confirmBooking("a@b.com", 1L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.getConfirmedAt()).isNotNull();
    }

    @Test
    void checkInMovesConfirmedToActiveAndOccupiesSlot() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.RESERVED);
        Booking booking = new Booking(user, slot, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        Booking result = service().checkIn("a@b.com", 1L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.OCCUPIED);
    }

    @Test
    void checkInRejectsBookingNotYetConfirmed() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.RESERVED);
        Booking booking = new Booking(user, slot, Instant.now(), Instant.now().plusSeconds(3600));

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service().checkIn("a@b.com", 1L))
                .isInstanceOf(com.smartparking.exception.InvalidBookingStateException.class);
    }

    @Test
    void checkOutMovesActiveToCompletedAndFreesSlot() {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.OCCUPIED);
        Booking booking = new Booking(user, slot, Instant.now().minusSeconds(3600), Instant.now());
        booking.setStatus(BookingStatus.ACTIVE);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        Booking result = service().checkOut("a@b.com", 1L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }
```

Also add these imports if not already present at the top of the test file:
`import static org.junit.jupiter.api.Assertions.*;` is not needed; the existing
`assertThat`/`assertThatThrownBy` static imports already cover it.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=BookingServiceTest`
Expected: FAIL (compilation error — `confirmBooking`, `checkIn`, `checkOut` don't exist yet).

- [ ] **Step 3: Implement lifecycle methods in `BookingService`**

Add to `src/main/java/com/smartparking/service/BookingService.java` (inside the class, after `cancelBooking`):

```java
    @Transactional
    public Booking confirmBooking(String userEmail, Long bookingId) {
        Booking booking = getOwnedBooking(userEmail, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException("Cannot confirm booking in state " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(Instant.now());
        return booking;
    }

    @Transactional
    public Booking checkIn(String userEmail, Long bookingId) {
        Booking booking = getOwnedBooking(userEmail, bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException("Cannot check in booking in state " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.ACTIVE);
        booking.getSlot().setStatus(SlotStatus.OCCUPIED);
        return booking;
    }

    @Transactional
    public Booking checkOut(String userEmail, Long bookingId) {
        Booking booking = getOwnedBooking(userEmail, bookingId);
        if (booking.getStatus() != BookingStatus.ACTIVE) {
            throw new InvalidBookingStateException("Cannot check out booking in state " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.COMPLETED);
        booking.getSlot().setStatus(SlotStatus.AVAILABLE);
        return booking;
    }

    private Booking getOwnedBooking(String userEmail, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new IllegalArgumentException("Booking does not belong to this user");
        }
        return booking;
    }
```

Also refactor `cancelBooking` to reuse `getOwnedBooking` (replace its first two
lines with `Booking booking = getOwnedBooking(userEmail, bookingId);`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -Dtest=BookingServiceTest`
Expected: PASS

- [ ] **Step 5: Add endpoints to `BookingController`**

Add to `src/main/java/com/smartparking/controller/BookingController.java` (inside the class, after `cancel`):

```java
    @PostMapping("/{id}/confirm")
    public BookingResponse confirm(@PathVariable Long id, Authentication auth) {
        return BookingResponse.from(bookingService.confirmBooking(auth.getName(), id));
    }

    @PostMapping("/{id}/checkin")
    public BookingResponse checkIn(@PathVariable Long id, Authentication auth) {
        return BookingResponse.from(bookingService.checkIn(auth.getName(), id));
    }

    @PostMapping("/{id}/checkout")
    public BookingResponse checkOut(@PathVariable Long id, Authentication auth) {
        return BookingResponse.from(bookingService.checkOut(auth.getName(), id));
    }
```

- [ ] **Step 6: Run all tests**

```bash
./mvnw test
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src
git commit -m "feat: booking lifecycle transitions (confirm, check-in, check-out)"
git push
```

---

## Task 6: Scheduled Expiry Job

**Files:**
- Create: `src/main/java/com/smartparking/scheduler/BookingExpiryScheduler.java`
- Modify: `src/main/java/com/smartparking/service/BookingService.java`
- Test: `src/test/java/com/smartparking/scheduler/BookingExpiryTest.java`

**Interfaces:**
- Produces: `BookingService.expireStalePendingBookings(Duration pendingExpiry)` returns `int` (count expired); `@Scheduled` job in `BookingExpiryScheduler` runs every minute calling it with the configured expiry window.
- Consumes: `BookingRepository.findByStatusAndCreatedAtBefore` (already added in Task 4).

- [ ] **Step 1: Write the failing unit test for expiry logic**

`src/test/java/com/smartparking/scheduler/BookingExpiryTest.java`:

```java
package com.smartparking.scheduler;

import com.smartparking.entity.*;
import com.smartparking.repository.BookingRepository;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.UserRepository;
import com.smartparking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingExpiryTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private ParkingSlotRepository slotRepository;
    @Mock private UserRepository userRepository;

    @Test
    void expiresStalePendingBookingsAndFreesSlots() {
        BookingService service = new BookingService(bookingRepository, slotRepository, userRepository);

        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.RESERVED);
        Booking stale = new Booking(user, slot, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));

        when(bookingRepository.findByStatusAndCreatedAtBefore(eq(BookingStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(stale));

        int expiredCount = service.expireStalePendingBookings(Duration.ofMinutes(10));

        assertThat(expiredCount).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=BookingExpiryTest`
Expected: FAIL (compilation error — `expireStalePendingBookings` doesn't exist).

- [ ] **Step 3: Implement `expireStalePendingBookings` in `BookingService`**

Add to `src/main/java/com/smartparking/service/BookingService.java` (inside the class):

```java
    @Transactional
    public int expireStalePendingBookings(java.time.Duration pendingExpiry) {
        Instant cutoff = Instant.now().minus(pendingExpiry);
        var stale = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff);
        for (Booking booking : stale) {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.getSlot().setStatus(SlotStatus.AVAILABLE);
        }
        return stale.size();
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=BookingExpiryTest`
Expected: PASS

- [ ] **Step 5: Create the scheduler**

`src/main/java/com/smartparking/scheduler/BookingExpiryScheduler.java`:

```java
package com.smartparking.scheduler;

import com.smartparking.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BookingExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryScheduler.class);

    private final BookingService bookingService;
    private final long pendingExpiryMinutes;

    public BookingExpiryScheduler(
            BookingService bookingService,
            @Value("${app.booking.pending-expiry-minutes}") long pendingExpiryMinutes) {
        this.bookingService = bookingService;
        this.pendingExpiryMinutes = pendingExpiryMinutes;
    }

    @Scheduled(fixedRate = 60_000)
    public void expirePendingBookings() {
        int expired = bookingService.expireStalePendingBookings(Duration.ofMinutes(pendingExpiryMinutes));
        if (expired > 0) {
            log.info("Expired {} stale pending booking(s)", expired);
        }
    }
}
```

- [ ] **Step 6: Run all tests**

```bash
./mvnw test
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src
git commit -m "feat: scheduled job to expire unconfirmed pending bookings"
git push
```

---

## Task 7: Dynamic Pricing

**Files:**
- Create: `src/main/java/com/smartparking/service/PricingService.java`
- Modify: `src/main/java/com/smartparking/service/BookingService.java`
- Modify: `src/main/java/com/smartparking/controller/BookingController.java` (no change needed — `checkOut` already returns the updated `BookingResponse`)
- Test: `src/test/java/com/smartparking/service/PricingServiceTest.java`
- Test: `src/test/java/com/smartparking/service/BookingServiceTest.java` (modify `checkOutMovesActiveToCompletedAndFreesSlot`)

**Interfaces:**
- Produces: `PricingService.calculateCost(Instant start, Instant end, double predictedOccupancyPct)` returns `BigDecimal`.
- Consumes: `BookingService.checkOut` now takes a `PricingService` dependency; the predicted occupancy used at checkout comes from an in-memory cache populated in Task 8 — until Task 8 exists, `checkOut` passes `0.5` (50%, "unknown" baseline) as the occupancy, and Task 8 wires the real prediction cache in.

- [ ] **Step 1: Write the failing test for `PricingService`**

`src/test/java/com/smartparking/service/PricingServiceTest.java`:

```java
package com.smartparking.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private final PricingService service = new PricingService(new BigDecimal("2.50"));

    @Test
    void appliesSurgeMultiplierForHighPredictedOccupancy() {
        Instant start = Instant.now();
        Instant end = start.plus(2, ChronoUnit.HOURS);

        BigDecimal cost = service.calculateCost(start, end, 0.85);

        // 2 hours * 2.50 * 1.5 = 7.50
        assertThat(cost).isEqualByComparingTo("7.50");
    }

    @Test
    void appliesDiscountForLowPredictedOccupancy() {
        Instant start = Instant.now();
        Instant end = start.plus(2, ChronoUnit.HOURS);

        BigDecimal cost = service.calculateCost(start, end, 0.20);

        // 2 hours * 2.50 * 0.8 = 4.00
        assertThat(cost).isEqualByComparingTo("4.00");
    }

    @Test
    void appliesStandardRateForModeratePredictedOccupancy() {
        Instant start = Instant.now();
        Instant end = start.plus(1, ChronoUnit.HOURS);

        BigDecimal cost = service.calculateCost(start, end, 0.5);

        // 1 hour * 2.50 * 1.0 = 2.50
        assertThat(cost).isEqualByComparingTo("2.50");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=PricingServiceTest`
Expected: FAIL (compilation error — `PricingService` doesn't exist).

- [ ] **Step 3: Implement `PricingService`**

`src/main/java/com/smartparking/service/PricingService.java`:

```java
package com.smartparking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Service
public class PricingService {

    private static final BigDecimal HIGH_OCCUPANCY_THRESHOLD = new BigDecimal("0.75");
    private static final BigDecimal LOW_OCCUPANCY_THRESHOLD = new BigDecimal("0.30");
    private static final BigDecimal SURGE_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal DISCOUNT_MULTIPLIER = new BigDecimal("0.8");
    private static final BigDecimal STANDARD_MULTIPLIER = BigDecimal.ONE;

    private final BigDecimal baseHourlyRate;

    public PricingService(@Value("${app.pricing.base-hourly-rate}") BigDecimal baseHourlyRate) {
        this.baseHourlyRate = baseHourlyRate;
    }

    public BigDecimal calculateCost(Instant start, Instant end, double predictedOccupancyPct) {
        BigDecimal hours = BigDecimal.valueOf(Duration.between(start, end).toMinutes())
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        BigDecimal occupancy = BigDecimal.valueOf(predictedOccupancyPct);
        BigDecimal multiplier = surgeMultiplierFor(occupancy);
        return hours.multiply(baseHourlyRate).multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal surgeMultiplierFor(BigDecimal predictedOccupancyPct) {
        if (predictedOccupancyPct.compareTo(HIGH_OCCUPANCY_THRESHOLD) > 0) {
            return SURGE_MULTIPLIER;
        }
        if (predictedOccupancyPct.compareTo(LOW_OCCUPANCY_THRESHOLD) < 0) {
            return DISCOUNT_MULTIPLIER;
        }
        return STANDARD_MULTIPLIER;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=PricingServiceTest`
Expected: PASS

- [ ] **Step 5: Wire `PricingService` into `BookingService.checkOut`**

Modify `src/main/java/com/smartparking/service/BookingService.java`: add a
`PricingService` field/constructor parameter, and update `checkOut`:

```java
    private final PricingService pricingService;

    public BookingService(BookingRepository bookingRepository, ParkingSlotRepository slotRepository,
                           UserRepository userRepository, PricingService pricingService) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.pricingService = pricingService;
    }
```

(Remove the old 3-argument constructor.) Then update `checkOut`:

```java
    @Transactional
    public Booking checkOut(String userEmail, Long bookingId) {
        Booking booking = getOwnedBooking(userEmail, bookingId);
        if (booking.getStatus() != BookingStatus.ACTIVE) {
            throw new InvalidBookingStateException("Cannot check out booking in state " + booking.getStatus());
        }
        // Placeholder occupancy of 0.5 until Task 8 wires in the real
        // AI-predicted occupancy cache.
        double predictedOccupancy = 0.5;
        var cost = pricingService.calculateCost(booking.getStartTime(), booking.getEndTime(), predictedOccupancy);
        booking.setTotalCost(cost);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.getSlot().setStatus(SlotStatus.AVAILABLE);
        return booking;
    }
```

- [ ] **Step 6: Update `BookingServiceTest` and `BookingExpiryTest` constructor calls**

In `src/test/java/com/smartparking/service/BookingServiceTest.java`, add a
`@Mock private PricingService pricingService;` field, update the `service()`
helper to `new BookingService(bookingRepository, slotRepository, userRepository, pricingService)`,
and in `checkOutMovesActiveToCompletedAndFreesSlot` add before the `checkOut` call:

```java
        when(pricingService.calculateCost(any(Instant.class), any(Instant.class), org.mockito.ArgumentMatchers.anyDouble()))
                .thenReturn(new java.math.BigDecimal("5.00"));
```

and assert `assertThat(result.getTotalCost()).isEqualByComparingTo("5.00");`.

In `src/test/java/com/smartparking/scheduler/BookingExpiryTest.java`, add a
`@Mock private PricingService pricingService;` field and update the
`new BookingService(...)` call to include it as the 4th argument.

- [ ] **Step 7: Run all tests**

```bash
./mvnw test
```

Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src
git commit -m "feat: dynamic pricing with occupancy-based surge/discount multiplier"
git push
```

---

## Task 8: AI — Demand Prediction (Gemini)

**Files:**
- Create: `src/main/java/com/smartparking/ai/AiAssistantService.java`
- Create: `src/main/java/com/smartparking/ai/OccupancyPrediction.java`
- Create: `src/main/java/com/smartparking/ai/HourlyBookingCount.java`
- Create: `src/main/java/com/smartparking/ai/GeminiAssistantService.java`
- Create: `src/main/java/com/smartparking/ai/PredictionCache.java`
- Create: `src/main/java/com/smartparking/dto/PredictionResponse.java`
- Create: `src/main/java/com/smartparking/controller/PredictionController.java`
- Modify: `src/main/java/com/smartparking/service/BookingService.java`
- Modify: `src/main/java/com/smartparking/repository/BookingRepository.java`
- Test: `src/test/java/com/smartparking/ai/PredictionCacheTest.java`
- Test: `src/test/java/com/smartparking/controller/PredictionControllerTest.java`

**Interfaces:**
- Produces: `AiAssistantService.predictOccupancy(String locationName, List<HourlyBookingCount> history)` returns `OccupancyPrediction(double predictedOccupancyPct, String reasoning)`; `PredictionCache.get(Long locationId)` / `.put(Long locationId, OccupancyPrediction)` with a 5-minute TTL.
- Consumes: `BookingService.checkOut` (Task 7) — modified here to read from `PredictionCache` instead of the hardcoded `0.5`.

- [ ] **Step 1: Create the `AiAssistantService` interface and supporting records**

`src/main/java/com/smartparking/ai/HourlyBookingCount.java`:

```java
package com.smartparking.ai;

public record HourlyBookingCount(int hourOfDay, int dayOfWeek, long bookingCount) {
}
```

`src/main/java/com/smartparking/ai/OccupancyPrediction.java`:

```java
package com.smartparking.ai;

public record OccupancyPrediction(double predictedOccupancyPct, String reasoning) {
}
```

`src/main/java/com/smartparking/ai/AiAssistantService.java`:

```java
package com.smartparking.ai;

import java.util.List;

public interface AiAssistantService {

    OccupancyPrediction predictOccupancy(String locationName, List<HourlyBookingCount> history);

    BookingIntent parseBookingIntent(String message, List<String> knownLocationNames);
}
```

`src/main/java/com/smartparking/ai/BookingIntent.java`:

```java
package com.smartparking.ai;

import java.time.Instant;

public record BookingIntent(String locationName, Instant startTime, int durationMinutes) {
}
```

(`BookingIntent` is created here because `AiAssistantService` must reference it,
but is only *used* starting in Task 9 — this keeps the interface's full shape
stable across both AI tasks.)

- [ ] **Step 2: Create `PredictionCache` and its failing test**

`src/test/java/com/smartparking/ai/PredictionCacheTest.java`:

```java
package com.smartparking.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionCacheTest {

    @Test
    void returnsCachedValueBeforeExpiry() {
        PredictionCache cache = new PredictionCache(Duration.ofMinutes(5));
        OccupancyPrediction prediction = new OccupancyPrediction(0.6, "test");

        cache.put(1L, prediction);

        assertThat(cache.get(1L)).contains(prediction);
    }

    @Test
    void returnsEmptyForUncachedLocation() {
        PredictionCache cache = new PredictionCache(Duration.ofMinutes(5));

        assertThat(cache.get(99L)).isEmpty();
    }

    @Test
    void returnsEmptyAfterTtlExpires() throws InterruptedException {
        PredictionCache cache = new PredictionCache(Duration.ofMillis(50));
        cache.put(1L, new OccupancyPrediction(0.6, "test"));

        Thread.sleep(100);

        assertThat(cache.get(1L)).isEmpty();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw test -Dtest=PredictionCacheTest`
Expected: FAIL (compilation error — `PredictionCache` doesn't exist).

- [ ] **Step 4: Implement `PredictionCache`**

`src/main/java/com/smartparking/ai/PredictionCache.java`:

```java
package com.smartparking.ai;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PredictionCache {

    private record Entry(OccupancyPrediction prediction, Instant expiresAt) {}

    private final Duration ttl;
    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();

    public PredictionCache() {
        this(Duration.ofMinutes(5));
    }

    public PredictionCache(Duration ttl) {
        this.ttl = ttl;
    }

    public void put(Long locationId, OccupancyPrediction prediction) {
        entries.put(locationId, new Entry(prediction, Instant.now().plus(ttl)));
    }

    public Optional<OccupancyPrediction> get(Long locationId) {
        Entry entry = entries.get(locationId);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            entries.remove(locationId);
            return Optional.empty();
        }
        return Optional.of(entry.prediction());
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=PredictionCacheTest`
Expected: PASS

- [ ] **Step 6: Implement `GeminiAssistantService` (predictOccupancy only; parseBookingIntent stubbed for now)**

`src/main/java/com/smartparking/ai/GeminiAssistantService.java`:

```java
package com.smartparking.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GeminiAssistantService implements AiAssistantService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public GeminiAssistantService(
            @Value("${app.ai.gemini.base-url}") String baseUrl,
            @Value("${app.ai.gemini.api-key}") String apiKey,
            @Value("${app.ai.gemini.model}") String model) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public OccupancyPrediction predictOccupancy(String locationName, List<HourlyBookingCount> history) {
        String historyText = history.stream()
                .map(h -> "day=%d hour=%d bookings=%d".formatted(h.dayOfWeek(), h.hourOfDay(), h.bookingCount()))
                .collect(Collectors.joining("\n"));

        String prompt = """
                You are a parking demand forecaster. Given this historical hourly booking
                count data for parking location "%s" (day 1=Monday..7=Sunday), predict the
                expected occupancy percentage for the NEXT hour. Reply with ONLY a JSON
                object matching exactly: {"predictedOccupancyPct": <number 0.0-1.0>, "reasoning": "<one sentence>"}

                Historical data:
                %s
                """.formatted(locationName, historyText);

        ObjectNode requestBody = objectMapper.createObjectNode();
        ObjectNode content = requestBody.putArray("contents").addObject();
        content.putArray("parts").addObject().put("text", prompt);
        ObjectNode generationConfig = requestBody.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");

        JsonNode response = restClient.post()
                .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        String text = response
                .path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText();

        try {
            JsonNode parsed = objectMapper.readTree(text);
            return new OccupancyPrediction(
                    parsed.path("predictedOccupancyPct").asDouble(0.5),
                    parsed.path("reasoning").asText(""));
        } catch (Exception e) {
            return new OccupancyPrediction(0.5, "Fallback: could not parse model response");
        }
    }

    @Override
    public BookingIntent parseBookingIntent(String message, List<String> knownLocationNames) {
        throw new UnsupportedOperationException("Implemented in Task 9");
    }
}
```

- [ ] **Step 7: Add the query needed to build history, and wire the controller**

Add to `src/main/java/com/smartparking/repository/BookingRepository.java`:

```java
    @Query("""
        SELECT FUNCTION('EXTRACT', HOUR FROM b.startTime) as hourOfDay,
               FUNCTION('EXTRACT', ISODOW FROM b.startTime) as dayOfWeek,
               COUNT(b) as bookingCount
        FROM Booking b
        WHERE b.slot.location.id = :locationId
        GROUP BY FUNCTION('EXTRACT', HOUR FROM b.startTime), FUNCTION('EXTRACT', ISODOW FROM b.startTime)
        """)
    List<Object[]> aggregateHourlyBookingCounts(@Param("locationId") Long locationId);
```

`src/main/java/com/smartparking/dto/PredictionResponse.java`:

```java
package com.smartparking.dto;

public record PredictionResponse(Long locationId, double predictedOccupancyPct, String reasoning) {
}
```

`src/main/java/com/smartparking/controller/PredictionController.java`:

```java
package com.smartparking.controller;

import com.smartparking.ai.AiAssistantService;
import com.smartparking.ai.HourlyBookingCount;
import com.smartparking.ai.OccupancyPrediction;
import com.smartparking.ai.PredictionCache;
import com.smartparking.dto.PredictionResponse;
import com.smartparking.repository.BookingRepository;
import com.smartparking.repository.LocationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PredictionController {

    private final AiAssistantService aiAssistantService;
    private final PredictionCache predictionCache;
    private final BookingRepository bookingRepository;
    private final LocationRepository locationRepository;

    public PredictionController(AiAssistantService aiAssistantService, PredictionCache predictionCache,
                                 BookingRepository bookingRepository, LocationRepository locationRepository) {
        this.aiAssistantService = aiAssistantService;
        this.predictionCache = predictionCache;
        this.bookingRepository = bookingRepository;
        this.locationRepository = locationRepository;
    }

    @GetMapping("/predictions/{locationId}")
    public PredictionResponse predict(@PathVariable Long locationId) {
        var cached = predictionCache.get(locationId);
        if (cached.isPresent()) {
            return new PredictionResponse(locationId, cached.get().predictedOccupancyPct(), cached.get().reasoning());
        }

        var location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown location: " + locationId));

        List<HourlyBookingCount> history = bookingRepository.aggregateHourlyBookingCounts(locationId).stream()
                .map(row -> new HourlyBookingCount(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).intValue(),
                        ((Number) row[2]).longValue()))
                .toList();

        OccupancyPrediction prediction = aiAssistantService.predictOccupancy(location.getName(), history);
        predictionCache.put(locationId, prediction);
        return new PredictionResponse(locationId, prediction.predictedOccupancyPct(), prediction.reasoning());
    }
}
```

- [ ] **Step 8: Write a controller test with a mocked `AiAssistantService`**

`src/test/java/com/smartparking/controller/PredictionControllerTest.java`:

```java
package com.smartparking.controller;

import com.smartparking.ai.AiAssistantService;
import com.smartparking.ai.OccupancyPrediction;
import com.smartparking.entity.Location;
import com.smartparking.repository.LocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PredictionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LocationRepository locationRepository;
    @MockBean private AiAssistantService aiAssistantService;

    @Test
    @WithMockUser
    void returnsPredictionFromAiAssistantService() throws Exception {
        Location location = locationRepository.save(new Location("Downtown Lot", "addr", 5));
        when(aiAssistantService.predictOccupancy(anyString(), any(List.class)))
                .thenReturn(new OccupancyPrediction(0.82, "High historical demand at this hour"));

        mockMvc.perform(get("/predictions/{id}", location.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.predictedOccupancyPct").value(0.82))
            .andExpect(jsonPath("$.reasoning").value("High historical demand at this hour"));
    }
}
```

- [ ] **Step 9: Run the test**

Run: `./mvnw test -Dtest=PredictionControllerTest`
Expected: PASS (the real `GeminiAssistantService` bean still gets created at
context startup since `@MockBean` replaces it, but note `GeminiAssistantService`'s
constructor only reads config values — it does not call the network at
construction time, so no API key is required for this test to pass).

- [ ] **Step 10: Wire `PredictionCache` into `BookingService.checkOut`**

Modify `src/main/java/com/smartparking/service/BookingService.java`: add a
`PredictionCache` constructor parameter, and replace the hardcoded `0.5` in
`checkOut`:

```java
    private final PredictionCache predictionCache;

    public BookingService(BookingRepository bookingRepository, ParkingSlotRepository slotRepository,
                           UserRepository userRepository, PricingService pricingService,
                           PredictionCache predictionCache) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.pricingService = pricingService;
        this.predictionCache = predictionCache;
    }
```

And in `checkOut`, replace:

```java
        double predictedOccupancy = 0.5;
```

with:

```java
        double predictedOccupancy = predictionCache.get(booking.getSlot().getLocation().getId())
                .map(com.smartparking.ai.OccupancyPrediction::predictedOccupancyPct)
                .orElse(0.5);
```

- [ ] **Step 11: Update existing test constructor calls**

In `BookingServiceTest.java` and `BookingExpiryTest.java`, add a
`@Mock private PredictionCache predictionCache;` field to each, and add
`predictionCache` as the 5th argument to every `new BookingService(...)` call.
In `BookingServiceTest`'s `checkOutMovesActiveToCompletedAndFreesSlot`, add
before the `checkOut` call:

```java
        when(predictionCache.get(any())).thenReturn(java.util.Optional.empty());
```

- [ ] **Step 12: Run all tests**

```bash
./mvnw test
```

Expected: PASS

- [ ] **Step 13: Commit**

```bash
git add src
git commit -m "feat: AI-powered demand prediction via Gemini with a TTL cache feeding dynamic pricing"
git push
```

---

## Task 9: AI — Natural-Language Booking Assistant

**Files:**
- Modify: `src/main/java/com/smartparking/ai/GeminiAssistantService.java`
- Create: `src/main/java/com/smartparking/dto/AssistantBookingRequest.java`
- Create: `src/main/java/com/smartparking/controller/AssistantController.java`
- Modify: `src/main/java/com/smartparking/service/BookingService.java` (add a locationName-based overload)
- Test: `src/test/java/com/smartparking/controller/AssistantControllerTest.java`

**Interfaces:**
- Produces: `AiAssistantService.parseBookingIntent(String message, List<String> knownLocationNames)` returns `BookingIntent(locationName, startTime, durationMinutes)`; `BookingService.createBookingByLocationName(String userEmail, String locationName, Instant start, Instant end)`.
- Consumes: `LocationRepository`, `ParkingSlotRepository` (Task 3), `BookingService.createBooking` (Task 4).

- [ ] **Step 1: Implement `parseBookingIntent` in `GeminiAssistantService` using Gemini function calling**

Replace the `parseBookingIntent` stub in
`src/main/java/com/smartparking/ai/GeminiAssistantService.java`:

```java
    @Override
    public BookingIntent parseBookingIntent(String message, List<String> knownLocationNames) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        ObjectNode content = requestBody.putArray("contents").addObject();
        content.putArray("parts").addObject().put("text", """
                Current UTC time is %s. Known parking location names: %s.
                Parse the user's booking request and call create_booking with
                the closest matching location name from the known list, an ISO-8601
                UTC start time, and a duration in minutes. If no start time is given,
                assume now. If no duration is given, assume 60 minutes.

                User request: "%s"
                """.formatted(java.time.Instant.now(), knownLocationNames, message));

        ObjectNode tool = requestBody.putArray("tools").addObject();
        ObjectNode functionDeclaration = tool.putArray("functionDeclarations").addObject();
        functionDeclaration.put("name", "create_booking");
        functionDeclaration.put("description", "Create a parking booking from parsed intent");
        ObjectNode parameters = functionDeclaration.putObject("parameters");
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        properties.putObject("locationName").put("type", "string");
        properties.putObject("startTime").put("type", "string").put("description", "ISO-8601 UTC datetime");
        properties.putObject("durationMinutes").put("type", "integer");
        parameters.putArray("required").add("locationName").add("startTime").add("durationMinutes");

        JsonNode response = restClient.post()
                .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        JsonNode functionCall = response
                .path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("functionCall");

        if (functionCall.isMissingNode()) {
            throw new IllegalArgumentException("Could not understand booking request: " + message);
        }

        JsonNode args = functionCall.path("args");
        String locationName = args.path("locationName").asText();
        java.time.Instant startTime = java.time.Instant.parse(args.path("startTime").asText());
        int durationMinutes = args.path("durationMinutes").asInt(60);

        return new BookingIntent(locationName, startTime, durationMinutes);
    }
```

- [ ] **Step 2: Add a locationName-based booking overload to `BookingService`**

Add to `src/main/java/com/smartparking/service/BookingService.java` (inside
the class); needs `LocationRepository` and `ParkingSlotRepository` (already
injected as `slotRepository`) — add a `LocationRepository` field/constructor
parameter:

```java
    private final com.smartparking.repository.LocationRepository locationRepository;

    // add locationRepository as a 6th constructor parameter, after predictionCache:
    public BookingService(BookingRepository bookingRepository, ParkingSlotRepository slotRepository,
                           UserRepository userRepository, PricingService pricingService,
                           PredictionCache predictionCache, com.smartparking.repository.LocationRepository locationRepository) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.pricingService = pricingService;
        this.predictionCache = predictionCache;
        this.locationRepository = locationRepository;
    }

    @Transactional
    public Booking createBookingByLocationName(String userEmail, String locationName, Instant startTime, Instant endTime) {
        var location = locationRepository.findAll().stream()
                .filter(l -> l.getName().equalsIgnoreCase(locationName))
                .findFirst()
                .orElseThrow(() -> new SlotUnavailableException("Unknown location: " + locationName));

        var availableSlot = slotRepository.findByLocationIdAndStatus(location.getId(), SlotStatus.AVAILABLE)
                .stream().findFirst()
                .orElseThrow(() -> new SlotUnavailableException("No available slots at " + locationName));

        return createBooking(userEmail, availableSlot.getId(), startTime, endTime);
    }
```

(Remove the old 5-argument constructor, keeping only this 6-argument one.)

- [ ] **Step 3: Update test constructor calls again**

In `BookingServiceTest.java` and `BookingExpiryTest.java`, add
`@Mock private com.smartparking.repository.LocationRepository locationRepository;`
and add it as the 6th argument to every `new BookingService(...)` call.

- [ ] **Step 4: Create the request DTO and controller**

`src/main/java/com/smartparking/dto/AssistantBookingRequest.java`:

```java
package com.smartparking.dto;

import jakarta.validation.constraints.NotBlank;

public record AssistantBookingRequest(@NotBlank String message) {
}
```

`src/main/java/com/smartparking/controller/AssistantController.java`:

```java
package com.smartparking.controller;

import com.smartparking.ai.AiAssistantService;
import com.smartparking.dto.AssistantBookingRequest;
import com.smartparking.dto.BookingResponse;
import com.smartparking.repository.LocationRepository;
import com.smartparking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/assistant")
public class AssistantController {

    private final AiAssistantService aiAssistantService;
    private final BookingService bookingService;
    private final LocationRepository locationRepository;

    public AssistantController(AiAssistantService aiAssistantService, BookingService bookingService,
                                LocationRepository locationRepository) {
        this.aiAssistantService = aiAssistantService;
        this.bookingService = bookingService;
        this.locationRepository = locationRepository;
    }

    @PostMapping("/book")
    public BookingResponse book(@Valid @RequestBody AssistantBookingRequest request, Authentication auth) {
        var knownNames = locationRepository.findAll().stream().map(l -> l.getName()).toList();
        var intent = aiAssistantService.parseBookingIntent(request.message(), knownNames);
        var endTime = intent.startTime().plus(Duration.ofMinutes(intent.durationMinutes()));
        var booking = bookingService.createBookingByLocationName(auth.getName(), intent.locationName(), intent.startTime(), endTime);
        return BookingResponse.from(booking);
    }
}
```

- [ ] **Step 5: Write the controller test with a mocked `AiAssistantService`**

`src/test/java/com/smartparking/controller/AssistantControllerTest.java`:

```java
package com.smartparking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.ai.AiAssistantService;
import com.smartparking.ai.BookingIntent;
import com.smartparking.entity.Location;
import com.smartparking.entity.ParkingSlot;
import com.smartparking.entity.SlotStatus;
import com.smartparking.repository.LocationRepository;
import com.smartparking.repository.ParkingSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssistantControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ParkingSlotRepository slotRepository;
    @MockBean private AiAssistantService aiAssistantService;

    @Test
    @WithMockUser(username = "assistant-test@example.com")
    void parsesNaturalLanguageAndCreatesBooking() throws Exception {
        Location location = locationRepository.save(new Location("Mall Entrance", "addr", 1));
        slotRepository.save(new ParkingSlot(location, 1, SlotStatus.AVAILABLE));

        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        when(aiAssistantService.parseBookingIntent(anyString(), anyList()))
                .thenReturn(new BookingIntent("Mall Entrance", start, 120));

        mockMvc.perform(post("/assistant/book")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Object() {
                    public String message = "book me a spot near the mall entrance for 2 hours starting at 3pm";
                })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationId").value(location.getId()))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
```

Note: `@WithMockUser(username = "assistant-test@example.com")` sets the
`Authentication.getName()` used by `createBookingByLocationName`, but that
user must exist in the DB for a real end-to-end flow. Since this test only
calls `/assistant/book` (which does not look up the `User` row itself — it
relies on `BookingService.createBookingByLocationName` → `createBooking` →
`userRepository.findByEmail`), you must also persist a matching `User` in
this test using `UserRepository` before the request, or the call fails with
`IllegalArgumentException`. Add `@Autowired private UserRepository
userRepository;` and
`userRepository.save(new User("assistant-test@example.com", "hash", Role.USER));`
before the `mockMvc.perform(...)` call, with the corresponding imports
(`com.smartparking.entity.User`, `com.smartparking.entity.Role`,
`com.smartparking.repository.UserRepository`).

- [ ] **Step 6: Run the test**

Run: `./mvnw test -Dtest=AssistantControllerTest`
Expected: PASS

- [ ] **Step 7: Run all tests**

```bash
./mvnw test
```

Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src
git commit -m "feat: natural-language booking assistant via Gemini function calling"
git push
```

---

## Task 10: Admin Occupancy Stats + Anomaly Detection

**Files:**
- Create: `src/main/java/com/smartparking/service/AnomalyDetectionService.java`
- Create: `src/main/java/com/smartparking/dto/OccupancyStatsResponse.java`
- Create: `src/main/java/com/smartparking/controller/AdminController.java`
- Modify: `src/main/java/com/smartparking/service/BookingService.java`
- Modify: `src/main/java/com/smartparking/repository/ParkingSlotRepository.java`
- Test: `src/test/java/com/smartparking/service/AnomalyDetectionServiceTest.java`
- Test: `src/test/java/com/smartparking/controller/AdminControllerTest.java`

**Interfaces:**
- Produces: `AnomalyDetectionService.checkForAbuse(String userEmail, List<Booking> recentBookings)` throws `AnomalyDetectedException` if more than 5 bookings in the last 5 minutes; `GET /admin/locations/{id}/occupancy` (ADMIN only).
- Consumes: `BookingRepository`, `ParkingSlotRepository` (Task 3/4), `SecurityConfig`'s existing `/admin/**` → `hasRole("ADMIN")` rule (Task 2).

- [ ] **Step 1: Write the failing test for `AnomalyDetectionService`**

`src/test/java/com/smartparking/service/AnomalyDetectionServiceTest.java`:

```java
package com.smartparking.service;

import com.smartparking.entity.*;
import com.smartparking.exception.AnomalyDetectedException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnomalyDetectionServiceTest {

    private final AnomalyDetectionService service = new AnomalyDetectionService(5, 5);

    @Test
    void allowsUserUnderThreshold() {
        List<Booking> recent = bookingsCreatedNow(4);

        assertThatCode(() -> service.checkForAbuse("a@b.com", recent)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUserOverThreshold() {
        List<Booking> recent = bookingsCreatedNow(6);

        assertThatThrownBy(() -> service.checkForAbuse("a@b.com", recent))
                .isInstanceOf(AnomalyDetectedException.class);
    }

    private List<Booking> bookingsCreatedNow(int count) {
        User user = new User("a@b.com", "hash", Role.USER);
        Location location = new Location("Mall", "addr", 1);
        ParkingSlot slot = new ParkingSlot(location, 1, SlotStatus.AVAILABLE);
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new Booking(user, slot, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200)))
                .toList();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AnomalyDetectionServiceTest`
Expected: FAIL (compilation error — `AnomalyDetectionService` doesn't exist).

- [ ] **Step 3: Implement `AnomalyDetectionService`**

`src/main/java/com/smartparking/service/AnomalyDetectionService.java`:

```java
package com.smartparking.service;

import com.smartparking.entity.Booking;
import com.smartparking.exception.AnomalyDetectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnomalyDetectionService {

    private final int maxBookingsPerWindow;
    private final int windowMinutes;

    public AnomalyDetectionService(
            @Value("${app.anomaly.max-bookings-per-window:5}") int maxBookingsPerWindow,
            @Value("${app.anomaly.window-minutes:5}") int windowMinutes) {
        this.maxBookingsPerWindow = maxBookingsPerWindow;
        this.windowMinutes = windowMinutes;
    }

    public void checkForAbuse(String userEmail, List<Booking> recentBookingsInWindow) {
        if (recentBookingsInWindow.size() > maxBookingsPerWindow) {
            throw new AnomalyDetectedException(
                    "User " + userEmail + " exceeded " + maxBookingsPerWindow +
                    " bookings in " + windowMinutes + " minutes");
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AnomalyDetectionServiceTest`
Expected: PASS

- [ ] **Step 5: Add the repository query and wire the check into `createBooking`**

Add to `src/main/java/com/smartparking/repository/BookingRepository.java`:

```java
    List<Booking> findByUserEmailAndCreatedAtAfter(String email, Instant cutoff);
```

Add an `AnomalyDetectionService` field/constructor parameter to
`BookingService` (7th argument) and call it at the top of `createBooking`:

```java
    private final AnomalyDetectionService anomalyDetectionService;

    // add anomalyDetectionService as the 7th constructor parameter

    @Transactional
    public Booking createBooking(String userEmail, Long slotId, Instant startTime, Instant endTime) {
        var recent = bookingRepository.findByUserEmailAndCreatedAtAfter(userEmail, Instant.now().minusSeconds(300));
        anomalyDetectionService.checkForAbuse(userEmail, recent);

        User user = userRepository.findByEmail(userEmail)
        // ... rest unchanged
```

Update `BookingServiceTest.java`, `BookingExpiryTest.java`, and
`AssistantControllerTest.java`'s implicit Spring context wiring: add
`@Mock private AnomalyDetectionService anomalyDetectionService;` to the two
unit test files, add it as the 7th constructor argument, and in
`BookingServiceTest`'s two success-path tests
(`createBookingSucceedsWhenNoOverlap` and the lifecycle tests that call
`createBooking` indirectly are unaffected — only tests calling
`createBooking` directly need the stub) add:

```java
        when(bookingRepository.findByUserEmailAndCreatedAtAfter(any(), any())).thenReturn(List.of());
```

before invoking `createBooking`.

- [ ] **Step 6: Add occupancy aggregate query and admin endpoint**

Add to `src/main/java/com/smartparking/repository/ParkingSlotRepository.java`:

```java
    long countByLocationIdAndStatus(Long locationId, SlotStatus status);
```

`src/main/java/com/smartparking/dto/OccupancyStatsResponse.java`:

```java
package com.smartparking.dto;

public record OccupancyStatsResponse(
        Long locationId, long totalSlots, long available, long reserved, long occupied, double occupancyPct) {
}
```

`src/main/java/com/smartparking/controller/AdminController.java`:

```java
package com.smartparking.controller;

import com.smartparking.dto.OccupancyStatsResponse;
import com.smartparking.entity.SlotStatus;
import com.smartparking.repository.LocationRepository;
import com.smartparking.repository.ParkingSlotRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ParkingSlotRepository slotRepository;
    private final LocationRepository locationRepository;

    public AdminController(ParkingSlotRepository slotRepository, LocationRepository locationRepository) {
        this.slotRepository = slotRepository;
        this.locationRepository = locationRepository;
    }

    @GetMapping("/locations/{id}/occupancy")
    public OccupancyStatsResponse occupancy(@PathVariable Long id) {
        var location = locationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown location: " + id));
        long available = slotRepository.countByLocationIdAndStatus(id, SlotStatus.AVAILABLE);
        long reserved = slotRepository.countByLocationIdAndStatus(id, SlotStatus.RESERVED);
        long occupied = slotRepository.countByLocationIdAndStatus(id, SlotStatus.OCCUPIED);
        long total = location.getTotalSlots();
        double occupancyPct = total == 0 ? 0.0 : (double) (reserved + occupied) / total;
        return new OccupancyStatsResponse(id, total, available, reserved, occupied, occupancyPct);
    }
}
```

- [ ] **Step 7: Write the admin controller test (requires an ADMIN-role mock user)**

`src/test/java/com/smartparking/controller/AdminControllerTest.java`:

```java
package com.smartparking.controller;

import com.smartparking.entity.Location;
import com.smartparking.entity.ParkingSlot;
import com.smartparking.entity.SlotStatus;
import com.smartparking.repository.LocationRepository;
import com.smartparking.repository.ParkingSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ParkingSlotRepository slotRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void returnsOccupancyStatsForAdmin() throws Exception {
        Location location = locationRepository.save(new Location("Stat Lot", "addr", 2));
        slotRepository.save(new ParkingSlot(location, 1, SlotStatus.AVAILABLE));
        slotRepository.save(new ParkingSlot(location, 2, SlotStatus.OCCUPIED));

        mockMvc.perform(get("/admin/locations/{id}/occupancy", location.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(1))
            .andExpect(jsonPath("$.occupied").value(1))
            .andExpect(jsonPath("$.occupancyPct").value(0.5));
    }

    @Test
    @WithMockUser(roles = "USER")
    void rejectsNonAdminUser() throws Exception {
        Location location = locationRepository.save(new Location("Stat Lot 2", "addr", 1));

        mockMvc.perform(get("/admin/locations/{id}/occupancy", location.getId()))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 8: Run all tests**

```bash
./mvnw test
```

Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add src
git commit -m "feat: admin occupancy stats endpoint and rule-based anomaly detection"
git push
```

---

## Task 11: Testcontainers Repository Integration Tests

**Files:**
- Create: `src/test/java/com/smartparking/AbstractIntegrationTest.java`
- Create: `src/test/java/com/smartparking/repository/BookingRepositoryIntegrationTest.java`
- Modify: `src/test/resources/application-test.yml` (create if absent)

**Interfaces:**
- Produces: `AbstractIntegrationTest` — base class starting a shared Postgres `Testcontainer` and registering its JDBC URL/credentials via `@DynamicPropertySource`, for any `@SpringBootTest` that extends it.
- Consumes: none new — exercises `BookingRepository.findActiveOverlapping` (Task 4) against real Postgres row locking.

- [ ] **Step 1: Create the shared Testcontainers base class**

`src/test/java/com/smartparking/AbstractIntegrationTest.java`:

```java
package com.smartparking;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("smart_parking_test")
                    .withUsername("test_user")
                    .withPassword("test_pass");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

- [ ] **Step 2: Write the failing repository integration test**

`src/test/java/com/smartparking/repository/BookingRepositoryIntegrationTest.java`:

```java
package com.smartparking.repository;

import com.smartparking.AbstractIntegrationTest;
import com.smartparking.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookingRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ParkingSlotRepository slotRepository;

    @Test
    @Transactional
    void findActiveOverlappingDetectsOverlapAcrossBoundaries() {
        User user = userRepository.save(new User("repo-test@example.com", "hash", Role.USER));
        Location location = locationRepository.save(new Location("Repo Test Lot", "addr", 1));
        ParkingSlot slot = slotRepository.save(new ParkingSlot(location, 1, SlotStatus.AVAILABLE));

        Instant existingStart = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant existingEnd = existingStart.plus(1, ChronoUnit.HOURS);
        bookingRepository.save(new Booking(user, slot, existingStart, existingEnd));

        // Overlaps the last 30 minutes of the existing booking.
        Instant queryStart = existingEnd.minus(30, ChronoUnit.MINUTES);
        Instant queryEnd = existingEnd.plus(30, ChronoUnit.MINUTES);

        List<Booking> overlapping = bookingRepository.findActiveOverlapping(slot.getId(), queryStart, queryEnd);

        assertThat(overlapping).hasSize(1);
    }

    @Test
    @Transactional
    void findActiveOverlappingReturnsEmptyForAdjacentNonOverlappingWindow() {
        User user = userRepository.save(new User("repo-test2@example.com", "hash", Role.USER));
        Location location = locationRepository.save(new Location("Repo Test Lot 2", "addr", 1));
        ParkingSlot slot = slotRepository.save(new ParkingSlot(location, 1, SlotStatus.AVAILABLE));

        Instant existingStart = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant existingEnd = existingStart.plus(1, ChronoUnit.HOURS);
        bookingRepository.save(new Booking(user, slot, existingStart, existingEnd));

        // Starts exactly when the existing booking ends — not an overlap.
        List<Booking> overlapping = bookingRepository.findActiveOverlapping(
                slot.getId(), existingEnd, existingEnd.plus(1, ChronoUnit.HOURS));

        assertThat(overlapping).isEmpty();
    }
}
```

- [ ] **Step 3: Run the test**

Run: `./mvnw test -Dtest=BookingRepositoryIntegrationTest`
Expected: PASS (Testcontainers pulls and starts a Postgres container — Docker
must be running).

- [ ] **Step 4: Run all tests**

```bash
./mvnw test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "test: Testcontainers-backed repository integration tests for booking overlap query"
git push
```

---

## Task 12: README and API Documentation

**Files:**
- Create: `README.md`
- Create: `docs/API.md`

**Interfaces:**
- Produces: none (documentation only).
- Consumes: the full API surface built in Tasks 1-10.

- [ ] **Step 1: Write `README.md`**

`README.md`:

```markdown
# AI-Enhanced Smart Parking System

A Spring Boot backend for booking parking slots with real-time availability,
JWT-secured reservations, automatic expiry, dynamic pricing, and an AI layer
(Google Gemini) for demand prediction and natural-language booking.

## Stack

Java 17, Spring Boot 3.2, PostgreSQL, Spring Security + JWT, Google Gemini
API, JUnit 5 + Mockito, Testcontainers.

## Running locally

1. Copy `.env.example` to `.env` and fill in `JWT_SECRET` and `GEMINI_API_KEY`
   (a free key is available at https://aistudio.google.com/apikey).
2. Start Postgres: `docker compose up -d`
3. Export the env vars from `.env` into your shell (or use your IDE's env
   file support), then run: `./mvnw spring-boot:run`
4. The API is available at `http://localhost:8080`. Try `GET /health`.

## Running tests

```bash
docker compose up -d   # only needed for the concurrency test
./mvnw test
```

Testcontainers will automatically start its own Postgres container for the
repository integration tests — Docker must be running.

## API overview

See [docs/API.md](docs/API.md) for the full endpoint reference and example
requests.

## Architecture notes

- **Booking overlap safety**: `BookingService.createBooking` takes a
  pessimistic write lock (`SELECT ... FOR UPDATE`) on a slot's active
  bookings before checking for overlap, all inside one transaction — this is
  what a concurrency test in `BookingServiceConcurrencyTest` verifies by
  firing 10 concurrent requests for the same slot/window and asserting
  exactly one succeeds.
- **AI as an interface layer**: `AiAssistantService` is an interface;
  `GeminiAssistantService` is the only implementation, calling the Gemini
  REST API directly. Every test that touches AI-dependent code mocks this
  interface, so the test suite never depends on a live API key or network
  access.
- **Hardening ideas not implemented here**: a Postgres `EXCLUDE` constraint
  over a `tstzrange` column (with the `btree_gist` extension) would let the
  database itself guarantee no booking overlap can ever be inserted,
  independent of application code — a natural next step for production
  hardening.
```

- [ ] **Step 2: Write `docs/API.md`**

`docs/API.md`:

```markdown
# API Reference

Base URL: `http://localhost:8080`

All endpoints except `/health` and `/auth/**` require an
`Authorization: Bearer <jwt>` header obtained from `/auth/login`.
`/admin/**` additionally requires the `ADMIN` role.

## Auth

### `POST /auth/register`
```json
{ "email": "user@example.com", "password": "password123" }
```
→ `201 Created`

### `POST /auth/login`
```json
{ "email": "user@example.com", "password": "password123" }
```
→ `200 OK` `{ "token": "<jwt>" }`

## Locations & Slots

### `GET /locations`
→ `[{ "id": 1, "name": "Mall Parking", "address": "...", "totalSlots": 20 }]`

### `GET /locations/{id}/slots?available=true`
→ `[{ "id": 1, "locationId": 1, "slotNumber": 1, "status": "AVAILABLE" }]`

## Bookings

### `POST /bookings`
```json
{ "slotId": 1, "startTime": "2026-09-23T15:00:00Z", "endTime": "2026-09-23T17:00:00Z" }
```
→ `201 Created`, booking in `PENDING` status. `409 Conflict` if the slot is
occupied or the window overlaps an existing active booking.

### `POST /bookings/{id}/confirm`
`PENDING` → `CONFIRMED`. Must happen before the pending-expiry window (10
minutes by default) elapses, or the scheduled job marks it `EXPIRED`.

### `POST /bookings/{id}/checkin`
`CONFIRMED` → `ACTIVE`; slot becomes `OCCUPIED`.

### `POST /bookings/{id}/checkout`
`ACTIVE` → `COMPLETED`; computes `totalCost` via `PricingService` (base rate
× duration × occupancy-based surge/discount multiplier); slot becomes
`AVAILABLE`.

### `DELETE /bookings/{id}`
Cancels a booking not yet `ACTIVE`.

## AI

### `GET /predictions/{locationId}`
→ `{ "locationId": 1, "predictedOccupancyPct": 0.82, "reasoning": "..." }`
Cached for 5 minutes per location.

### `POST /assistant/book`
```json
{ "message": "book me a spot near the mall entrance for 2 hours starting at 3pm" }
```
Parses intent via Gemini function calling, resolves the closest matching
location, and creates a `PENDING` booking exactly as `POST /bookings` would.

## Admin

### `GET /admin/locations/{id}/occupancy` (ADMIN only)
→ `{ "locationId": 1, "totalSlots": 20, "available": 12, "reserved": 5, "occupied": 3, "occupancyPct": 0.4 }`
```

- [ ] **Step 3: Commit**

```bash
git add README.md docs/API.md
git commit -m "docs: add README and API reference"
git push
```
