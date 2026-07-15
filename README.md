# 🛒 Microservices E-Commerce Platform

A production-ready e-commerce backend built with **Spring Boot 4** following a microservices architecture. The system handles the full order lifecycle — from product management to order placement, inventory validation, and email notification — using asynchronous event-driven communication via RabbitMQ.

## 🏗️ Architecture Overview

```
                        ┌─────────────────┐
                        │   Config Server  │  ← Centralized config (Git-backed)
                        └────────┬────────┘
                                 │
                        ┌────────▼────────┐
                        │ Discovery Server │  ← Eureka (Service Registry)
                        └────────┬────────┘
                                 │
Client ──► API Gateway (9000) ───┼──► product-service   (MongoDB)
           │  Spring Cloud       ├──► order-service      (PostgreSQL)
           │  Gateway + OAuth2   ├──► inventory-service  (MySQL)
           │  (Keycloak)         └──► notification-service (Email/SMTP)
                                          ▲
                                    RabbitMQ (Events)
```

## ⚙️ Services

| Service | Responsibility | Database |
|---|---|---|
| `api-gateway` | Single entry point, routing, OAuth2 security (Keycloak) | — |
| `config-server` | Centralized configuration backed by Git | — |
| `discovery-server` | Service registry and discovery (Eureka) | — |
| `product-service` | Product catalog and stock management | MongoDB |
| `order-service` | Order processing, saga orchestration | PostgreSQL |
| `inventory-service` | Stock validation and reservation | MySQL |
| `notification-service` | Email notifications via SMTP | — |

## 🚀 Tech Stack

| Technology | Purpose |
|---|---|
| Java 21 + Spring Boot 4 | Core framework |
| Spring Cloud Gateway | API Gateway + load balancing |
| Spring Cloud Config | Centralized configuration |
| Netflix Eureka | Service discovery |
| Spring Security + OAuth2 + Keycloak | Authentication & authorization |
| RabbitMQ | Async event-driven communication |
| Resilience4j | Circuit breaker & retry patterns |
| MapStruct | DTO mapping |
| MongoDB / PostgreSQL / MySQL | Per-service databases |
| Docker + Docker Compose | Containerization |
| OpenTelemetry + Grafana LGTM | Observability (logs, traces, metrics) |

## 🔄 Order Flow

```
1. Client sends POST /api/orders (with JWT token)
2. API Gateway validates token via Keycloak
3. order-service receives request → saves order (PENDING) → publishes OrderPlacedEvent
4. inventory-service consumes event → validates stock → publishes OrderConfirmedEvent or OrderFailedEvent
5. order-service consumes result → updates order status (CONFIRMED / FAILED)
6. notification-service consumes event → sends email to customer
```

### Key Patterns Implemented

- **Saga Pattern** — distributed transaction coordination across services
- **Transactional Outbox** — guarantees zero data loss between DB writes and message publishing
- **Circuit Breaker** — protects against cascading failures (Resilience4j)
- **Retry with Exponential Backoff** — handles transient failures
- **Dead Letter Queue** — captures and recovers failed messages
- **Database per Service** — each service owns its data store

## 🔐 Security

Authentication is handled by **Keycloak** as the Identity Provider:

- API Gateway acts as OAuth2 Resource Server — rejects requests without a valid JWT
- Token Relay propagates the JWT from the Gateway to internal services
- Role-based access control via JWT Converter (maps Keycloak roles to Spring Security authorities)
- Business-level data isolation: users can only access their own orders

## 📊 Observability

The system is instrumented with **OpenTelemetry** and the **Grafana LGTM stack**:

- **Loki** — centralized log aggregation
- **Tempo** — distributed tracing (Trace ID injected in every log)
- **Prometheus** — metrics collection
- **Grafana** — unified dashboards across logs, traces, and metrics

## ✅ Prerequisites

- Docker Desktop installed and running
- Java 21+
- Maven 3.x

## ⚡ Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/Juammpa/microservices-ecommerce.git
cd microservices-ecommerce
```

### 2. Build all services

```bash
mvn clean install -DskipTests
```

### 3. Start the full stack

```bash
docker-compose up --build
```

This will start all services, databases, RabbitMQ, Keycloak, and the observability stack.

### 4. Access the services

| Service | URL |
|---|---|
| API Gateway | http://localhost:9000 |
| Eureka Dashboard | http://localhost:8761 |
| RabbitMQ Management | http://localhost:15672 |
| Keycloak Admin | http://localhost:8181 |
| Grafana | http://localhost:3000 |

## 📡 API Endpoints

All requests go through the API Gateway at `http://localhost:9000`. A valid Bearer token (obtained from Keycloak) is required.

### Products

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/products` | List all products |
| GET | `/api/products/{id}` | Get product by ID |
| POST | `/api/products` | Create a product |
| PUT | `/api/products/{id}` | Update a product |
| DELETE | `/api/products/{id}` | Delete a product |

### Orders

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/orders` | List orders (own orders for users, all for admins) |
| GET | `/api/orders/{id}` | Get order by ID |
| POST | `/api/orders` | Place a new order |

### Request example

**Place an order:**
```json
POST /api/orders
Authorization: Bearer <token>

{
  "productId": "abc123",
  "quantity": 2
}
```

## 📁 Project Structure

```
microservices-ecommerce/
├── api-gateway/          ← Spring Cloud Gateway + OAuth2
├── config-server/        ← Spring Cloud Config Server
├── discovery-server/     ← Netflix Eureka
├── product-service/      ← Products API (MongoDB)
├── order-service/        ← Orders + Saga (PostgreSQL)
├── inventory-service/    ← Stock management (MySQL)
├── notification-service/ ← Email notifications
├── config-data/          ← Git-backed configuration files
└── docker-compose.yml    ← Full stack orchestration
```

## 🗺️ Roadmap

- [ ] Kubernetes deployment (Minikube)
- [ ] Swagger / OpenAPI documentation
