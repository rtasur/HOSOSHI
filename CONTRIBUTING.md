# Contributing to HOSOSHI

## Branching
- `main` — stable/demo-ready
- `develop` — integration branch if the team uses one
- `feature/*` — individual work

Do not push directly to `main` for feature work.

## Pull requests
Every PR should include:
- what changed
- why it changed
- any API/database impact
- manual test steps
- screenshots for UI changes when useful

## Local validation
From the repository root:
```bash
docker compose config
docker compose build --progress=plain
```

Frontend:
```bash
cd frontend
npm ci
npm run build
```

Backend:
```bash
cd backend
./mvnw test
```

## Database changes
Flyway migrations are append-only after merge.

Create a new migration such as:
```text
backend/src/main/resources/db/migration/V9__description.sql
```

Do not modify an already-applied migration to change the schema.

## Secrets
Never commit:
- `.env`
- real JWT secrets
- real database credentials
- private keys
- real investigative data

Use `.env.example` for placeholders.

## Scope discipline
Avoid bundling unrelated changes into one PR. Prefer small, reviewable changes.
