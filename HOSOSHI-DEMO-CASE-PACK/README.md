# HOSOSHI Demo Case Pack — Operation Silent Harbor

All names, identifiers, phone numbers, account numbers, dates, and locations in this pack are fictional synthetic data for testing only.

## Demo objective

Exercise the full prototype workflow:
- login and JWT authentication
- supervisor case creation
- case assignment
- entity creation
- relationship creation
- evidence upload
- ingestion upload
- investigation report creation
- timeline
- Network Explorer / Neo4j projection
- dashboard counters
- audit logging

## Fastest way

1. Start HOSOSHI with `START-HOSOSHI.cmd`.
2. Log in at `http://localhost:5173` as:
   - username: `supervisor`
   - password: `supervisor123`
3. Use Case Files to create the case described in `data/case.json`.
4. Assign:
   - `investigator` as INVESTIGATOR
   - `analyst` as INTELLIGENCE_ANALYST
   - `operator` as DATA_OPERATOR
5. Use Entity Registry to create the 10 entities in `data/entities.json`.
6. Use the Network/relationship UI or API to create the relationships listed in `data/relationships.json`.
7. Upload the documents from `documents/` as evidence/ingestion records.
8. Open Timeline and Network Explorer for the case.
9. Log in as `auditor` and verify that the audit trail is visible but modifications are denied.

## Automated load

From the HOSOSHI project root, after services are healthy:

```powershell
powershell -ExecutionPolicy Bypass -File .\DEMO-CASE-PACK\scripts\LOAD-HOSOSHI-DEMO.ps1
```

The script uses the application's REST APIs with the demo supervisor account and creates the complete synthetic case, assignments, entities, relationships, evidence files, report, timeline, and Neo4j graph projection.

## Manual database checks

PostgreSQL:

```powershell
docker compose exec postgres psql -U hososhi -d hososhi -c "select count(*) as users from app_users; select count(*) as roles from roles; select count(*) as cases from investigation_cases; select count(*) as case_members from case_members; select count(*) as entities from entities; select count(*) as case_entities from case_entities; select count(*) as relationships from relationships; select count(*) as evidence from evidence_files; select count(*) as ingestions from data_ingestions; select count(*) as reports from investigation_reports; select count(*) as audit from audit_logs;"
```

Neo4j is rebuilt when the graph endpoint is opened for a case. You can verify it in the Neo4j Browser at `http://localhost:7474` using the local credentials in your `.env`.

Example Cypher:

```cypher
MATCH (n:HEntity) RETURN count(n) AS nodes;
MATCH ()-[r:CASE_REL]->() RETURN count(r) AS relationships;
```

## Expected demo case

Case number: `CNI-2026-0091`
Title: `Operation Silent Harbor`
Priority: `HIGH`
Classification: `RESTRICTED`
Status: `OPEN`

This pack is intentionally fictional and must not be treated as real investigative intelligence.


## Compatibility note
The loader uses explicit UTF-8 JSON bytes for POST requests so Windows PowerShell 5.1 does not corrupt non-ASCII JSON payloads.


Source files and evidence files can be viewed from the HOSOSHI UI after loading the demo.
