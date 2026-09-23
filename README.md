# HOSOSHI — AI-Powered Criminal Network Analysis System

**SIH 2026 · Problem Statement SIH26189 · Team YOKAI**

HOSOSHI is a **working manual prototype** that demonstrates how fragmented investigative information can be organized into cases, entities, relationships, timelines, maps, evidence and a graph-backed Network Explorer.

> **Prototype status:** This repository contains the **manual version without the final AI engine**. The current prototype is intentionally operator-driven so judges can run and inspect the complete investigative workflow. In the full model, approved-source ingestion, NLP/entity extraction, entity resolution, relationship extraction, OSINT correlation, controlled dark-web intelligence collection and graph/ML analysis will be automated or AI-assisted, with investigator validation remaining in the loop.

**Judge quick guide:** See [`JUDGE-QUICK-GUIDE.md`](JUDGE-QUICK-GUIDE.md) for the 60-second startup, credentials, demo flow and verification commands.

---

## 1. What the prototype currently demonstrates

- Secure sign-in, registration and administrator approval
- Role-based access for Super Admin, Investigation Supervisor, Investigator, Intelligence Analyst, Data Operator and Auditor
- Case creation, assignment and administrator recycle bin
- Entity registration with separate entity type and case role
- Manual relationship creation and verification
- PostgreSQL as the application system of record
- Neo4j as the graph projection used by Network Explorer
- Interactive Cytoscape.js criminal-network visualization
- MapLibre geographic context
- Investigation timeline and investigation sessions
- Evidence upload with SHA-256 file metadata, validation and authenticated file viewing
- Data-ingestion/source tracking with authenticated source-file viewing
- Investigation reports
- Audit activity
- Cross-case entity lookup subject to case access
- Optional AES-256-GCM application-layer protection for operational JSON API payloads

## 2. Manual prototype vs. full model

### Current manual prototype

```text
Case
  ↓
Manual Entity Registration
  ↓
Manual Relationship Creation
  ↓
Neo4j Network Projection
  ↓
Network Explorer + Map + Timeline
  ↓
Evidence + Reports
  ↓
Investigator Review
```

### Planned full model

```text
Approved Data Sources
        ↓
Ingestion & Normalization
        ↓
AI / NLP Entity Extraction
        ↓
Entity Resolution
        ↓
Relationship Extraction
        ↓
Temporal Knowledge Graph
        ↓
Graph + ML Analytics
        ↓
OSINT Correlation
        ↓
Controlled Dark-Web Intelligence Collection
        ↓
Pattern / Anomaly Detection
        ↓
Investigator Validation
        ↓
Actionable Intelligence
```

The AI, OSINT and controlled dark-web components in the second diagram are **future full-model capabilities**, not claims that they are already implemented in this repository.

---

# 3. Judge quick start — Windows

The normal judge path requires only:

- **Windows 10/11**
- **Docker Desktop** with the Linux/WSL2 engine running
- **Git**
- Internet access the first time Docker has to pull its base images

You do **not** need to install Java, Maven, Node.js, PostgreSQL or Neo4j separately for the standard Docker demo.

## Step 1 — Clone the repository

Open **PowerShell** or **Command Prompt** and run:

```powershell
git clone https://github.com/rtasur/HOSOSHI.git
cd HOSOSHI
```

## Step 2 — Start Docker Desktop

Open Docker Desktop and wait until it shows that the Docker Engine is running.

## Step 3 — Start HOSOSHI

From the HOSOSHI folder run:

```cmd
START-HOSOSHI.cmd
```

The launcher automatically:

1. Checks that Docker is available.
2. Creates a local `.env` from `.env.example` when needed and generates strong random JWT/API-encryption keys when those values are blank.
3. Builds the frontend and backend images.
4. Starts PostgreSQL and Neo4j.
5. Waits for the dependencies to become healthy.
6. Starts the backend and frontend.
7. Checks the application readiness endpoint.
8. Opens the HOSOSHI web application.

**First startup can take a few minutes** because Docker may need to download the base images and Maven/npm dependencies.

## Step 4 — Open HOSOSHI

If the launcher does not open your browser automatically, open:

**http://localhost:5173**

## Step 5 — Sign in

The local prototype includes a demo administrator:

```text
Username: admin
Password: 123456
```

These credentials are for the local hackathon prototype only.

## Step 6 — Verify the installation

After the application opens, run:

```cmd
VERIFY-HOSOSHI.cmd
```

The verification script performs application-level smoke checks for Docker/Compose, PostgreSQL, Neo4j, backend/frontend readiness, JWT login, all six roles, protected endpoints, case creation, case assignment, case isolation, auditor read-only access, and encryption wiring. It creates/reuses only `CNI-VERIFY-001` and `CNI-VERIFY-002`; it does not reset the database.

