-- The tasks. Each names the account that keeps it, and the version is what refuses a change made
-- against a task somebody else changed since.
CREATE TABLE task (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference UUID NOT NULL UNIQUE,
    owner_id BIGINT NOT NULL REFERENCES app_user (id),
    title VARCHAR(120) NOT NULL CHECK (title <> ''),
    description VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'DONE')),
    due_on DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);

CREATE INDEX task_owner ON task (owner_id);
