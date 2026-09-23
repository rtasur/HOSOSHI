# HOSOSHI — Judge / User Guide

HOSOSHI is the **manual prototype** for SIH26189. It does not contain the final AI engine. The purpose of this release is to let judges clone the repository, start the complete local stack, and inspect the investigative workflow. The full model will automate approved-source ingestion, AI extraction, entity resolution, relationship extraction, OSINT correlation, controlled dark-web intelligence collection and graph/ML analysis.

## Start here

```powershell
git clone https://github.com/rtasur/HOSOSHI.git
cd HOSOSHI
START-HOSOSHI.cmd
```

Then open:

**http://localhost:5173**

Demo login:

```text
admin / 123456
```

Packaged demo case (optional):

```powershell
powershell -ExecutionPolicy Bypass -File ".\HOSOSHI-DEMO-CASE-PACK\scripts\LOAD-HOSOSHI-DEMO.ps1"
```

Verify after startup:

```cmd
VERIFY-HOSOSHI.cmd
```

Stop without deleting data:

```cmd
STOP-HOSOSHI.cmd
```

## Recommended demonstration path

1. **Cases** — create or open an investigation.
2. **Assignment** — Super Admin or Investigation Supervisor assigns approved investigators/analysts/data operators according to case-access rules.
3. **Entities** — add PERSON/PHONE/EMAIL/VEHICLE/LOCATION/ORGANIZATION/BANK_ACCOUNT/DOCUMENT/EVENT records.
4. **Case roles** — distinguish the entity type from its role in the investigation.
5. **Relationships** — connect entities and optionally verify a relationship.
6. **Network Explorer** — inspect the graph, search nodes, zoom, fit, reload and inspect details.
7. **Timeline** — review investigative activity chronologically.
8. **Map** — inspect location-bearing entities.
9. **Evidence** — upload a safe demo file, use **View** to open it, and inspect its SHA-256 metadata.
10. **Reports** — create a draft investigation report.
11. **Audit** — Super Admin/Auditor can inspect recorded operational events.

## Local URLs

- UI: http://localhost:5173
- API: http://localhost:8081
- Swagger: http://localhost:8081/swagger-ui/index.html
- Neo4j: http://localhost:7474

## Important prototype limitation

Do not describe the current release as an automated AI system. The current release is the **manual demonstration version without the final AI engine**. AI/OSINT/dark-web automation belongs to the planned full model.
