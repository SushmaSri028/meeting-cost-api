# 💸 MeetingCost.io — Backend API

> **Real-time meeting cost tracker** — Spring Boot REST API with JWT auth, Google OAuth2, Calendar sync, and WebSocket live cost broadcasting.

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![WebSocket](https://img.shields.io/badge/WebSocket-STOMP-purple)](https://stomp.github.io/)

---

## What It Does

MeetingCost.io calculates and broadcasts the **real dollar cost of meetings** in real time. Every meeting has an estimated cost based on participant salaries and duration — and the live ticker shows that cost ticking up second by second while the meeting is happening.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3, Spring Security |
| Auth | JWT (JJWT), Google OAuth2 |
| Database | PostgreSQL + Flyway migrations |
| Real-time | Spring WebSocket (STOMP over SockJS) |
| Calendar | Google Calendar API |
| Build | Maven |
| Java | 21 |

---

## Features

- **JWT Authentication** — register, login, stateless token auth
- **Google OAuth2** — sign in with Google, get refresh token, persist to DB
- **Google Calendar Sync** — pull meetings from Google Calendar, calculate cost per meeting
- **Cost Engine** — estimates meeting cost based on participant count, duration, and average salary
- **WebSocket Live Ticker** — broadcasts cost updates every second to all connected clients
- **REST API** — meetings CRUD, stats endpoint, health check

---

## Project Structure

```
src/main/java/com/meetingcost/
├── config/
│   ├── SecurityConfig.java          # Spring Security + OAuth2 config
│   ├── WebSocketConfig.java         # STOMP WebSocket broker setup
│   ├── OAuth2SuccessHandler.java    # Handles Google login success + JWT redirect
│   └── AppConfig.java
├── controller/
│   ├── AuthController.java          # /api/auth/register, /api/auth/login
│   ├── MeetingController.java       # /api/meetings, /api/meetings/stats
│   ├── CalendarController.java      # /api/calendar/sync
│   ├── WebSocketController.java     # STOMP @MessageMapping handlers
│   └── HealthController.java
├── service/
│   ├── AuthService.java
│   ├── CalendarService.java
│   ├── CalendarSyncService.java
│   ├── CostCalculationService.java
│   ├── LiveCostTickerService.java   # @Scheduled broadcaster
│   └── MeetingService.java
├── entity/
│   ├── User.java
│   └── Meeting.java
├── dto/
│   └── LiveCostUpdate.java          # WebSocket broadcast payload
├── security/
│   ├── JwtTokenProvider.java
│   └── JwtAuthenticationFilter.java
└── repository/
    ├── UserRepository.java
    └── MeetingRepository.java
```

---

## Getting Started

### Prerequisites

- Java 21
- PostgreSQL 15+
- Maven 3.9+
- Google Cloud project with Calendar API enabled

### 1. Clone the repo

```bash
git clone https://github.com/YOUR_USERNAME/meetingcost-api.git
cd meetingcost-api
```

### 2. Create the database

```bash
psql -U postgres
CREATE DATABASE meetingcost;
CREATE USER mcuser WITH PASSWORD 'mcpassword';
GRANT ALL PRIVILEGES ON DATABASE meetingcost TO mcuser;
\q
```

### 3. Set environment variables

```bash
export GOOGLE_CLIENT_ID=your_google_client_id
export GOOGLE_CLIENT_SECRET=your_google_client_secret
export JWT_SECRET=your_jwt_secret_min_32_chars
```

Or set them in your IDE's run configuration.

### 4. Run the app

```bash
./mvnw spring-boot:run
```

App starts at `http://localhost:8080`

---

## API Reference

### Auth

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Create new account |
| POST | `/api/auth/login` | Login, get JWT |

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"pass123","displayName":"Your Name"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"pass123"}'
```

### Meetings

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/meetings` | List all meetings |
| GET | `/api/meetings/stats` | Aggregated cost stats |
| POST | `/api/calendar/sync` | Sync from Google Calendar |

```bash
# Get meetings (requires JWT)
curl http://localhost:8080/api/meetings \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Get stats
curl http://localhost:8080/api/meetings/stats \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### WebSocket (STOMP)

Connect to `ws://localhost:8080/ws` (SockJS)

| Destination | Direction | Description |
|-------------|-----------|-------------|
| `/app/meeting/start` | Client → Server | Start tracking meeting cost |
| `/app/meeting/stop` | Client → Server | Stop tracking |
| `/topic/cost/{meetingId}` | Server → Client | Live cost updates (1/sec) |

---

## Google OAuth2 Setup

1. Create a project in [Google Cloud Console](https://console.cloud.google.com)
2. Enable **Google Calendar API**
3. Create OAuth2 credentials (Web Application type)
4. Add authorized redirect URI: `http://localhost:8080/login/oauth2/code/google`
5. Add your email as a test user (OAuth consent screen → Test users)
6. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` env vars

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret |
| `JWT_SECRET` | Secret key for signing JWTs (min 32 chars) |

---

## Frontend

The React frontend lives in a separate repo:  
👉 [meetingcost-frontend](https://github.com/YOUR_USERNAME/meetingcost-frontend)

---

## Built as Part of Full-Stack Boot Camp

This project was built step by step as a portfolio project:

| Step | Feature |
|------|---------|
| 1 | Spring Boot project setup + PostgreSQL |
| 2 | JWT Authentication |
| 3 | Google OAuth2 + Calendar Sync + Cost Engine |
| 4 | Spring WebSocket + Live Cost Ticker |
| 5 | React Frontend (separate repo) |

---

*Built by Sushma Sri Kondamareddy — CS Masters, Florida Atlantic University*