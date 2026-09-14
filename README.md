# HOSOSHI — Criminal Network Intelligence Platform

HOSOSHI is Team YOKAI's Smart India Hackathon 2026 prototype for **SIH26189 — AI-Powered Criminal Network Analysis System**.

The prototype provides a secure investigator-facing foundation for criminal-network analysis:
- case management and case assignment
- role-based access control
- entity registry with case-specific roles
- source-data ingestion and evidence metadata
- relationship creation and interactive Network Explorer
- geographic context and map visualization
- investigation sessions and timeline
- reports and audit activity
- PostgreSQL as the system of record and Neo4j as the graph projection

> **Repository status:** internal team development repository. Keep the GitHub repository private.

## Quick start — Windows

### Prerequisites
- Windows 10/11
- Docker Desktop with WSL 2 enabled
- Git

### First run
```powershell
git clone <your-private-repo-url>
cd Hososhi

Copy-Item .env.example .env

.\START-HOSOSHI.cmd
```

Open **http://localhost:5173**.

Local bootstrap administrator:
- Username: `admin`
- Password: `123456`

These are **development-only credentials**. Change them before any deployment outside the team's controlled environment.

### Stop
```powershell
.\STOP-HOSOSHI.cmd
```

`docker compose down` preserves PostgreSQL/Neo4j volumes.

**Do not use `docker compose down -v` unless you intentionally want to wipe local data.**

## Services

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8081 |
| Swagger UI | http://localhost:8081/swagger-ui/index.html |
| Neo4j Browser | http://localhost:7474 |
| PostgreSQL | localhost:5432 |

## Architecture

```text
                  HOSOSHI Web UI
             React + TypeScript + Vite
                       |
                       v
               Spring Boot REST API
                       |
        +--------------+--------------+
        |                             |
        v                             v
   PostgreSQL                       Neo4j
 System of record             Graph projection
        |                             |
        +-------------+---------------+
                      |
              Investigation services
                      |
       +--------------+---------------+
       |              |               |
     Cases          Entities        Timeline
       |              |               |
       +--------------+---------------+
                      |
                Network Explorer
                + MapLibre map
```

### Data responsibilities
- **PostgreSQL:** users, roles, cases, case memberships, entities, case/entity registrations, evidence metadata, ingestion records, reports, sessions and audit records.
- **Neo4j:** case relationship graph used by Network Explorer.
- **Cytoscape.js:** interactive network visualization.
- **MapLibre GL:** geographic visualization.

## Development workflow

Use feature branches. Do not work directly on `main`.

Example:
```bash
git checkout -b feature/network-analytics
```

Before opening a pull request:
```bash
docker compose config
docker compose build --progress=plain
```

For frontend-only work, also run:
```bash
cd frontend
npm install
npm run build
```

For backend-only work, Java 21 and the Maven wrapper are included:
```bash
cd backend
./mvnw test
```

On Windows:
```powershell
cd backend
.\mvnw.cmd test
```

## Team rules

1. Pull the latest `main`/`develop` before starting work.
2. Create a feature branch for every change.
3. Never commit `.env`, database dumps, generated build directories, or credentials.
4. Do not delete or rewrite an existing Flyway migration that has already been merged. Add a new migration instead.
5. Test the Docker build before opening a pull request.
6. Keep changes focused and explain database/API changes in the pull request.
7. Do not directly push experimental work to `main`.

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Environment

Copy `.env.example` to `.env` for local overrides.

The Compose file also has development defaults so a teammate can start the stack immediately, but **production/external deployments must override all credentials and secrets**.

Tracked:
```text
.env.example
```

Never tracked:
```text
.env
```

## Clean local state

The Flyway migrations establish the current application schema and remove legacy synthetic/demo records from the local database state.

When switching between major branches, let Flyway run normally. Do not manually edit the database schema.

## Current prototype vs future AI layer

The current prototype demonstrates the investigator-facing foundation and graph workflow.

The planned AI/data-analysis layer includes:
- NLP/NER for entity extraction
- OCR for scanned intelligence
- entity resolution
- relationship extraction
- graph centrality/community analysis
- anomaly and suspicious-pattern detection
- human-in-the-loop validation

Do not represent these planned capabilities as already deployed unless they have been implemented and tested in the repository.

## Security

This is a prototype for an internal hackathon. It includes authentication, RBAC, case-level authorization and audit logging.

For real-world deployment, add and configure appropriate production controls such as:
- strong secret management
- MFA
- HTTPS/TLS
- secure key rotation
- hardened infrastructure
- external identity provider / enterprise SSO
- security monitoring and backup policy

See [SECURITY.md](SECURITY.md).

## Repository hygiene

The repository intentionally excludes:
- `.env`
- `node_modules`
- frontend build output
- Maven `target`
- IDE metadata
- local database files

The frontend includes a committed `package-lock.json` so team installs are reproducible.

## License / ownership

This repository is maintained by Team YOKAI for the Smart India Hackathon 2026 internal project. See `NOTICE.md`.
