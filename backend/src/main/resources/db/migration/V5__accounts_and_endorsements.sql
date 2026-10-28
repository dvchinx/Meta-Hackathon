-- Citizen accounts for the web app. WhatsApp users are identified by their (hashed) number and need no account.
CREATE TABLE user_account (
    id                uuid PRIMARY KEY,
    email             text        NOT NULL UNIQUE, -- stored lowercased and trimmed
    display_name      text        NOT NULL,
    password_hash     text,                       -- null for accounts created through Google sign-in
    email_verified_at timestamptz,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL
);

-- Browser sessions. Only the SHA-256 of the cookie value is stored.
CREATE TABLE user_session (
    token_hash   text        PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    created_at   timestamptz NOT NULL,
    expires_at   timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL
);

CREATE INDEX user_session_user_idx ON user_session (user_id);

-- Single-use links sent by email (verify address, reset password). Hashed like sessions.
CREATE TABLE email_token (
    token_hash text        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    purpose    text        NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    expires_at timestamptz NOT NULL,
    used_at    timestamptz
);

-- "I saw it too": a citizen joins an existing Issue without a full report. Counted once per person.
CREATE TABLE issue_endorsement (
    issue_id    uuid        NOT NULL REFERENCES issue (id),
    reporter_id text        NOT NULL,
    created_at  timestamptz NOT NULL,
    PRIMARY KEY (issue_id, reporter_id)
);
