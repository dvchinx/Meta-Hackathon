CREATE TABLE media (
    id           uuid PRIMARY KEY,
    kind         text        NOT NULL,
    visibility   text        NOT NULL CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    content_type text        NOT NULL,
    sha256       text        NOT NULL,
    size_bytes   bigint      NOT NULL,
    derived_from uuid REFERENCES media (id),
    created_at   timestamptz NOT NULL
);

CREATE INDEX media_sha256_idx ON media (sha256);

CREATE TABLE report (
    id             uuid PRIMARY KEY,
    channel        text        NOT NULL,
    -- Salted HMAC of the phone number or web session token. Never the number itself.
    reporter_id    text        NOT NULL,
    status         text        NOT NULL,
    photo_media_id uuid REFERENCES media (id),
    context        text,
    latitude       double precision,
    longitude      double precision,
    interpretation jsonb,
    analysis       jsonb,
    issue_id       uuid REFERENCES issue (id),
    review_reason  text,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL
);

CREATE INDEX report_reporter_created_idx ON report (reporter_id, created_at DESC);
CREATE INDEX report_issue_idx ON report (issue_id);

-- Cache and cost log of every model call (Muse Spark and SAM through vision-service).
CREATE TABLE model_call (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cache_key     text          NOT NULL,
    purpose       text          NOT NULL,
    model         text          NOT NULL,
    response      text,
    input_tokens  integer       NOT NULL DEFAULT 0,
    output_tokens integer       NOT NULL DEFAULT 0,
    images        integer       NOT NULL DEFAULT 0,
    cost_usd      numeric(12, 6) NOT NULL,
    success       boolean       NOT NULL,
    created_at    timestamptz   NOT NULL
);

-- One cached answer per input; failed attempts can repeat (and are still billed).
CREATE UNIQUE INDEX model_call_cache_idx ON model_call (cache_key) WHERE success;
