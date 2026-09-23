-- Align the PostgreSQL column type with the Java Double field used by Relationship.confidence.
-- This is safe for existing values because confidence is constrained to numeric values.
ALTER TABLE relationships
    ALTER COLUMN confidence TYPE DOUBLE PRECISION
    USING confidence::double precision;
