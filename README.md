# BookTicket :: Services :: Payment Service

## Overview

The **Payment Service** is a specialized microservice that acts as a secure facade and anti-corruption layer for all interactions with our third-party payment provider, Stripe. Its sole responsibility is to handle payment intent creation, provide a mechanism to verify payment status, and maintain a local, auditable record of every transaction.

## Core Responsibilities

-   **Payment Intent Creation:** Provides an internal API for other services (like the Booking Service) to request the creation of a new Stripe Checkout Session.
-   **Encapsulation of Stripe SDK:** It is the only service in the system that contains the Stripe SDK and API keys, creating a strong security boundary.
-   **Payment Status Verification:** Exposes an endpoint that allows the `Booking Service` to poll for the status of a payment after the user has been redirected to Stripe.
-   **Transactional Record Keeping:** Persists a `Payment` entity for every transaction attempt, providing a local audit trail and decoupling the system from relying solely on Stripe's API for historical data.

## Architecture & Communication Flow

This service is part of a **synchronous polling** model for payment verification, which is a robust pattern for confirming payment status when a webhook is not used.
<img width="3425" height="2302" alt="PaymentService" src="https://github.com/user-attachments/assets/4a5f5946-2a76-4106-92d2-11b429099bd8" />


### How It Works

1.  **Isolation:** This service is the only component that communicates directly with Stripe. This isolates sensitive API keys and decouples the rest of our platform from a specific payment provider implementation.
2.  **Transactional Records:** When a payment is initiated, a `Payment` record is immediately created in the service's own **PostgreSQL** database with a `PENDING` status. This provides an immediate, local audit trail for every attempted transaction.
3.  **Polling for Verification:**
    -   Payment creation is a **synchronous** REST call from the `Booking Service`.
    -   After the user is redirected back from Stripe, the `Booking Service` initiates a **polling sequence**. It repeatedly calls the `GET /api/v1/internal/payments/checkout/verify/{sessionId}` endpoint on this service.
    -   The Payment Service calls the Stripe API to get the latest status. It then updates its local `Payment` record to `COMPLETED` or `FAILED` and returns the final status to the Booking Service.
4.  **Security:** All interactions are internal (service-to-service), preventing direct external access to the payment creation logic.

## Key Dependencies

-   **Spring Boot Starter Web:** For building the REST APIs.
-   **Spring Boot Starter Data JPA:** For database interaction with PostgreSQL.
-   **Stripe Java SDK (`stripe-java`):** The official library for interacting with the Stripe API.
-   **Eureka Discovery Client:** To register with the service registry.

## API Endpoints

All endpoints are for internal, service-to-service communication and are not exposed on the public API Gateway.

-   `POST /api/v1/internal/payments/checkout/create`: Creates a new Stripe Checkout Session and a corresponding `Payment` record.
-   `GET /api/v1/internal/payments/checkout/verify/{sessionId}`: Verifies the current status of a Checkout Session with Stripe and updates the local `Payment` record.
-   `GET /api/v1/internal/payments/status/{transactionId}`: Retrieves the last known status of a payment from the service's local database.
