ALTER TABLE entities
    ADD COLUMN IF NOT EXISTS map_code VARCHAR(3),
    ADD COLUMN IF NOT EXISTS alias VARCHAR(200),
    ADD COLUMN IF NOT EXISTS case_role VARCHAR(40),
    ADD COLUMN IF NOT EXISTS date_of_birth DATE,
    ADD COLUMN IF NOT EXISTS gender VARCHAR(40),
    ADD COLUMN IF NOT EXISTS nationality VARCHAR(80),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(50),
    ADD COLUMN IF NOT EXISTS email VARCHAR(200),
    ADD COLUMN IF NOT EXISTS confidence DOUBLE PRECISION;

UPDATE entities
SET map_code = LEFT(reference_code, 3)
WHERE map_code IS NULL OR map_code = '';

ALTER TABLE entities
    ALTER COLUMN map_code SET NOT NULL;

ALTER TABLE relationships
    ADD COLUMN IF NOT EXISTS source_reference VARCHAR(255),
    ADD COLUMN IF NOT EXISTS observed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS verified_by VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_entities_type_name ON entities(entity_type, primary_name);
CREATE INDEX IF NOT EXISTS idx_entities_reference ON entities(reference_code);
CREATE INDEX IF NOT EXISTS idx_relationships_case_source ON relationships(case_id, source_entity_id);
CREATE INDEX IF NOT EXISTS idx_relationships_case_target ON relationships(case_id, target_entity_id);
