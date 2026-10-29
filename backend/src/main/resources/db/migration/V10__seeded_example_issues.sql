-- Example Issues loaded for the demo (infra/seed/): AI-edited photos over Mapillary street imagery. The map labels
-- them as examples and infra/seed/purge.sql removes a whole batch. Null on every real Issue.
ALTER TABLE issue ADD COLUMN seed_batch text;
-- Attribution shown with the example, e.g. the Mapillary author and licence of the base photo.
ALTER TABLE issue ADD COLUMN seed_credit text;

CREATE INDEX issue_seed_batch_idx ON issue (seed_batch) WHERE seed_batch IS NOT NULL;