## Step 7 — Stop the application

```cmd
STOP-HOSOSHI.cmd
```

This stops the application containers **without deleting the local database volumes**.

---

# 4. Packaged synthetic demo case

The repository includes the **Operation Silent Harbor** synthetic case pack under `HOSOSHI-DEMO-CASE-PACK/`. After `START-HOSOSHI.cmd` reports that the application is ready, load the demo with:

```powershell
powershell -ExecutionPolicy Bypass -File ".\HOSOSHI-DEMO-CASE-PACK\scripts\LOAD-HOSOSHI-DEMO.ps1"
```

The loader creates/reuses case `CNI-2026-0091`, assigns the demo team, loads the synthetic source/evidence files, rebuilds the Neo4j graph, and generates the timeline/report. Re-running it is safe for the packaged demo records.

# 5. Recommended judge demo workflow

The easiest way to understand the prototype is to follow this sequence:

### A. Command Overview

Open the dashboard and review the current case/data posture.

### B. Case Files

Create or open an investigation case. Admins can assign an approved investigator or supervisor.

### C. Entity Registry

Select **Add Entity**, choose the entity type, enter the available attributes and assign a case role.

Examples of entity types include:

```text
PERSON
PHONE
EMAIL
VEHICLE
LOCATION
ORGANIZATION
BANK_ACCOUNT
DOCUMENT
EVENT
```

### D. Relationships

Create links between entities such as association, communication, ownership or other investigative relationship types available in the prototype.

### E. Network Explorer

Open the Network Explorer to inspect:

- nodes and relationships
- entity types
- case roles
- relationship status/confidence
- search
- zoom and fit
- entity inspection
- graph reload

The network is backed by Neo4j while PostgreSQL remains the application system of record.

### F. Timeline

Open Timeline to see investigation events chronologically. Selecting an event can focus the associated entities in the investigation workflow.

### G. Map

Location-bearing entities can be inspected in the MapLibre view.

### H. Evidence

Upload a demonstration evidence file. Evidence upload is enabled only after the selected case has at least one real uploaded source document. The prototype records file metadata and a SHA-256 hash, and authorized users can use **View** to open the stored source/evidence file.

### I. Reports

Create a draft investigation report containing the findings demonstrated in the prototype.

### J. Audit

Super Admins and Auditors can inspect recorded application activity.

---

# 6. Local service URLs

| Service | URL |
|---|---|
| **HOSOSHI UI** | http://localhost:5173 |
| **Backend API** | http://localhost:8081 |
| **Swagger UI** | http://localhost:8081/swagger-ui/index.html |
| **Neo4j Browser** | http://localhost:7474 |
| **PostgreSQL** | localhost:5432 |

Neo4j local demo credentials:

```text
Username: neo4j
Password: hoshoshi_neo4j_dev
```

---

# 7. Technology stack

### Current prototype

**Frontend**

- React
- TypeScript
- Vite
- CSS
- Cytoscape.js
- MapLibre GL
- Axios

**Backend**

- Java 21
- Spring Boot
- Spring Security
- JWT
- Spring Data JPA
- Neo4j Java Driver
- Flyway

**Data / infrastructure**

- PostgreSQL 16
- Neo4j 5
- Docker / Docker Compose
- Nginx

**Security / integrity**

- Role-based authorization
- Case-level authorization
- Audit logging
- SHA-256 evidence metadata
- Optional AES-256-GCM application-layer JSON API payload protection

### Planned full-model intelligence layer

- Python / FastAPI
- Transformers / LLMs
- spaCy / NER
- scikit-learn
- NetworkX
- OCR
- BeautifulSoup4
- Scrapy
- Requests / HTTPX
- Playwright
- Tor / Onion Services
- DNS / WHOIS / IP intelligence

These planned technologies are **not claimed as currently implemented features**.

---

# 8. API encryption in the prototype

HOSOSHI includes an optional AES-256-GCM application-layer wrapper for operational JSON API traffic. The browser and backend use the same local demonstration key when encryption is enabled.

Authentication/bootstrap endpoints under `/api/v1/auth/*` remain normal JSON so login and account registration do not depend on the application-layer key.

This is a **prototype defense-in-depth control** and is not a replacement for HTTPS/TLS. The browser-visible key must not be treated as a production server secret.

Multipart file uploads remain standard multipart requests so the Evidence Vault workflow is not disrupted.

---

# 9. Repository structure

```text
HOSOSHI/
├── README.md
├── USER-GUIDE.md
├── SECURITY.md
├── CONTRIBUTING.md
├── .env.example
├── docker-compose.yml
├── START-HOSOSHI.cmd
├── STOP-HOSOSHI.cmd
├── VERIFY-HOSOSHI.cmd
│
├── .github/workflows/ci.yml
│
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│
└── frontend/
    ├── Dockerfile
    ├── package.json
    └── src/
```

