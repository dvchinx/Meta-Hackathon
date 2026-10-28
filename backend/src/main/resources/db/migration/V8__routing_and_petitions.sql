-- Routing by what the photo shows. Manholes and poles belong to different utilities: Muse Spark reads the markings
-- (asset_owner) and the most specific rule wins over the 'ANY' default. Sidewalks and street furniture go to the
-- Alcaldía Local of the locality where the damage is (by_locality).
INSERT INTO agency (code, name) VALUES
    ('UMV',   'Unidad de Mantenimiento Vial'),
    ('UAESP', 'Unidad Administrativa Especial de Servicios Públicos'),
    ('VANTI', 'Vanti S.A. E.S.P.');

DROP TABLE agency_rule;

CREATE TABLE agency_rule (
    damage_type text    NOT NULL,
    asset_owner text    NOT NULL DEFAULT 'ANY',
    agency_code text REFERENCES agency (code),
    by_locality boolean NOT NULL DEFAULT false,
    PRIMARY KEY (damage_type, asset_owner),
    CHECK ((agency_code IS NULL) = by_locality)
);

INSERT INTO agency_rule (damage_type, asset_owner, agency_code, by_locality) VALUES
    ('POTHOLE',                  'ANY',             'UMV',   false),
    ('OPEN_MANHOLE',             'ANY',             'EAAB',  false),
    ('OPEN_MANHOLE',             'POWER',           'ENEL',  false),
    ('OPEN_MANHOLE',             'GAS',             'VANTI', false),
    ('OPEN_MANHOLE',             'TELECOM',         NULL,    true),
    ('DAMAGED_POLE',             'ANY',             'ENEL',  false),
    ('DAMAGED_POLE',             'STREET_LIGHTING', 'UAESP', false),
    ('DAMAGED_POLE',             'TELECOM',         NULL,    true),
    ('RISKY_TREE',               'ANY',             'SDA',   false),
    ('STREETLIGHT_OUT',          'ANY',             'UAESP', false),
    ('WATER_LEAK',               'ANY',             'EAAB',  false),
    ('BLOCKED_DRAIN',            'ANY',             'EAAB',  false),
    ('GARBAGE_DUMP',             'ANY',             'UAESP', false),
    ('DAMAGED_SIDEWALK',         'ANY',             NULL,    true),
    ('DAMAGED_STREET_FURNITURE', 'ANY',             NULL,    true);

-- Where a citizen can report for real, shown next to our simulated filing. A row with agency_code NULL is the
-- default for the damage type. Sources: bogota.gov.co/yo-participo/reporta-sdqs and the agencies' public lines.
CREATE TABLE official_channel (
    id          integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    damage_type text NOT NULL,
    agency_code text REFERENCES agency (code),
    url         text NOT NULL,
    phone       text,
    UNIQUE NULLS NOT DISTINCT (damage_type, agency_code)
);

INSERT INTO official_channel (damage_type, agency_code, url, phone) VALUES
    ('POTHOLE',                  NULL,    'https://bogota.gov.co/yo-participo/reporta-sdqs/huecos',            'Línea 195'),
    ('OPEN_MANHOLE',             NULL,    'https://bogota.gov.co/sdqs/',                                       'Acualínea 116'),
    ('OPEN_MANHOLE',             'ENEL',  'https://www.enel.com.co/',                                          'Línea 115'),
    ('OPEN_MANHOLE',             'VANTI', 'https://bogota.gov.co/sdqs/',                                       NULL),
    ('DAMAGED_POLE',             NULL,    'https://www.enel.com.co/',                                          'Línea 115'),
    ('DAMAGED_POLE',             'UAESP', 'https://bogota.gov.co/yo-participo/reporta-sdqs/alumbrado-publico', 'Línea 115'),
    ('RISKY_TREE',               NULL,    'https://bogota.gov.co/sdqs/',                                       'Línea 123'),
    ('STREETLIGHT_OUT',          NULL,    'https://bogota.gov.co/yo-participo/reporta-sdqs/alumbrado-publico', 'Línea 115'),
    ('WATER_LEAK',               NULL,    'https://bogota.gov.co/sdqs/',                                       'Acualínea 116'),
    ('BLOCKED_DRAIN',            NULL,    'https://bogota.gov.co/sdqs/',                                       'Acualínea 116'),
    ('GARBAGE_DUMP',             NULL,    'https://bogota.gov.co/yo-participo/reporta-sdqs/basuras',           NULL),
    ('DAMAGED_SIDEWALK',         NULL,    'https://bogota.gov.co/yo-participo/reporta-sdqs/espacio-publico',   'Línea 195'),
    ('DAMAGED_STREET_FURNITURE', NULL,    'https://bogota.gov.co/yo-participo/reporta-sdqs/espacio-publico',   'Línea 195');

-- Data the agencies ask of a petitioner. Optional, only for citizens who decide to file. Encrypted by the backend
-- (AES-256-GCM, PERSONAL_DATA_KEY): the database never sees the plain document number.
CREATE TABLE filer_profile (
    user_id                uuid PRIMARY KEY REFERENCES user_account (id) ON DELETE CASCADE,
    data                   bytea       NOT NULL,
    consent_policy_version text        NOT NULL,
    consented_at           timestamptz NOT NULL,
    updated_at             timestamptz NOT NULL
);

-- Simulated petitions (derecho de petición). filer_data is the encrypted snapshot shared with the agency.
CREATE SEQUENCE petition_seq;

CREATE TABLE petition (
    id                     uuid PRIMARY KEY,
    issue_id               uuid        NOT NULL REFERENCES issue (id),
    agency_code            text        NOT NULL REFERENCES agency (code),
    filer_user_id          uuid REFERENCES user_account (id) ON DELETE SET NULL,
    radicado               text        NOT NULL UNIQUE,
    filed_at               timestamptz NOT NULL,
    due_date               date        NOT NULL,
    subject                text        NOT NULL,
    filer_data             bytea       NOT NULL,
    consent_policy_version text        NOT NULL
);

CREATE INDEX petition_issue_idx ON petition (issue_id, filed_at DESC);
CREATE INDEX petition_filer_idx ON petition (filer_user_id);
