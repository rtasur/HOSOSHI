-- Local launch package reset: remove any legacy/demo investigation data so the UI starts clean.
DELETE FROM evidence_files;
DELETE FROM data_ingestions;
DELETE FROM investigation_reports;
DELETE FROM investigation_sessions;
DELETE FROM relationships;
DELETE FROM entities;
DELETE FROM case_members;
DELETE FROM investigation_cases;
DELETE FROM audit_logs;
DELETE FROM user_roles;
DELETE FROM app_users;
DELETE FROM roles;
