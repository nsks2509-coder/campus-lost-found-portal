# Campus Lost and Found Portal

A Spring Boot, Thymeleaf, Spring Security, and Spring Data JPA application for campus lost-and-found reports.

## Requirements
- JDK 17+
- Maven 3.9+
- Docker (optional)

## Run locally

PowerShell:
```powershell
$env:ADMIN_EMAIL = "admin@your-campus.edu"
$env:ADMIN_PASSWORD = "use-a-unique-password-of-at-least-12-characters"
$env:ADMIN_NAME = "Campus Administrator"
mvn spring-boot:run
```

Linux/macOS:
```bash
export ADMIN_EMAIL="admin@your-campus.edu"
export ADMIN_PASSWORD="use-a-unique-password-of-at-least-12-characters"
export ADMIN_NAME="Campus Administrator"
mvn spring-boot:run
```

Open http://localhost:8080. Students can register at `/register`.

**There is no default administrator password.** Set both `ADMIN_EMAIL` and `ADMIN_PASSWORD` in your environment or hosting provider before deployment. The password must be at least 12 characters. On an existing database, the old sample administrator account is disabled when the new credentials are not configured; set the environment variables to provision the administrator account you intend to use.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DB_URL` | `jdbc:h2:file:./data/lostfound` | JDBC connection URL |
| `DB_USER` | `sa` | Database username |
| `DB_PASS` | empty | Database password |
| `ADMIN_EMAIL` | empty | Administrator login email |
| `ADMIN_PASSWORD` | empty | Administrator password (minimum 12 characters) |
| `ADMIN_NAME` | `Campus Administrator` | Display name for the provisioned administrator |
| `APP_EXPIRY_DAYS` | `30` | Age threshold for stale reports |
| `APP_UPLOAD_MAX_BYTES` | `5242880` | Maximum image upload size in bytes |
| `APP_UPLOAD_MAX_SIZE` | `5MB` | Spring multipart request limit |
| `LEGACY_ADMIN_EMAIL` | legacy sample account email | Account to disable on older databases when no admin credentials are configured |

An example environment file is provided in `.env.example`. Do not commit real passwords or database secrets.

## Features
- Lost/found item reports and search by title, description, or location
- Item image uploads validated as JPEG, PNG, or GIF
- Claim submission with proof messages
- Claim review, private claimant/finder chat, and handover status
- Student accounts and administrator workflow
- H2 file database for local demonstration

## Tests

```bash
mvn -B verify
```

## Docker

```bash
docker build -t campus-lost-found .
docker run --rm -p 8080:8080 \
  -e ADMIN_EMAIL="admin@your-campus.edu" \
  -e ADMIN_PASSWORD="use-a-unique-password-of-at-least-12-characters" \
  campus-lost-found
```

For deployment, persist both the database directory and `uploads/`; the container filesystem may be ephemeral. Configure a managed database and private object storage for production.

## Security and production notes
- Administrator credentials are provisioned from environment variables, not source code.
- Registration normalizes email addresses, bounds input lengths, and requires stronger passwords.
- Image files are decoded, dimension-limited, re-encoded, and assigned generated filenames; declared MIME types and user filenames are not trusted.
- Keep HTTPS enabled at the hosting layer.
- Before using real student information, add rate limiting, email verification, audit retention controls, backup/restore procedures, and production-grade database and object storage. Review campus privacy requirements before exposing contact details.
