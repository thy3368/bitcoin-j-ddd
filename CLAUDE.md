# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common

1. 用中文
2. 用utf8

## Project Overview

This is a Bitcoin implementation using Domain-Driven Design (DDD) principles in Java. The project aims to:

1. Provide clear understanding of Bitcoin's working principles
2. Build a cleaner, more robust Bitcoin codebase using DDD architecture

## Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot 4.0.0-M3
- **Build Tool**: Maven (with Maven wrapper)
- **Native Support**: GraalVM for AOT compilation
- **Dependencies**: Lombok for boilerplate reduction

## Build and Run Commands

### Standard Build and Run

```bash
# Build the project
./mvnw clean package

# Run the application
./mvnw spring-boot:run

# Run tests
./mvnw test
```

### Native Image (GraalVM)

```bash
# Build native image using Spring Boot buildpacks (requires Docker)
./mvnw spring-boot:build-image -Pnative

# Run native container
docker run --rm bitcoin-j-ddd:0.0.1-SNAPSHOT

# Build native executable (requires GraalVM 22.3+)
./mvnw native:compile -Pnative

# Run native executable
./target/bitcoin-j-ddd

# Run tests in native image
./mvnw test -PnativeTest
```

### Development Commands

```bash
# Clean build
./mvnw clean install

# Skip tests
./mvnw package -DskipTests

# Run specific test class
./mvnw test -Dtest=BitcoinJDddApplicationTests

# Run specific test method
./mvnw test -Dtest=BitcoinJDddApplicationTests#methodName
```

## Architecture: Hexagonal Architecture (Ports and Adapters)

The codebase follows DDD with Hexagonal Architecture pattern:

```
src/main/java/com/tanggo/fund/bitcoin/lib/
├── domain/          # Core business logic (center of hexagon)
│   ├── entities     # Domain entities (Transaction, Block, Peer, etc.)
│   ├── repo/        # Repository interfaces (ports)
│   └── gateway/     # External gateway interfaces (ports)
├── service/         # Application services (use cases)
├── inboud/          # Input adapters (API controllers, event handlers)
└── outbound/        # Output adapters (repository/gateway implementations)
    ├── repo/        # Database adapters
    └── gateway/     # External system adapters
```

### Key Architectural Principles

1. **Domain Layer** (`domain/`): Contains pure business logic with no framework dependencies
    - Entities: `Transaction`, `Block`, `Peer`, `PeerAddress`, `Command`
    - Ports: `TransactionRepo`, `PeerGateway` (interfaces only)

2. **Application Layer** (`service/`): Orchestrates domain objects and coordinates workflows
    - Use cases like `TransactionApps`, `CommonApps`

3. **Inbound Adapters** (`inboud/`): Entry points for external requests
    - `TransactionInbound` handles incoming commands

4. **Outbound Adapters** (`outbound/`): Implementations for external dependencies
    - `LocalDBTransactionRepo`: Local database implementation
    - `RemoteTransactionRepo`: Remote system implementation

### Dependency Rule

Dependencies flow inward:

- `inboud/` → `service/` → `domain/`
- `outbound/` → implements interfaces from `domain/`
- Domain layer has zero external dependencies

## Development Guidelines

### When Adding New Features

1. **Start with Domain**: Define entities and business rules in `domain/`
2. **Define Ports**: Create interfaces in `domain/repo/` or `domain/gateway/`
3. **Implement Use Cases**: Add application logic in `service/`
4. **Add Adapters**: Implement ports in `outbound/` and create entry points in `inboud/`

### Bitcoin Domain Concepts

Key domain entities represent Bitcoin protocol primitives:

- `Transaction`: Bitcoin transaction data and validation
- `Block`: Blockchain block structure
- `Peer`: Network peer node
- `Command`: Protocol commands for peer communication
- `PeerAddress`: Network addressing

## Project Goals

This project reimplements Bitcoin with:

- Clear separation of concerns using DDD
- Testable business logic isolated from infrastructure
- Clean architecture for better understanding and maintainability
