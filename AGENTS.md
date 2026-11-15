# AGENTS.md

## Project

Spring Boot 3.3.6 e-commerce (Thymeleaf + MongoDB) under `demo/`. Java 17. Companion Python FastAPI prediction microservice at repo root (`microservicio_prediccion.py`).

## Commands (run from `demo/`)

- Build: `./mvnw clean package -DskipTests` (Windows: `mvnw.cmd`)
- Run: `./mvnw spring-boot:run`
- App URL: http://localhost:8080 (port via `PORT`, default 8080)
- Python microservice: `pip install -r requirements.txt` then `uvicorn microservicio_prediccion:app --reload --port 8000` (from repo root); Spring calls it via `PREDICCION_SERVICE_URL` (default http://localhost:8000)

## Key quirks

- README claims MySQL/MySQL stack — stale. Actual DB is MongoDB Atlas (`SPRING_DATA_MONGODB_URI`); local fallback `mongodb://localhost:27017/cultivus`. Need MongoDB running locally.
- No tests exist (no src/test). Do not expect `mvnw test` to validate much.
- Env loading: Spring imports `.env` from several relative locations (`spring.config.import`). Secrets also via env vars; Render uses its env panel.
- Active profile defaults to `dev`; set `SPRING_PROFILES_ACTIVE=prod` for production settings.
- Run commands from `demo/`, not repo root (pom.xml is in `demo/`).
- OAuth2 (Google/Facebook) optional; missing creds disable gracefully. Google vars currently commented in properties.
- Wompi payment keys have sandbox defaults; switch `WOMPI_API_BASE_URL` to production for prod.
- Password reset email uses Google Apps Script webhook (`MAIL_WEBHOOK_URL`); SMTP optional.

## Structure

- `demo/src/main/java/com/example/demo/` — controllers, models, repositories, services
- `demo/src/main/resources/templates/` — Thymeleaf (34 templates + fragments)
- `demo/src/main/resources/static/` — CSS/JS/images
- Python service at repo root depends on MongoDB too
