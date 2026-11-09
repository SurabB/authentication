<div align="center">

# 🔐 Authentication API

**Secure email/password and Google login for Spring Boot, with JWTs in HTTP-only cookies.**

![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-REST%20API-6DB33F?logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-database-4479A1?logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-rate%20limits-DC382D?logo=redis&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-RS256-000000?logo=jsonwebtokens&logoColor=white)

[Features](#-features) • [Quick start](#-quick-start) • [Configuration](#%EF%B8%8F-configuration) • [API routes](#-api-routes) • [Project layout](#-project-layout)

</div>

---

## ✨ Features

- 📧 **Email/password registration** with email verification codes
- 🔑 **Login, logout and refresh tokens** (opaque, hashed in MySQL)
- 🔄 **Password reset** via emailed code
- 🌐 **Google OpenID Connect** login
- 🍪 **RS256 JWTs in HTTP-only cookies**, plus a CSRF cookie for browser requests
- 🚦 **Rate limiting and temporary blocking** for verification and reset codes (Redis)

---

## 📋 Requirements

| Requirement | Notes |
| --- | --- |
| ☕ **JDK 25** | The Maven project sets `java.version` to 25 |
| 🐳 **Docker + Docker Compose** | Runs MySQL and Redis |
| 🔐 **Google OAuth credentials** | Optional to start the app, required for Google login |
| 📬 **SMTP account** | Required to send verification and reset emails |

---

## 🚀 Quick start

### 1. Start MySQL and Redis

The included `docker-compose.yml` defines both services. Spring Boot's Docker Compose support can start them with the app, or you can start them yourself:

```bash
docker compose up -d
```

### 2. Generate the RSA key pair

`.pem` files are git-ignored, so a fresh checkout needs its own keys. The app expects an **unencrypted PKCS#8** private key and an **X.509** public key on the classpath.

```powershell
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out src/main/resources/private.pem
openssl pkey -in src/main/resources/private.pem -pubout -out src/main/resources/public.pem
```

> [!WARNING]
> Never commit `private.pem`. Back it up securely, and don't regenerate the pair between deployments unless you are intentionally rotating signing keys.

### 3. Set environment variables

Run these in the **same PowerShell window** that will start the app. The database values match the Compose MySQL service. The mail values must come from your real SMTP account, and the Google values are placeholders: the app starts with them, but Google login won't work until you use real credentials.

<details>
<summary><b>📄 Click to expand the full PowerShell setup</b></summary>

```powershell
# Database (matches docker-compose.yml)
# Optional locally: Spring Boot's Docker Compose support takes the connection
# details from the running MySQL container and takes precedence over these.
# They are used when running without Compose support (e.g. production).
$env:JDBC_URL = 'jdbc:mysql://localhost:3307/mydatabase'
$env:JDBC_USERNAME = 'myuser'
$env:JDBC_PASSWORD = 'secret' # Required field for docker compose setup

# Mail (use your real SMTP provider's values)
$env:MAIL_HOST = 'smtp.example.com'
$env:MAIL_PORT = '587'
$env:MAIL_USERNAME = 'your-smtp-username'
$env:MAIL_PASSWORD = 'your-smtp-password'

# Google OAuth (placeholders)
$env:GOOGLE_CLIENT_ID = 'local-client-id'
$env:GOOGLE_CLIENT_SECRET = 'local-client-secret'

# Cookies and token lifetimes
# Local HTTP development: cookies are not marked Secure.
$env:COOKIE_SECURE = 'false'
$env:ACCESS_TOKEN_TIME_IN_MINUTES = '15'
$env:REFRESH_TOKEN_TIME_IN_DAYS = '7'

# Registration verification
$env:REG_VERIF_SERVICE_NAME = 'reg:'
$env:REG_VERIF_SERVICE_MSG = 'Registration verification code'
$env:REG_VERIF_TOKEN_STORAGE = 'token:'
$env:REG_VERIF_MAIL_SENT_LIMIT = '5'
$env:REG_VERIF_ATTEMPTS_LIMIT = '5'
$env:REG_VERIF_BLOCKED_STORAGE = 'blocked:'
$env:REG_VERIF_LIMIT_STORAGE = 'limit:'
$env:REG_VERIF_BLOCK_TIME_IN_MINS = '15'

# Password reset
$env:PASS_RESET_SERVICE_NAME = 'reset:'
$env:PASS_RESET_SERVICE_MSG = 'Password reset code'
$env:PASS_RESET_TOKEN_STORAGE = 'token:'
$env:PASS_RESET_MAIL_SENT_LIMIT = '5'
$env:PASS_RESET_ATTEMPTS_LIMIT = '5'
$env:PASS_RESET_BLOCKED_STORAGE = 'blocked:'
$env:PASS_RESET_LIMIT_STORAGE = 'limit:'
$env:PASS_RESET_BLOCK_TIME_IN_MINS = '15'
```

</details>

### 4. Run the app

```powershell
.\mvnw.cmd spring-boot:run
```

The API is now available at **`http://localhost:8080/api`**.

> [!NOTE]
> `frontend-url` defaults to `http://localhost:5173` and controls where users are redirected after Google login.

### Add a local test user (optional)

To create a user for local API testing without going through email verification, temporarily add this bean to `AuthenticationApp`:

```java
@Bean
public CommandLineRunner commandLineRunner(UserService userService) {
    return args -> {
        RegisterRequest user1 = new RegisterRequest(
                "ram", "pass", "abc@gmail.com", TokenGeneration.generateToken());
        userService.createUser(user1, Role.USER);
    };
}
```

 The test credentials are `abc@gmail.com` / `pass`. Use this only with disposable local data, and remove the bean before committing or deploying the application. The configured `ddl-auto: create` currently recreates the schema on startup; if you change that setting, avoid running this bean repeatedly against a database where the same email already exists.

### Build a jar

```powershell
.\mvnw.cmd clean package
java -jar target/jwt-0.0.1-SNAPSHOT.jar
```

Set the same environment variables before running the jar.

---

## ⚙️ Configuration

Configuration is read from `src/main/resources/application.yml` using the environment variables above.

> [!TIP]
> PowerShell doesn't load `.env` files automatically, and `.env` is git-ignored. Use the commands above or set the variables in your IDE's run configuration.

### 🔑 JWT keys

For production, keep the keys **outside the jar** and point the app at them:

```text
JWT_PRIVATE_KEY_LOCATION=file:/run/secrets/private.pem
JWT_PUBLIC_KEY_LOCATION=file:/run/secrets/public.pem
```

### 🍪 Cookies

Set `COOKIE_SECURE=true` for production over HTTPS. It applies to the JWT, refresh-token and CSRF cookies. The local example uses `false` so cookies work over `http://localhost`.

### 🔄 Refresh tokens

Refresh tokens are random opaque values stored **hashed** in MySQL. `REFRESH_TOKEN_TIME_IN_DAYS` controls both their database expiry and cookie lifetime.

### 🗄️ Ports and services

| Service | Port | Notes |
| --- | --- | --- |
| MySQL | `3307` (host) | Published by Compose |
| Redis | auto-mapped | Spring Boot's Docker Compose support detects the mapped host port from the running container, so no fixed mapping is needed |
| API | `8080` | Served under `/api` |
| Frontend | `5173` | Default `frontend-url` |

### 🔐 Google login

Register this redirect URI in your Google OAuth client for the default local setup:

```text
http://localhost:8080/login/oauth2/code/google
```

Use the real client ID and secret to test it. Start the flow at `/oauth2/authorization/google`.

### 📬 Email

Verification and reset emails are sent through a real SMTP account. Set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME` and `MAIL_PASSWORD` to your provider's values. If these are wrong, registration and password-reset codes won't be delivered.

### 🛡️ Security notes for production

> [!CAUTION]
> The defaults in this repo are for **disposable local development only**.

> [!WARNING]
> This repository is provided as-is. The repository owner is not responsible for damage or loss resulting from its use. Before using this project in a real production environment, review your company's security policies and requirements. This project may require substantial changes and additional configuration for your environment; assess and change the defaults to meet your requirements instead of relying on them as production-ready settings.

- **Compose credentials and ports:** a known local password is fine for a disposable database on your own machine. It becomes a concern if it is reused, if the database holds valuable data, or if the port is reachable from other machines. Don't reuse these credentials or expose these ports on a production host. Redis has **no password** configured.
- **Schema management:** Hibernate uses `ddl-auto: create`, which **recreates the schema on every start**. For production, add Flyway or Liquibase with migrations and switch to a non-destructive setting such as `validate`.
- **Debug logging:** Spring Security debug mode and verbose CSRF logs are off by default. For temporary local troubleshooting, enable `debug = true` on `@EnableWebSecurity` in `SecurityConfig`, and/or set targeted log levels in a local-only config:

  ```yaml
  logging.level.org.springframework.security: DEBUG
  logging.level.org.springframework.security.web.csrf: TRACE
  ```

  Revert these afterwards. Verbose security logs can expose request and authentication details.

---

## 📡 API routes

All routes are prefixed with `/api`.

| Method | Route | Purpose |
| :---: | --- | --- |
| `GET` | `/auth/getCsrfToken` | Get a CSRF token before making protected browser requests |
| `POST` | `/auth/login` | Log in with email and password; sets auth cookies |
| `POST` | `/auth/refreshToken` | Refresh auth cookies |
| `POST` | `/auth/logout` | Revoke the refresh token and clear auth cookies |
| `POST` | `/auth/forgetPasswordSendCode` | Send a password reset code |
| `POST` | `/auth/verifyAndChangePassword` | Verify the reset code and change the password |
| `POST` | `/user/sendRegCode` | Send a registration verification code |
| `POST` | `/user/verifyAndRegisterUser` | Verify the code and create a user |
| `GET` | `/user/me` | Return the authenticated user's profile |

🌐 **Google login:** `GET /oauth2/authorization/google`

---

## Endpoint testing guide

For Postman request examples, CSRF instructions, and endpoint screenshots, see the [API endpoint testing guide](markdown/API_ENDPOINT_TESTING.md). Its screenshots are stored in [`markdown/images/`](markdown/images/).

---

## 🗂️ Project layout

```text
.
├── docker-compose.yml                 # Local MySQL and Redis
└── src/main
    ├── java/sb/auth
    │   ├── controller/                # REST endpoints
    │   ├── service/                   # Auth, registration, mail and token workflows
    │   ├── security/                  # Spring Security and JWT
    │   └── config/                    # CSRF, OAuth and app configuration
    └── resources/application.yml      # Server and environment-based configuration
```
