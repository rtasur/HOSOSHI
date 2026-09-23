-- Reset local demo data and extend schema for launch-ready workflows.
DELETE FROM relationships;
DELETE FROM entities;
DELETE FROM case_members;
DELETE FROM investigation_cases;
DELETE FROM audit_logs;
DELETE FROM user_roles;
DELETE FROM app_users;
DELETE FROM roles;

ALTER TABLE app_users ADD COLUMN IF NOT EXISTS status VARCHAR(24) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS requested_role VARCHAR(64);

ALTER TABLE entities ADD COLUMN IF NOT EXISTS location_label VARCHAR(255);
ALTER TABLE entities ADD COLUMN IF NOT EXISTS location_lat DOUBLE PRECISION;
ALTER TABLE entities ADD COLUMN IF NOT EXISTS location_lng DOUBLE PRECISION;
ALTER TABLE entities ADD COLUMN IF NOT EXISTS source_reference VARCHAR(255);

CREATE TABLE IF NOT EXISTS evidence_files (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), case_id UUID NOT NULL REFERENCES investigation_cases(id) ON DELETE CASCADE, uploaded_by UUID NOT NULL REFERENCES app_users(id), original_name VARCHAR(500) NOT NULL, stored_name VARCHAR(500) NOT NULL, content_type VARCHAR(200), size_bytes BIGINT NOT NULL DEFAULT 0, sha256 VARCHAR(64), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX IF NOT EXISTS idx_evidence_case ON evidence_files(case_id);

CREATE TABLE IF NOT EXISTS data_ingestions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), case_id UUID NOT NULL REFERENCES investigation_cases(id) ON DELETE CASCADE, created_by UUID NOT NULL REFERENCES app_users(id), source_type VARCHAR(80) NOT NULL, file_name VARCHAR(500) NOT NULL, record_count BIGINT NOT NULL DEFAULT 0, status VARCHAR(40) NOT NULL, notes TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX IF NOT EXISTS idx_ingestion_case ON data_ingestions(case_id);

CREATE TABLE IF NOT EXISTS investigation_reports (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), case_id UUID NOT NULL REFERENCES investigation_cases(id) ON DELETE CASCADE, author_id UUID NOT NULL REFERENCES app_users(id), title VARCHAR(240) NOT NULL, content TEXT, status VARCHAR(24) NOT NULL DEFAULT 'DRAFT', created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX IF NOT EXISTS idx_reports_case ON investigation_reports(case_id);

CREATE TABLE IF NOT EXISTS investigation_sessions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE, case_id UUID NOT NULL REFERENCES investigation_cases(id) ON DELETE CASCADE, status VARCHAR(16) NOT NULL, state_json TEXT NOT NULL DEFAULT '{}', started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX IF NOT EXISTS idx_sessions_user_status ON investigation_sessions(user_id,status);
