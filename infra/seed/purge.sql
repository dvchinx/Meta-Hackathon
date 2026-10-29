-- Deletes every demo example Issue of one batch, with everything that hangs from it (also what real citizens added
-- to an example later: "yo también", petitions, verification photos). Real Issues are never touched: they have no
-- seed_batch. The media files stay on disk; the query at the end lists them.
--
--   psql -v batch=demo-2026-10 -f purge.sql
BEGIN;

CREATE TEMP TABLE seed_issue ON COMMIT DROP AS
SELECT id FROM issue WHERE seed_batch = :'batch';

CREATE TEMP TABLE seed_media ON COMMIT DROP AS
WITH RECURSIVE m (id) AS (
    SELECT photo_media_id FROM report WHERE issue_id IN (SELECT id FROM seed_issue)
    UNION SELECT cover_media_id FROM issue WHERE id IN (SELECT id FROM seed_issue)
    UNION SELECT photo_media_id FROM verification WHERE issue_id IN (SELECT id FROM seed_issue)
    UNION SELECT public_media_id FROM verification WHERE issue_id IN (SELECT id FROM seed_issue)
    UNION SELECT media.id FROM media JOIN m ON media.derived_from = m.id
)
SELECT media.id, media.visibility FROM media JOIN m USING (id);

DELETE FROM verification WHERE issue_id IN (SELECT id FROM seed_issue);
DELETE FROM agency_response WHERE issue_id IN (SELECT id FROM seed_issue);
DELETE FROM petition WHERE issue_id IN (SELECT id FROM seed_issue);
DELETE FROM issue_endorsement WHERE issue_id IN (SELECT id FROM seed_issue);
DELETE FROM issue_follower WHERE issue_id IN (SELECT id FROM seed_issue);
DELETE FROM pending_verification WHERE issue_id IN (SELECT id FROM seed_issue);
DELETE FROM notification_outbox WHERE issue_id IN (SELECT id FROM seed_issue);
DELETE FROM report WHERE issue_id IN (SELECT id FROM seed_issue);
DELETE FROM status_transition WHERE issue_id IN (SELECT id FROM seed_issue);
DELETE FROM issue WHERE id IN (SELECT id FROM seed_issue);
DELETE FROM media WHERE id IN (SELECT id FROM seed_media);

-- Files to remove from the media volume.
SELECT lower(visibility) || '/' || id AS file FROM seed_media;

COMMIT;
