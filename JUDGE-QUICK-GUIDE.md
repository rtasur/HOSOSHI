# HOSOSHI — Judge Quick Guide

## Current release

> **Current Prototype: Manual Version without the AI Engine.**
>
> This release demonstrates the complete investigative workflow using operator-entered data. The proposed full system will automate approved-source ingestion, NLP/entity extraction, entity resolution, relationship extraction, OSINT correlation, controlled dark-web intelligence collection and graph/ML analysis, with investigator validation remaining in the loop.

## Start

Requirements: Windows 10/11, Docker Desktop (Linux/WSL2 engine), Git.

```cmd
git clone https://github.com/rtasur/HOSOSHI.git
cd HOSOSHI
START-HOSOSHI.cmd
```

Open: **http://localhost:5173**

## Packaged demo case

The repository includes the synthetic **Operation Silent Harbor** case pack under `HOSOSHI-DEMO-CASE-PACK/`.

After HOSOSHI is running, load it with:

```powershell
powershell -ExecutionPolicy Bypass -File ".\HOSOSHI-DEMO-CASE-PACK\scripts\LOAD-HOSOSHI-DEMO.ps1"
```

It creates/reuses case **CNI-2026-0091**, assigns the demo investigation team, loads synthetic source documents/evidence, builds the Neo4j graph, and generates the timeline/report.

## Demo accounts

| Role | Username | Password |
|---|---|---|
| Super Admin | `admin` | `123456` |
| Investigation Supervisor | `supervisor` | `supervisor123` |
| Investigator | `investigator` | `investigator123` |
| Intelligence Analyst | `analyst` | `analyst123` |
| Data Operator | `operator` | `operator123` |
| Auditor | `auditor` | `auditor123` |

## Demo flow

1. Sign in as **admin** or **supervisor**.
2. Open **Operation Silent Harbor**.
3. Review the source documents in **Data Ingestion** and use **View** on an uploaded file.
4. Open **Network Explorer** and inspect the Neo4j-backed relationship graph.
5. Review **Timeline**, **Map**, **Evidence** and **Reports**.
6. In **Evidence Vault**, note that evidence upload requires a real source document in the same case.
7. Use **View** to open an uploaded evidence file.
8. Open **Audit** as Super Admin/Auditor to review file upload/view events.
9. Sign in as `investigator` to demonstrate case-level access isolation.

## Verify

After startup and, optionally, after loading the demo:

```cmd
VERIFY-HOSOSHI.cmd
```

The verifier performs application-level smoke tests for Docker/Compose, PostgreSQL, Neo4j, frontend/backend readiness, security headers, JWT authentication, all six roles, protected APIs, case creation/assignment, case isolation, dashboard/graph/timeline/report endpoints, source/evidence file controls, file-type validation, audit events and the frontend View/gating controls.

The verifier creates/reuses only `CNI-VERIFY-001` and `CNI-VERIFY-002`. It does not reset the database.

## Stop

```cmd
STOP-HOSOSHI.cmd
```

Database/evidence volumes are preserved.

## URLs

- UI: http://localhost:5173
- Backend: http://localhost:8081
- Swagger: http://localhost:8081/swagger-ui/index.html
- Neo4j: http://localhost:7474

## Current feature boundary

Implemented now: manual case management, case assignment, RBAC/JWT authentication, case-level access control, entity registry, relationships, graph projection/network explorer, timeline, map, evidence metadata and file viewing, source-document ingestion and file viewing, reports, audit activity and optional application-layer API payload encryption.

Future full-model capability: AI/NLP-assisted ingestion and intelligence automation. Do not present the current manual prototype as already containing the final AI engine.
