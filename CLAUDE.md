# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PizzaNat is a pizza delivery platform built with Spring Boot 3.4.5 and Java 21. It provides REST APIs for order management, payment processing, delivery tracking, and Telegram bot integration.

## Build Commands

```bash
# Build the project
./gradlew build

# Run locally with dev profile
./gradlew bootRun

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'

# Run tests (currently disabled in build.gradle)
./gradlew test
```

## Docker Commands

```bash
# Start all services (PostgreSQL, Redis, MinIO)
docker compose up -d

# Production deployment
docker compose -f docker-compose.production.yml up -d

# Development environment
docker compose -f docker-compose.dev.yml up -d
```

## Architecture

### Package Structure
```
com.baganov.pizzanat
├── config/          # Spring configurations (Security, JWT, S3, Telegram, YooKassa)
├── controller/      # REST controllers (~25 controllers)
├── service/         # Business logic layer (~45 services)
├── entity/          # JPA entities (User, Order, Product, Cart, Payment, etc.)
├── model/dto/       # Data transfer objects
├── repository/      # Spring Data JPA repositories
├── event/           # Application events (NewOrderEvent, PaymentStatusChangedEvent)
├── exception/       # Global exception handling
├── mapper/          # Object mappers
└── security/        # JWT and security components
```

### Key Services
- `ProductService` - Product CRUD and search
- `CartService` - Shopping cart management (supports anonymous users)
- `OrderService` - Order lifecycle management
- `YooKassaPaymentService` - YooKassa payment integration
- `TelegramAuthService` - Telegram bot authentication
- `TelegramBotService` / `AdminBotService` - Telegram bot handlers
- `DeliveryZoneService` - Delivery zones and cost calculation
- `S3Service` / `StorageService` - File storage (MinIO/Timeweb S3)

### Authentication Methods
1. Traditional username/password (JWT)
2. SMS authentication (Exolve API)
3. Telegram bot authentication

### Database
- PostgreSQL with Flyway migrations (24 migrations in `src/main/resources/db/migration/`)
- Redis for caching (dev) / Simple cache (prod)

## Environment Profiles

- **dev**: Uses local PostgreSQL, Redis, MinIO
- **prod**: Uses Timeweb Cloud PostgreSQL and S3, no Redis

## External Integrations

- **YooKassa** - Payment processing (SBP, bank cards)
- **Telegram Bots** - Two bots: customer bot (DIMBOpizzaBot) and admin bot (PizzaNatOrders_bot)
- **Exolve** - SMS authentication
- **Google Sheets** - Order tracking integration
- **MinIO / Timeweb S3** - Product image storage
- **DaData / Yandex Maps / Nominatim** - Address suggestions

## API Structure

Base URL: `/api/v1/`

Key endpoints:
- Auth: `/api/v1/auth/*` (login, register, SMS, Telegram)
- Products: `/api/v1/products`
- Categories: `/api/v1/categories`
- Cart: `/api/v1/cart`
- Orders: `/api/v1/orders`
- Payments: `/api/v1/payments/yookassa/*`
- Admin: `/api/v1/admin/*`
- Delivery: `/api/v1/delivery/*`

Interactive API docs: http://localhost:8080/swagger-ui.html

## Configuration

Main config: `src/main/resources/application.yml`

Critical environment variables (set in docker-compose.yml or .env):
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ADMIN_BOT_TOKEN`
- `YOOKASSA_SHOP_ID`, `YOOKASSA_SECRET_KEY`
- `TIMEWEB_S3_*` (for production S3 storage)

## Order Status Flow

`PENDING` → `CONFIRMED` → `PREPARING` → `READY` → `OUT_FOR_DELIVERY` → `DELIVERED`

Also: `CANCELLED`

## Payment Flow

1. Create order with `paymentMethod: YOOKASSA`
2. Call `/api/v1/payments/yookassa/create` to get payment URL
3. User completes payment via YooKassa
4. Webhook updates order payment status
5. Admin notified via Telegram

## Telegram Bot Architecture

- **Long Polling mode** (not webhooks) for both bots
- Customer bot: handles authentication, order notifications
- Admin bot: receives order notifications, status updates, payment alerts
- Configuration in `TelegramBotConfig` and `TelegramAdminBotConfig`

## Development Journal Rules

The project maintains three documentation files in `/docs/` that must be updated during development:

### 1. Project.md - Project Overview
**Location:** `docs/Project.md`

Detailed structured description of the project including:
- Project goals and objectives
- Architecture and component design
- Development stages and milestones
- Technologies and standards used
- Requirements for consistency and maintainability

**When to update:**
- When architecture changes are made
- When new functional requirements are added
- When technology stack changes
- When significant refactoring occurs

### 2. Tasktracker.md - Progress Tracking
**Location:** `docs/Tasktracker.md`

Development progress tracking based on Project.md.

**Task priorities:**
- 🔴 **Critical** - blocks other work or production
- 🟠 **High** - important for next release
- 🟡 **Medium** - planned features
- 🟢 **Low** - nice-to-have improvements

**Task statuses:**
- `[ ]` - Pending
- `[~]` - In progress
- `[x]` - Completed
- `[-]` - Cancelled/Deferred

**When to update:**
- When starting a new task (change status to in-progress)
- When completing a task (mark as completed)
- When adding new tasks (include priority)
- When task priorities change

### 3. Diary.md - Development Diary
**Location:** `docs/Diary.md`

Detailed project observation diary for seamless handoff between developers.

**Entry format:**
```markdown
## YYYY-MM-DD - [Entry Title]

### Observations
- Technical observations about the system
- Discovered patterns or behaviors
- Notes about codebase structure

### Solutions
- Problems solved today
- Technical decisions made
- Implementation approaches chosen

### Problems
- Outstanding issues
- Blockers encountered
- Questions that need answers
```

**When to update:**
- Daily during active development
- When important technical decisions are made
- When problems and their solutions are discovered
- When significant changes are implemented

### Claude's Responsibilities

At the **start of each session**, Claude should:
1. Read `docs/Diary.md` to understand recent work
2. Check `docs/Tasktracker.md` for current task status
3. Review `docs/Project.md` if architectural context is needed

During **active development**, Claude should:
1. Update Tasktracker.md when task status changes
2. Add entries to Diary.md for significant work
3. Update Project.md when architecture changes

At the **end of each session**, Claude should:
1. Ensure Diary.md has an entry for today's work
2. Verify all completed tasks are marked in Tasktracker.md
3. Note any new tasks discovered during the session
