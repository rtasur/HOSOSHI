# HOSOSHI Release Verification

This release was checked before packaging.

## Static checks completed

- YAML: `docker-compose.yml` and `backend/src/main/resources/application.yml` parsed successfully.
- XML: `backend/pom.xml` parsed successfully.
- JSON: frontend configuration and demo JSON files parsed successfully.
- TypeScript/TSX: all 18 source files parsed without syntax diagnostics.
- Java: all source files passed structural brace checks; a dependency-free `javac` pass reported only missing Spring/JPA/Lombok dependencies because Maven dependencies are not installed in the verification environment, with no Java syntax-style errors found.
- PowerShell: release scripts passed bracket/structure checks.
- RBAC: exactly the six required `RoleCode` values are present.
- Release hygiene: no `.env`, `target`, `node_modules`, `dist`, IDE artifacts or local test-evidence files are included.
- Required security/file features are present: JWT secret validation, upload validation, case-scoped file access, source/evidence View endpoints, evidence source-document prerequisite, audit events, and Timeline transaction boundary.

## End-to-end checks to run on Windows

The final runtime environment is the judge/developer Windows machine with Docker Desktop. Run:

```cmd
START-HOSOSHI.cmd
VERIFY-HOSOSHI.cmd
```

Then optionally load the packaged demo:

```powershell
powershell -ExecutionPolicy Bypass -File ".\HOSOSHI-DEMO-CASE-PACK\scripts\LOAD-HOSOSHI-DEMO.ps1"
```

The verifier exercises infrastructure readiness, JWT/RBAC, protected APIs, case assignment and isolation, dashboard/entities/relationships/ingestion/evidence/timeline/graph/reports, source/evidence upload and file viewing, unsupported-file rejection and audit events.

## Environment limitation

Docker Engine and Maven were not available in the packaging environment, so a real Docker image build and live multi-container execution could not be repeated here. Those checks are intentionally delegated to `START-HOSOSHI.cmd` and `VERIFY-HOSOSHI.cmd` on the target Windows machine.
