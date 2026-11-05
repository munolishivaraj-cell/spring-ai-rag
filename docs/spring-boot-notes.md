# Spring Boot notes (sample document for testing RAG)

## Spring Security with JWT

A stateless REST API usually authenticates with JSON Web Tokens (JWT). The client logs in
once with a username and password, the server returns a signed JWT, and the client sends it
on every request in the `Authorization: Bearer <token>` header.

In Spring Security 6 you configure this with a `SecurityFilterChain` bean:
disable CSRF for the stateless API, set the session policy to `STATELESS`, permit the
`/auth/login` endpoint, require authentication for everything else, and add a custom
`OncePerRequestFilter` that validates the token before `UsernamePasswordAuthenticationFilter`.
Keep tokens short-lived (for example 15 minutes) and use refresh tokens for longer sessions.

## Spring Boot Actuator

Actuator adds production endpoints such as `/actuator/health` and `/actuator/metrics`.
Only expose what you need with `management.endpoints.web.exposure.include`, and protect
sensitive endpoints with Spring Security.

## Configuration profiles

Use `application-dev.yml` and `application-prod.yml` for environment-specific settings and
activate one with `spring.profiles.active=prod` or the `SPRING_PROFILES_ACTIVE` environment
variable. Never commit real passwords; read them from environment variables or a secrets manager.

## Microservice communication

Synchronous calls between services can use `RestClient` or OpenFeign. Add timeouts and a
circuit breaker (Resilience4j) so one slow service doesn't take down the others. For
asynchronous events, use a message broker such as Kafka or RabbitMQ.
