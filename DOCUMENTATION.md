# Chatmosphere Backend — Documentation

> **Spring Boot** backend for the Chatmosphere real-time chat platform.  
> Handles REST APIs, WebSocket messaging, JWT authentication, email verification, and scheduled database maintenance.

---

## Table of Contents

1. [Tech Stack](#1-tech-stack)
2. [Project Structure](#2-project-structure)
3. [Environment Variables](#3-environment-variables)
4. [Running Locally](#4-running-locally)
5. [Authentication API](#5-authentication-api)
6. [Room API](#6-room-api)
7. [Chat WebSocket API](#7-chat-websocket-api)
8. [Contacts API](#8-contacts-api)
9. [Users API](#9-users-api)
10. [DB Keep-Alive Scheduler](#10-db-keep-alive-scheduler)
11. [Security](#11-security)
12. [Email Service](#12-email-service)
13. [Data Models](#13-data-models)
14. [Deployment Notes](#14-deployment-notes)

---

## 1. Tech Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.4 |
| REST | Spring Web (MVC) |
| Real-time | Spring WebSocket + STOMP |
| Relational DB | MySQL via Spring Data JPA (Hibernate) |
| Document DB | MongoDB via Spring Data MongoDB |
| Auth | Spring Security + JWT (jjwt 0.12.5) |
| Email | Spring Mail (Gmail SMTP) + EmailJS REST API |
| Scheduler | Spring `@Scheduled` |
| Build | Maven |
| Java | 17 |

---

## 2. Project Structure

```
src/main/java/com/chatmosphere/backend/
├── ChatmosphereBackendApplication.java   # Entry point — @EnableScheduling enabled
│
├── config/
│   ├── WebConfig.java           # CORS configuration
│   └── WebSocketConfig.java     # STOMP/WebSocket endpoint config
│
├── controller/
│   ├── AuthController.java      # /api/v1/auth/** (signup, login, OTP)
│   ├── RoomController.java      # /api/v1/rooms/** (create, join, messages)
│   ├── ChatController.java      # @MessageMapping (send message, typing)
│   ├── ContactController.java   # /api/v1/contacts (save, list)
│   └── UserController.java      # /api/v1/users (list all users)
│
├── documents/                   # MongoDB @Document classes
│   ├── Room.java                # Chat room document
│   ├── Message.java             # Message document
│   ├── EmailLog.java            # Email send log
│   └── Audit.java               # Shared audit fields (MongoDB)
│
├── dto/                         # Response transfer objects
│   ├── RoomDTO.java
│   ├── ContactDTO.java
│   └── UserDTO.java
│
├── entity/                      # JPA @Entity classes (MySQL)
│   ├── User.java
│   ├── Contact.java
│   ├── PendingVerification.java
│   ├── RoomMeta.java
│   └── Audit.java               # Shared audit fields (JPA)
│
├── exception/                   # Global exception handlers
│
├── listener/                    # WebSocket connect/disconnect → presence
│
├── model/
│   ├── MessageRequest.java      # Incoming WS message payload
│   └── TypingRequest.java       # Incoming WS typing payload
│
├── repository/                  # Spring Data interfaces
│   ├── UserRepository.java
│   ├── PendingVerificationRepository.java
│   ├── ContactRepository.java
│   ├── RoomsRepository.java     # MongoRepository<Room>
│   ├── MessageRepository.java
│   ├── RoomMetaRepository.java
│   └── EmailLogRepository.java
│
├── scheduler/
│   └── DbKeepAliveScheduler.java   # Ping MySQL + MongoDB every 3 hrs
│
├── security/
│   ├── SecurityConfig.java
│   ├── JwtUtils.java
│   └── JwtAuthenticationFilter.java
│
├── service/
│   ├── UserService.java
│   ├── RoomsService.java
│   ├── ChatService.java
│   ├── ContactService.java
│   ├── EmailService.java        # Interface
│   ├── EmailServiceImpl.java    # SMTP + EmailJS dual strategy
│   └── PresenceService.java     # Tracks online users per room
│
└── vo/                          # Request value objects
    ├── SignupRequestVO.java
    ├── LoginRequestVO.java
    ├── VerifyOtpRequestVO.java
    ├── CreateRoomRequestVO.java
    ├── CreatePersonalRoomRequestVO.java
    ├── InviteUsersRequestVO.java
    ├── AddContactRequestVO.java
    └── AuthResponseVO.java

src/main/resources/
└── application.properties
```

---

## 3. Environment Variables

All variables have defaults for local development. Override via system environment or a `.env`-equivalent (e.g. Hugging Face Secrets).

### Database

| Variable | Description | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/chatmosphere?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true` |
| `SPRING_DATASOURCE_USERNAME` | MySQL username | `root` |
| `SPRING_DATASOURCE_PASSWORD` | MySQL password | `admin@123` |
| `SPRING_MONGODB_URI` | MongoDB connection URI | `mongodb://localhost:27017/chatmosphere` |
| `SPRING_MONGODB_DATABASE` | MongoDB database name | `chatmosphere` |

### Server & CORS

| Variable | Description | Default |
|---|---|---|
| `FRONTEND_BASE_URL` | Allowed CORS origin | `http://localhost:5173` |
| `BACKEND_BASE_URL` | This server's own URL (used in email links) | `http://localhost:8080` |

### JWT

| Variable | Description | Default |
|---|---|---|
| `JWT_SECRET` | Secret for signing tokens | *(long default string)* |
| `JWT_EXPIRATION` | Token expiry (ms) | `86400000` (24 hours) |

### Email — Spring Mail (Gmail SMTP)

| Variable | Description | Default |
|---|---|---|
| `SPRING_MAIL_HOST` | SMTP host | `smtp.gmail.com` |
| `SPRING_MAIL_PORT` | SMTP port (**465 SSL** — port 587 blocked on HF Spaces) | `465` |
| `SPRING_MAIL_USERNAME` | Gmail address | *(empty — must set)* |
| `SPRING_MAIL_PASSWORD` | Gmail App Password | *(empty — must set)* |

> **Generate App Password:** Google Account → Security → 2-Step Verification → App Passwords.

### Email — EmailJS (HTTP fallback)

| Variable | Description | Default |
|---|---|---|
| `EMAILJS_SERVICE_ID` | EmailJS service ID | `service_edlcedo` |
| `EMAILJS_TEMPLATE_ID` | EmailJS template ID | `template_y3r69ef` |
| `EMAILJS_PUBLIC_KEY` | EmailJS public key | *(must set)* |
| `EMAILJS_PRIVATE_KEY` | EmailJS private key | *(must set)* |
| `EMAILJS_API_URL` | EmailJS send endpoint | `https://api.emailjs.com/api/v1.0/email/send` |

---

## 4. Running Locally

**Prerequisites:** Java 17+, Maven, MySQL on port 3306, MongoDB on port 27017.

```bash
# Clone and enter backend
cd chatmosphere-backend

# Run with Maven
mvn spring-boot:run

# Or build a JAR first
mvn clean package -DskipTests
java -jar target/chatmosphere-backend-0.0.1-SNAPSHOT.jar
```

The server starts on **http://localhost:8080**.

Hibernate creates all MySQL tables automatically on first run (`ddl-auto=update`). MongoDB collections are created on first write.

---

## 5. Authentication API

**Base path:** `/api/v1/auth`  
All auth endpoints are **public** (no JWT required).

---

### 5.1 Send Signup Email Verification

```
POST /api/v1/auth/signup/send-verification?email={email}
```

Sends a verification link to the given email. Token is valid for **15 minutes**.

**Response `200`:**
```json
{ "message": "Verification email sent successfully" }
```

---

### 5.2 Verify Email Token (link click)

```
GET /api/v1/auth/signup/verify?token={token}
```

Called when the user clicks the link in their inbox. Returns an HTML success/error page.

---

### 5.3 Check Verification Status

```
GET /api/v1/auth/signup/check-verification?email={email}
```

Frontend polls this every 3 seconds until the user clicks the link.

**Response `200`:**
```json
{ "verified": true }
```

---

### 5.4 Complete Registration

```
POST /api/v1/auth/signup
Content-Type: application/json
```

**Request body:**
```json
{
  "username": "john_doe",
  "password": "securePassword123",
  "displayName": "John Doe",
  "mobileNumber": "+919876543210",
  "email": "john@example.com",
  "avatarUrl": "grad-1"
}
```

`avatarUrl` accepts: `grad-1` (purple), `grad-2` (green), `grad-3` (orange), `grad-4` (blue), `grad-5` (red).

**Response `201`:**
```json
{
  "token": "eyJhbGci...",
  "username": "john_doe",
  "displayName": "John Doe",
  "avatarUrl": "grad-1"
}
```

---

### 5.5 Login — Send OTP

```
POST /api/v1/auth/login
Content-Type: application/json
```

**Request body:**
```json
{
  "username": "john_doe",
  "password": "securePassword123"
}
```

**Response `200`:**
```json
{ "message": "OTP sent to registered email" }
```

The OTP is 6 digits and valid for **5 minutes**.

---

### 5.6 Login — Resend OTP

```
POST /api/v1/auth/login/resend-otp?username={username}
```

**Response `200`:**
```json
{ "message": "OTP resent successfully" }
```

---

### 5.7 Login — Verify OTP

```
POST /api/v1/auth/verify-otp
Content-Type: application/json
```

**Request body:**
```json
{
  "username": "john_doe",
  "otp": "847291"
}
```

**Response `200`:**
```json
{
  "token": "eyJhbGci...",
  "username": "john_doe",
  "displayName": "John Doe",
  "avatarUrl": "grad-1"
}
```

Use the returned `token` as `Authorization: Bearer <token>` in all subsequent requests.

---

## 6. Room API

**Base path:** `/api/v1/rooms`  
**Auth required:** Yes — `Authorization: Bearer <token>`

---

### 6.1 Create a Group Space

```
POST /api/v1/rooms/create-room
Content-Type: application/json
```

**Request body:**
```json
{
  "roomName": "Dev Team Chat",
  "roomId": "DEV2024"
}
```

`roomId` is the shareable join code — must be globally unique.

**Response `201`:** `RoomDTO`

---

### 6.2 Start a Direct Message

```
POST /api/v1/rooms/personal
Content-Type: application/json
```

**Request body:**
```json
{
  "recipientUsername": "jane_doe"
}
```

Returns existing DM room if one already exists; otherwise creates a new one.

**Response `201`:** `RoomDTO`

---

### 6.3 Get All Rooms for Current User

```
GET /api/v1/rooms/
GET /api/v1/rooms/?search=dev
```

Returns all group and DM rooms the authenticated user participates in. Optional `search` param filters by room name.

**Response `200`:** `List<RoomDTO>`

---

### 6.4 Get Room Details

```
GET /api/v1/rooms/{roomId}
```

Returns full room details including participants list, creation date, and creator.

**Response `200`:** `RoomDTO`

---

### 6.5 Get Paginated Message History

```
GET /api/v1/rooms/{roomId}/messages?pageNo=0&pageSize=15
```

Returns a page of messages. `pageNo=0` is the most recent. Increment `pageNo` to load older messages.

**Response `200`:**
```json
{
  "messages": [
    {
      "id": "...",
      "roomId": "DEV2024",
      "senderId": "alice",
      "content": "Hello!",
      "sentAt": "2025-06-01T10:35:22Z"
    }
  ],
  "totalMessages": 120
}
```

---

### 6.6 Invite Users to a Room

```
PATCH /api/v1/rooms/{roomId}/invite
Content-Type: application/json
```

**Request body:**
```json
{
  "usernames": ["alice", "bob"]
}
```

Only applicable to group rooms. Caller must be a participant.

**Response `200`:** Updated `RoomDTO`

---

### 6.7 Remove a Participant

```
DELETE /api/v1/rooms/{roomId}/participants/{username}
```

- **Room creator** can remove any participant
- **Other members** can only remove themselves (leave room)

**Response `200`:** Updated `RoomDTO`

---

## 7. Chat WebSocket API

**Transport:** SockJS  
**Protocol:** STOMP  
**Connection endpoint:** `{BACKEND_URL}/chat`

---

### 7.1 Connect

```js
const socketFactory = () => new SockJS(`${baseUrl}/chat`);
const client = Stomp.over(socketFactory);

client.connectHeaders = { token: "eyJhbGci..." }; // JWT token

client.onConnect = () => { /* subscribe to topics here */ };
client.activate();
```

---

### 7.2 Send a Message

```
STOMP SEND → /app/sendMessage/{roomId}

Payload:
{
  "senderId": "john_doe",
  "messageContent": "Hello team!",
  "roomId": "DEV2024"
}
```

The server persists the message to MongoDB and broadcasts it to `/topic/room/{roomId}`.

---

### 7.3 Receive Messages

```
STOMP SUBSCRIBE → /topic/room/{roomId}

Incoming payload:
{
  "id": "...",
  "roomId": "DEV2024",
  "senderId": "john_doe",
  "content": "Hello team!",
  "sentAt": "2025-06-01T10:35:22Z"
}
```

**System messages** (user join/leave/remove) have `senderId = "system"`.

---

### 7.4 Typing Indicator

**Send** (broadcast your typing status):
```
STOMP SEND → /app/typing/{roomId}

Payload: { "username": "john_doe", "typing": true }
```

**Receive** (other users' typing status):
```
STOMP SUBSCRIBE → /topic/room/{roomId}/typing

Payload: { "username": "alice", "typing": true }
```

---

### 7.5 Online Presence

```
STOMP SUBSCRIBE → /topic/room/{roomId}/presence

Incoming payload: ["alice", "john_doe"]
```

Sent whenever a user connects or disconnects from the room. Backed by `PresenceService` which tracks per-room session counts.

---

## 8. Contacts API

**Base path:** `/api/v1/contacts`  
**Auth required:** Yes

### 8.1 Save a Contact

```
POST /api/v1/contacts
Content-Type: application/json

{
  "contactUsername": "jane_doe",
  "nickname": "Jane"
}
```

`nickname` is optional. If omitted, the contact's display name is used.

**Response `201`:** `ContactDTO`

---

### 8.2 Get All Contacts

```
GET /api/v1/contacts
```

**Response `200`:** `List<ContactDTO>`

---

## 9. Users API

**Base path:** `/api/v1/users`  
**Auth required:** Yes

### 9.1 Get All Other Users

```
GET /api/v1/users
```

Returns all registered users except the authenticated user.

**Response `200`:** `List<UserDTO>`

---

## 10. DB Keep-Alive Scheduler

**Class:** `scheduler/DbKeepAliveScheduler.java`  
**Schedule:** Every **3 hours** (`fixedDelay = 10,800,000 ms`)  
**Initial delay:** **2 minutes** after application startup

### Why it exists

Hugging Face Spaces (and similar free-tier cloud platforms) hibernate idle database connections. Without periodic activity, the first real request after a long idle period fails with a connection error.

### How it works

Each tick performs **insert → fetch → delete** on both databases using sentinel records:

**MySQL** (via `PendingVerificationRepository`):
- Inserts a row with `email = __keepalive__@chatmosphere.internal`
- Fetches it back to confirm the read path works
- Deletes it immediately

**MongoDB** (via `RoomsRepository`):
- Inserts a `Room` with `roomId = __keepalive__room`
- Fetches it back by `roomId`
- Deletes it immediately

Sentinel records never remain in the database. Each ping starts by cleaning up any leftover sentinel from a previous crashed run.

Errors are caught per-database and logged at `ERROR` level — a MongoDB failure does not prevent the MySQL ping from running, and vice versa.

---

## 11. Security

### JWT

- Tokens are generated on successful OTP verification and signup
- All protected REST endpoints require `Authorization: Bearer <token>`
- Token expiry is configurable (`JWT_EXPIRATION`, default 24 hours)
- The `JwtAuthenticationFilter` validates the token on every request

### WebSocket Auth

- The `token` value passed in STOMP connect headers is validated by `WebSocketAuthInterceptor`
- Connections without a valid token are rejected

### CORS

Configured in `WebConfig.java`:
- Allowed origin: `FRONTEND_BASE_URL` only
- Allowed methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`
- Credentials: allowed

### Passwords

Stored using **BCrypt** hashing via Spring Security's `PasswordEncoder`.

### Public Endpoints

The following are accessible without a token:
```
POST /api/v1/auth/signup/send-verification
GET  /api/v1/auth/signup/verify
GET  /api/v1/auth/signup/check-verification
POST /api/v1/auth/signup
POST /api/v1/auth/login
POST /api/v1/auth/login/resend-otp
POST /api/v1/auth/verify-otp
```

---

## 12. Email Service

**Class:** `service/EmailServiceImpl.java`

### Dual Strategy

Emails are sent using two parallel strategies with automatic fallback and **3 retry attempts**:

1. **Spring Mail** — Gmail SMTP on port 465 (SSL)
2. **EmailJS REST API** — HTTP fallback used because Hugging Face Spaces blocks outbound port 587 (STARTTLS)

The service tries both in parallel; if the primary fails, the fallback is used. Up to 3 attempts total with a short delay between retries.

### Email Types

| Trigger | Type | Validity |
|---|---|---|
| Signup | Email verification link | 15 minutes |
| Login | 6-digit OTP | 5 minutes |
| Room invite | Notification | N/A |

---

## 13. Data Models

### MySQL Tables (auto-created by Hibernate)

#### `users`

| Column | Type | Constraint |
|---|---|---|
| id | BIGINT | PK, Auto |
| username | VARCHAR | Unique, Not Null |
| password | VARCHAR | Not Null (BCrypt) |
| display_name | VARCHAR | |
| avatar_url | VARCHAR | e.g. `grad-1` |
| mobile_number | VARCHAR | Unique, Not Null |
| email | VARCHAR | Unique, Not Null |
| otp | VARCHAR | Nullable |
| otp_expiry | DATETIME | Nullable |

#### `pending_verifications`

| Column | Type | Constraint |
|---|---|---|
| id | BIGINT | PK, Auto |
| email | VARCHAR | Unique, Not Null |
| token | VARCHAR | Unique, Not Null |
| verified | BOOLEAN | |
| expiry_time | DATETIME | |

#### `contacts`

| Column | Type | Constraint |
|---|---|---|
| id | BIGINT | PK, Auto |
| owner_username | VARCHAR | Not Null |
| contact_username | VARCHAR | Not Null |
| nickname | VARCHAR | Nullable |

### MongoDB Collections

#### `rooms`

```json
{
  "_id": "ObjectId",
  "roomId": "DEV2024",
  "roomName": "Dev Team Chat",
  "isGroup": true,
  "participants": ["alice", "bob", "john"],
  "createdBy": "alice",
  "createDate": "2025-06-01T10:30:00",
  "updatedBy": null,
  "updateDate": null
}
```

`roomId` is indexed as unique.

#### `messages`

```json
{
  "_id": "ObjectId",
  "roomId": "DEV2024",
  "senderId": "alice",
  "content": "Hello team!",
  "sentAt": "2025-06-01T10:35:22Z"
}
```

#### `email_logs`

Records each email send attempt for debugging.

---

## 14. Deployment Notes

### Hugging Face Spaces

1. Push your Docker image or use the Space's build system
2. Set all environment variables in **Settings → Repository Secrets**
3. Use **port 465** for Gmail SMTP (port 587 is blocked)
4. The DB Keep-Alive Scheduler automatically prevents connection hibernation
5. Ensure `FRONTEND_BASE_URL` matches your deployed Netlify URL exactly

### General Cloud Deployment

- `spring.jpa.hibernate.ddl-auto=update` will auto-migrate schema changes
- MongoDB indexes (e.g. on `roomId`) are created automatically on startup
- Increase `JWT_EXPIRATION` if you want longer sessions (value is in milliseconds)

### Local Docker Compose

A `docker-compose.yml` is included at the project root to spin up MySQL and MongoDB locally:

```bash
docker-compose up -d
mvn spring-boot:run
```
