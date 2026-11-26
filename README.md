# CAG Finance App - Backend API

A secure, scalable RESTful backend service for financial transaction logging, user management, and receipt management built with **Spring Boot**, **MySQL**, and **Redis**.

---

## 🚀 Key Features

* **Google OAuth2 Authentication**: Stateless verification of Google ID tokens directly via Google API client certificates.
* **Dual-Layer Token Blacklisting**:
  * **Redis Primary**: Sub-millisecond blacklist lookups with dynamic Time-To-Live (TTL) tied to the token's natural expiration.
  * **MySQL Fallback**: Seamless fallback to database persistence if Redis is unavailable or restarting.
  * **Automated Janitor Job**: An `@Scheduled` cron job purges expired MySQL blacklist records hourly to prevent database bloat.
* **Role & Permission-Based Access Control (RBAC)**: Fine-grained permissions (`MANAGE_USERS`, `UPLOAD_TX`, etc.) enforced via Spring Method Security (`@PreAuthorize`).
* **Admin Approval Workflow**: New users must be reviewed and approved by an administrator before gaining system access.
* **Transaction Management & Pagination**: Full CRUD, paginated transaction queries, and search capabilities.
* **Receipt & Media Storage**: Cloudinary integration for receipt attachments and image uploads.
* **Audit Trail**: Transaction lifecycle and action logging via `TransactionLog`.

---

## 🛠️ Tech Stack

* **Java**: 21+
* **Framework**: Spring Boot (Web, Security, Data JPA, Data Redis, Validation)
* **Database**: MySQL 8.0+
* **In-Memory Cache**: Redis 7+ (via `StringRedisTemplate`)
* **Cloud Storage**: Cloudinary SDK
* **External APIs**: Google OAuth2 Client, Google Sheets API
* **Build Tool**: Maven

---

## 📋 Prerequisites

Before running the backend, make sure you have:
* **Java 21 or 25 JDK** installed.
* **MySQL** running on `localhost:3306` with database `cag_finance_db`.
* **Docker** installed (to run Redis).

---

## ⚡ Quick Start

### 1. Start the Redis Container
Run Redis in Docker:
```bash
docker run -d --name redis -p 6379:6379 --restart unless-stopped redis:alpine
```

Verify Redis is alive:
```bash
docker exec -it redis redis-cli ping
# Expected output: PONG
```

### 2. Configure Environment Variables
Copy the `.env.example` template to `.env` in the project root:
```bash
cp .env.example .env
```
Fill in your credentials in `.env` (this file is excluded by `.gitignore` and won't be pushed to GitHub):

| Variable | Description | Default |
| :--- | :--- | :--- |
| `DB_URL` | MySQL Connection JDBC URL | `jdbc:mysql://localhost:3306/cag_finance_db` |
| `DB_USERNAME` | MySQL Username | `root` |
| `DB_PASSWORD` | MySQL Password | *(your password)* |
| `REDIS_HOST` | Redis Server Host | `localhost` |
| `REDIS_PORT` | Redis Server Port | `6379` |
| `GOOGLE_CLIENT_ID` | Google OAuth2 Client ID | *(your Google client id)* |
| `CLOUDINARY_CLOUD_NAME` | Cloudinary Cloud Name | *(your cloud name)* |
| `CLOUDINARY_API_KEY` | Cloudinary API Key | *(your API key)* |
| `CLOUDINARY_API_SECRET` | Cloudinary API Secret | *(your API secret)* |

### 3. Build & Run the Backend
Using Maven Wrapper:
```bash
# Compile and package
./mvnw clean install -DskipTests

# Run application
./mvnw spring-boot:run
```
The server will start on `http://localhost:8080`.

---

## 📡 API Overview

### 🔐 Authentication (`/auth`)
* `POST /verifyToken`: Authenticates a Google ID token from React and syncs profile.
* `GET /auth/verify`: Verifies active session token.
* `POST /auth/logout`: Revokes token immediately by placing it into the Redis & MySQL blacklists.

### 💰 Transactions (`/transactions`)
* `GET /transactions?page=0&size=10&search=...`: Retrieve paginated transactions.
* `POST /transactions/add`: Create a transaction with receipt upload (`hasAuthority('UPLOAD_TX')`).

### 👤 User & Admin (`/users`, `/admin`)
* `GET /users/me`: Fetch current logged-in profile and permissions.
* `GET /admin/users`: List all users (`hasAuthority('MANAGE_USERS')`).
* `POST /admin/users/{userId}/approve`: Approve new registration.
* `PUT /admin/users/{userId}/permissions`: Update user permission set.

---

## 🔒 Security & Blacklist Architecture

```
Incoming Request (Bearer <token>)
       │
       ▼
GoogleTokenFilter
       │
       ├──> 1. Check Redis ("blacklist:token:<token>")
       │       ├── Found ──────────────> 401 Unauthorized ("Session expired")
       │       └── Redis Down? ─────────> Check MySQL Fallback Table
       │
       ├──> 2. Verify Google Signature & Expiration in Memory
       │
       ├──> 3. Verify User Approved Status in MySQL
       │
       └──> 4. Load Authorities & Pass to Controller
```