-- The files people hand the application. The bytes live in object storage and a row here says what they
-- are: one row for each file somebody stored, naming the bytes by their SHA-256, so the same bytes stored
-- twice are rendered once. What the renditions of an image are is kept per set of widths, since a portrait
-- and an attachment are drawn at different sizes from the same bytes.
CREATE TABLE media_object (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference UUID NOT NULL UNIQUE,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('IMAGE', 'PDF')),
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('ATTACHMENT', 'PORTRAIT')),
    original_name VARCHAR(255) NOT NULL CHECK (original_name <> ''),
    content_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size > 0),
    sha256 VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX media_object_sha256 ON media_object (sha256);

-- The widths an image's bytes were rendered at for one purpose, and the size of the largest. A file with no
-- picture to draw, such as a PDF whose first page could not be read, has none.
CREATE TABLE media_rendition (
    sha256 VARCHAR(64) NOT NULL,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('ATTACHMENT', 'PORTRAIT')),
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0),
    widths VARCHAR(100) NOT NULL CHECK (widths ~ '^[0-9]+(,[0-9]+)*$'),
    PRIMARY KEY (sha256, purpose)
);

-- A file attached to a task. Removing the task removes the attachment; the file itself is removed by the
-- application, which also removes its bytes.
CREATE TABLE task_attachment (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    media_id BIGINT NOT NULL UNIQUE REFERENCES media_object (id),
    added_by BIGINT NOT NULL REFERENCES app_user (id),
    added_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX task_attachment_task ON task_attachment (task_id, added_at);

-- The picture an account is shown with. An account has one or none.
CREATE TABLE user_portrait (
    user_id BIGINT PRIMARY KEY REFERENCES app_user (id),
    media_id BIGINT NOT NULL UNIQUE REFERENCES media_object (id),
    set_at TIMESTAMP WITH TIME ZONE NOT NULL
);
