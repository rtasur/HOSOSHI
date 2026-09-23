$ErrorActionPreference='Stop'
Write-Host '=== HOSOSHI DATABASE CHECK ===' -ForegroundColor Yellow
docker compose ps
Write-Host "`nPostgreSQL counts:" -ForegroundColor Cyan
docker compose exec postgres psql -U hososhi -d hososhi -c "select 'users' t,count(*) n from app_users union all select 'roles',count(*) from roles union all select 'cases',count(*) from investigation_cases union all select 'case_members',count(*) from case_members union all select 'entities',count(*) from entities union all select 'case_entities',count(*) from case_entities union all select 'relationships',count(*) from relationships union all select 'evidence',count(*) from evidence_files union all select 'ingestions',count(*) from data_ingestions union all select 'reports',count(*) from investigation_reports union all select 'audit',count(*) from audit_logs order by 1;"
Write-Host "`nNeo4j counts:" -ForegroundColor Cyan
docker compose exec neo4j cypher-shell -u neo4j -p (if(Test-Path .env){(Get-Content .env | ? {$_ -match '^NEO4J_PASSWORD='} | % { $_ -replace '^NEO4J_PASSWORD=',''}) } else {'hoshoshi_neo4j_dev'}) "MATCH (n:HEntity) RETURN count(n) AS nodes"
docker compose exec neo4j cypher-shell -u neo4j -p (if(Test-Path .env){(Get-Content .env | ? {$_ -match '^NEO4J_PASSWORD='} | % { $_ -replace '^NEO4J_PASSWORD=',''}) } else {'hoshoshi_neo4j_dev'}) "MATCH ()-[r:CASE_REL]->() RETURN count(r) AS relationships"
