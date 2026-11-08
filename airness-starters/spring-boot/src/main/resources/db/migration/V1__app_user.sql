-- The accounts that sign in. An address is stored in one canonical spelling, so the unique constraint
-- is the rule that no two accounts sign in with one mailbox.
CREATE TABLE app_user (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference UUID NOT NULL UNIQUE,
    email VARCHAR(180) NOT NULL UNIQUE CHECK (email = lower(trim(email)) AND email <> ''),
    full_name VARCHAR(120) NOT NULL CHECK (full_name <> ''),
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    enabled BOOLEAN NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    failed_attempts INTEGER NOT NULL CHECK (failed_attempts >= 0),
    locked_until TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
