# Campus Lost & Found Portal

A Spring Boot application for reporting lost and found items, submitting ownership claims, and coordinating safe returns on campus.

## Features

- Student registration and sign-in with BCrypt password hashing
- Searchable lost and found reports with category filters
- Ownership claims, private conversations, and status history
- Admin queue for claims and ageing reports
- Optional item photos
- H2 for local development; PostgreSQL-compatible configuration for deployment

## Requirements

- Java 17
- Maven 3.9+
- Docker (optional)

## Run locally

```bash
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080) and register a student account. The application creates a local H2 database under `./data` by default.

## Administrator setup

There is no built-in administrator account or default password. To create or rotate an administrator, set both environment variables before starting the app:

```text
ADMIN_EMAIL=admin@example.edu
ADMIN_PASSWORD=<a unique password with at least 12 characters>
```

When both are set, startup creates the account if needed and applies the supplied password and administrator role. Keep the password in your hosting provider's secret settings; never commit it to the repository. Existing deployments should configure these variables before restarting so any previously seeded account receives a new password.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:h2:file:./data/lostfound` | JDBC connection URL |
| `DB_USER` | `sa` | Database user |
| `DB_PASS` | empty | Database password |
| `PORT` | `8080` | HTTP port |
| `ADMIN_EMAIL` | empty | Optional administrator bootstrap email |
| `ADMIN_PASSWORD` | empty | Optional administrator bootstrap password (12+ characters) |

For a hosted deployment, use PostgreSQL and persistent storage for the database and uploaded photos. The included Dockerfile builds the Spring Boot application:

```bash
docker build -t campus-lost-found .
docker run --rm -p 8080:8080 campus-lost-found
```

Pass database and administrator settings through your host's secret/environment configuration.

## Main flows

1. Register or sign in.
2. Post a lost or found report, optionally with a photo.
3. Submit an ownership claim with identifying proof.
4. Coordinate in the claim conversation.
5. Confirm the return to close the case.

Administrators can review reports and claims through the protected admin area.

## Production notes

This repository is a project/demo implementation. Before using it with real campus data, configure HTTPS, PostgreSQL backups and persistent file storage, review upload validation and retention, and replace schema auto-update with managed database migrations. Rotate any credentials used by an earlier deployment.
