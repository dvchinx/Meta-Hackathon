-- Agencies are simulated: petitions are generated but never filed for real, so no contact data is seeded.
CREATE TABLE agency (
    code           text PRIMARY KEY,
    name           text NOT NULL,
    petition_email text
);

-- Which agency is responsible for each damage type. OTHER has no rule: manual review.
CREATE TABLE agency_rule (
    damage_type text PRIMARY KEY,
    agency_code text NOT NULL REFERENCES agency (code)
);

INSERT INTO agency (code, name) VALUES
    ('IDU',  'Instituto de Desarrollo Urbano'),
    ('EAAB', 'Empresa de Acueducto y Alcantarillado de Bogotá'),
    ('ENEL', 'Enel Colombia'),
    ('SDA',  'Secretaría Distrital de Ambiente');

INSERT INTO agency_rule (damage_type, agency_code) VALUES
    ('POTHOLE',      'IDU'),
    ('OPEN_MANHOLE', 'EAAB'),
    ('DAMAGED_POLE', 'ENEL'),
    ('RISKY_TREE',   'SDA');

CREATE TABLE issue (
    id             uuid PRIMARY KEY,
    damage_type    text             NOT NULL,
    status         text             NOT NULL,
    latitude       double precision NOT NULL,
    longitude      double precision NOT NULL,
    -- Derived by PostgreSQL so the domain never deals with geometry types.
    location       geography(Point, 4326) GENERATED ALWAYS AS (
                       ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    report_count   integer          NOT NULL CHECK (report_count > 0),
    opened_at      timestamptz      NOT NULL,
    agency_code    text REFERENCES agency (code),
    cover_media_id uuid,
    version        bigint           NOT NULL DEFAULT 0
);

CREATE INDEX issue_location_gix ON issue USING gist (location);
CREATE INDEX issue_open_by_type_idx ON issue (damage_type) WHERE status <> 'VERIFIED';

-- Append-only audit log: rows are never updated or deleted.
CREATE TABLE status_transition (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    issue_id     uuid        NOT NULL REFERENCES issue (id),
    seq          integer     NOT NULL,
    from_status  text,
    to_status    text        NOT NULL,
    cause        text        NOT NULL,
    evidence_ref text        NOT NULL,
    at           timestamptz NOT NULL,
    UNIQUE (issue_id, seq)
);
