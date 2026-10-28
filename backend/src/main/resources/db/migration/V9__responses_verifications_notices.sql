-- Agency replies (typed in the admin panel: agencies are simulated) and Muse Spark's reading of them.
CREATE TABLE agency_response (
    id             uuid PRIMARY KEY,
    issue_id       uuid        NOT NULL REFERENCES issue (id),
    body           text        NOT NULL,
    classification text        NOT NULL CHECK (classification IN ('RESOLVES', 'EVASIVE', 'NO_RESPONSE')),
    summary        text        NOT NULL,
    received_at    timestamptz NOT NULL
);

CREATE INDEX agency_response_issue_idx ON agency_response (issue_id, received_at);

-- Before/after checks. Both signals are kept: SAM areas and Muse Spark's judgement.
CREATE TABLE verification (
    id              uuid PRIMARY KEY,
    issue_id        uuid             NOT NULL REFERENCES issue (id),
    verifier_id     text             NOT NULL, -- salted hash, like report.reporter_id
    photo_media_id  uuid             NOT NULL REFERENCES media (id),
    public_media_id uuid             NOT NULL REFERENCES media (id),
    area_before     double precision NOT NULL,
    area_after      double precision NOT NULL,
    judgement       text             NOT NULL,
    confidence      double precision NOT NULL,
    reason          text             NOT NULL,
    outcome         text             NOT NULL CHECK (outcome IN ('REPAIRED', 'NOT_REPAIRED', 'INCONCLUSIVE')),
    created_at      timestamptz      NOT NULL
);

CREATE INDEX verification_issue_idx ON verification (issue_id, created_at);
CREATE INDEX verification_verifier_idx ON verification (verifier_id, created_at);

-- Who hears about an Issue. Web: the account (its verified email). WhatsApp: the number, encrypted with
-- PERSONAL_DATA_KEY, only for these notices, deleted once the Issue is verified or when the citizen writes BAJA.
CREATE TABLE issue_follower (
    issue_id     uuid        NOT NULL REFERENCES issue (id),
    follower_key text        NOT NULL, -- salted reporter hash: one row per person
    channel      text        NOT NULL CHECK (channel IN ('WEB', 'WHATSAPP')),
    user_id      uuid REFERENCES user_account (id) ON DELETE CASCADE,
    recipient    bytea,
    created_at   timestamptz NOT NULL,
    PRIMARY KEY (issue_id, follower_key),
    CHECK ((channel = 'WEB') = (user_id IS NOT NULL)),
    CHECK ((channel = 'WHATSAPP') = (recipient IS NOT NULL))
);

CREATE INDEX issue_follower_key_idx ON issue_follower (follower_key);

-- After a "the agency says it is repaired" notice, the WhatsApp follower's next photo verifies that Issue.
CREATE TABLE pending_verification (
    follower_key text PRIMARY KEY,
    issue_id     uuid        NOT NULL REFERENCES issue (id),
    expires_at   timestamptz NOT NULL
);

-- Status changes to notify, written in the same transaction as the Issue (transactional outbox).
CREATE TABLE notification_outbox (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    issue_id   uuid        NOT NULL REFERENCES issue (id),
    to_status  text        NOT NULL,
    created_at timestamptz NOT NULL,
    sent_at    timestamptz
);

CREATE INDEX notification_outbox_pending_idx ON notification_outbox (id) WHERE sent_at IS NULL;
