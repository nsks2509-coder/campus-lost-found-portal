# Campus Lost and Found Portal (Spring Boot + Thymeleaf + Spring Data JPA)
Run: `mvn spring-boot:run` then open http://localhost:8080
Admin login: admin@campus.edu / admin123 (change after first login). Students register on /register.
Database: H2 file by default. For MySQL set env DB_URL, DB_USER, DB_PASS.
Deploy: push to GitHub, create a Docker web service on Render/Railway (uses the Dockerfile), set PORT if needed.