Local files such as `.env`, `node_modules`, `dist`, `target`, logs and database volumes are intentionally excluded from the Git repository.

---

# 10. Troubleshooting

### Existing local demo data is in a bad state

Normal startup does **not** remove PostgreSQL, Neo4j or evidence volumes. Flyway versioned migrations run only once per database. For a deliberate local demo reset only, use:

```powershell
docker compose down -v
START-HOSOSHI.cmd
```

**Warning:** this deletes local prototype data. Do not run it when you need to preserve an investigation state.

## `x509: certificate signed by unknown authority`

This is a **Docker Desktop / network certificate problem**, not a HOSOSHI application error.

Check:

1. Docker Desktop → **Settings → Resources → Proxies**.
2. Disconnect a VPN temporarily if it is intercepting or filtering HTTPS.
3. Check whether antivirus or institutional proxy software performs HTTPS inspection.
4. Test:

```powershell
docker pull nginx:1.27-alpine
```

Do not disable TLS verification just to make the prototype run.

## Neo4j is unhealthy after changing credentials

Neo4j stores its database state in a Docker volume. Changing the password in `.env` does not automatically change the password inside an already-created Neo4j volume.

For a **fresh local demo reset only**:

```powershell
docker compose down -v
docker compose up -d --build
```

**Warning:** `down -v` deletes the local PostgreSQL and Neo4j volumes and therefore deletes local prototype data.

## Check service logs

```powershell
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 neo4j
docker compose logs --tail=100 frontend
```

## Rebuild without deleting data

```powershell
docker compose down
docker compose up -d --build
```

---

# 11. Configuration

The standard demo can run without manually creating configuration files because `START-HOSOSHI.cmd` creates `.env` from `.env.example` when `.env` is absent.

For custom local configuration:

```powershell
Copy-Item .env.example .env
```

Then edit `.env` before startup.

**Never commit `.env`.** Commit only `.env.example` with safe demonstration values/placeholders.

---

# 12. Development mode (optional)

The normal SIH judge path is Docker-based. Developers who want to run services outside Docker need Java 21, Maven 3.9+, Node 20+, PostgreSQL and Neo4j.

Frontend:

```powershell
cd frontend
npm ci
npm run build
```

Backend:

```powershell
cd backend
mvn test
```

Compose validation:

```powershell
docker compose config
```

---

# 13. Publishing / updating the GitHub repository (maintainer only)

The GitHub repository is the judge-facing prototype. Keep the repository free of local secrets and generated build output.

If you already have a local clone of `rtasur/HOSOSHI`, replace its project files with the contents of this release package **without deleting the existing `.git` folder**, then run:

```powershell
git status
git add -A
git commit -m "Prepare judge-ready SIH prototype"
git push origin main
```

Before pushing, confirm that `.env`, `node_modules`, `dist`, `target` and `*.tsbuildinfo` are not shown by `git status`.

For a fresh clone, judges use the simpler workflow already shown above:

```powershell
git clone https://github.com/rtasur/HOSOSHI.git
cd HOSOSHI
START-HOSOSHI.cmd
```

# 14. Linux / macOS Docker quick start (optional)

The primary SIH judge workflow is the Windows launcher above. On Linux/macOS, Docker Compose is supported, but the Windows launcher is the maintained judge path. Before starting manually, set non-empty `JWT_SECRET` and `API_ENCRYPTION_KEY` values in `.env` (the sample leaves them blank deliberately). Then use:

```bash
cp .env.example .env
# edit .env and set JWT_SECRET and API_ENCRYPTION_KEY
docker compose up -d --build
```

Then open `http://localhost:5173`. To stop without deleting data:

```bash
docker compose down
```

# 15. Prototype limitations

This repository is intentionally a **manual SIH demonstration prototype**.

The current application does **not** contain the final AI engine. Data ingestion is source/metadata tracking rather than a full automated intelligence pipeline. OSINT collection, controlled dark-web intelligence collection, automated NER, entity resolution, automated relationship extraction and graph/ML pattern detection are planned for the full model.

The prototype should not be used with real sensitive investigative data without appropriate legal, privacy, security and operational controls.

---

# 16. GitHub contribution basics

After cloning and making changes:

```powershell
git status
git add .
git commit -m "Describe the change"
git push
```

Before pushing, confirm that `.env` and generated files are not staged:

```powershell
git status
```

The target release state is:

```text
nothing to commit, working tree clean
```

---

## HOSOSHI in one line

**From scattered investigative data to a connected, reviewable intelligence workspace — with the present release demonstrating the workflow manually and the full model automating the intelligence pipeline.**
