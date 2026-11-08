-- The audit trail. An entry is written once: the trigger refuses every update and every delete, so an
-- entry stays what it was written as even against a write that bypasses the application.
CREATE TABLE audit_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor VARCHAR(180) NOT NULL,
    action VARCHAR(40) NOT NULL,
    target VARCHAR(180) NOT NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCEEDED', 'REFUSED'))
);

CREATE INDEX audit_event_target ON audit_event (target, occurred_at);

CREATE FUNCTION audit_event_is_written_once() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'An audit event is written once and never changed or removed';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_written_once
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_is_written_once();
