CREATE TABLE IF NOT EXISTS case_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES investigation_cases(id) ON DELETE CASCADE,
    entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    case_role VARCHAR(40),
    registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    confidence DOUBLE PRECISION,
    source_reference VARCHAR(255),
    CONSTRAINT uk_case_entity UNIQUE(case_id, entity_id)
);
INSERT INTO case_entities (case_id, entity_id, case_role, registered_at, status, confidence, source_reference)
SELECT e.case_id, e.id, e.case_role, e.created_at, e.status, e.confidence, e.source_reference
FROM entities e
WHERE e.case_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM case_entities ce WHERE ce.case_id=e.case_id AND ce.entity_id=e.id);
CREATE INDEX IF NOT EXISTS idx_case_entities_case ON case_entities(case_id);
CREATE INDEX IF NOT EXISTS idx_case_entities_entity ON case_entities(entity_id);
CREATE INDEX IF NOT EXISTS idx_case_entities_registered ON case_entities(registered_at DESC);
ALTER TABLE entities ALTER COLUMN case_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_entities_reference_code_global ON entities(reference_code);
