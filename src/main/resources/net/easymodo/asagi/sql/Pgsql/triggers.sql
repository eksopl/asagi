CREATE OR REPLACE FUNCTION %%BOARD%%_update_thread(n_row "%%BOARD%%") RETURNS void AS $$
BEGIN
  UPDATE
    %%BOARD%%_threads AS op
  SET
    time_last = (
      COALESCE(GREATEST(
        op.time_op,
        (SELECT MAX(timestamp) FROM %%BOARD%% re WHERE
          re.thread_num = $1.thread_num AND re.subnum = 0)
      ), op.time_op)
    ),
    time_bump = (
      COALESCE(GREATEST(
        op.time_op,
        (SELECT MAX(timestamp) FROM %%BOARD%% re WHERE
          re.thread_num = $1.thread_num AND (re.email <> 'sage' OR re.email IS NULL)
          AND re.subnum = 0)
      ), op.time_op)
    ),
    time_ghost = (
      SELECT MAX(timestamp) FROM %%BOARD%% re WHERE
        re.thread_num = $1.thread_num AND re.subnum <> 0
    ),
    time_ghost_bump = (
      SELECT MAX(timestamp) FROM %%BOARD%% re WHERE
        re.thread_num = $1.thread_num AND re.subnum <> 0 AND (re.email <> 'sage' OR
          re.email IS NULL)
    ),
    time_last_modified = (
      COALESCE(GREATEST(
        op.time_op,
        (SELECT GREATEST(MAX(timestamp), MAX(timestamp_expired)) FROM %%BOARD%% re WHERE
          re.thread_num = $1.thread_num)
      ), op.time_op)
    ),
    nreplies = (
      SELECT COUNT(*) FROM %%BOARD%% re WHERE
        re.thread_num = $1.thread_num
    ),
    nimages = (
      SELECT COUNT(media_hash) FROM %%BOARD%% re WHERE
        re.thread_num = $1.thread_num
    )
    WHERE op.thread_num = $1.thread_num;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION %%BOARD%%_create_thread(n_row "%%BOARD%%") RETURNS void AS $$
BEGIN
  IF n_row.op = false THEN RETURN; END IF;
  INSERT INTO %%BOARD%%_threads SELECT $1.num, $1.timestamp, $1.timestamp,
      $1.timestamp, NULL, NULL, $1.timestamp, 0, 0, false, false WHERE NOT EXISTS (SELECT 1 FROM %%BOARD%%_threads WHERE thread_num=$1.num);
  RETURN;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION %%BOARD%%_delete_thread(n_parent integer) RETURNS void AS $$
BEGIN
  DELETE FROM %%BOARD%%_threads WHERE thread_num = n_parent;
  RETURN;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION %%BOARD%%_insert_image(n_row "%%BOARD%%") RETURNS integer AS $$
DECLARE
    img_id INTEGER;
BEGIN
  INSERT INTO %%BOARD%%_images
    (media_hash, media, preview_op, preview_reply, total)
    SELECT n_row.media_hash, n_row.media_orig, NULL, NULL, 0
    WHERE NOT EXISTS (SELECT 1 FROM %%BOARD%%_images WHERE media_hash = n_row.media_hash);

  IF n_row.op = true THEN
    UPDATE %%BOARD%%_images SET total = (total + 1), preview_op = COALESCE(preview_op, n_row.preview_orig) WHERE media_hash = n_row.media_hash RETURNING media_id INTO img_id;
  ELSE
    UPDATE %%BOARD%%_images SET total = (total + 1), preview_reply = COALESCE(preview_reply, n_row.preview_orig) WHERE media_hash = n_row.media_hash RETURNING media_id INTO img_id;
  END IF;
  RETURN img_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION %%BOARD%%_delete_image(n_media_id integer) RETURNS void AS $$
BEGIN
  UPDATE %%BOARD%%_images SET total = (total - 1) WHERE id = n_media_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION %%BOARD%%_insert_post(n_row "%%BOARD%%") RETURNS void AS $$
DECLARE
  d_day integer;
  d_image integer;
  d_sage integer;
  d_anon integer;
  d_trip integer;
  d_name integer;
BEGIN
  d_day := FLOOR($1.timestamp/86400)*86400;
  d_image := CASE WHEN $1.media_hash IS NOT NULL THEN 1 ELSE 0 END;
  d_sage := CASE WHEN $1.email = 'sage' THEN 1 ELSE 0 END;
  d_anon := CASE WHEN $1.name = 'Anonymous' AND $1.trip IS NULL THEN 1 ELSE 0 END;
  d_trip := CASE WHEN $1.trip IS NOT NULL THEN 1 ELSE 0 END;
  d_name := CASE WHEN COALESCE($1.name <> 'Anonymous' AND $1.trip IS NULL, TRUE) THEN 1 ELSE 0 END;

  INSERT INTO %%BOARD%%_daily
    SELECT d_day, 0, 0, 0, 0, 0, 0
    WHERE NOT EXISTS (SELECT 1 FROM %%BOARD%%_daily WHERE day = d_day);

  UPDATE %%BOARD%%_daily SET posts=posts+1, images=images+d_image,
    sage=sage+d_sage, anons=anons+d_anon, trips=trips+d_trip,
    names=names+d_name WHERE day = d_day;

  IF (SELECT trip FROM %%BOARD%%_users WHERE trip = $1.trip) IS NOT NULL THEN
    UPDATE %%BOARD%%_users SET postcount=postcount+1,
      firstseen = LEAST($1.timestamp, firstseen),
      name = COALESCE($1.name, '')
      WHERE trip = $1.trip;
  ELSE
    INSERT INTO %%BOARD%%_users (name, trip, firstseen, postcount)
      SELECT COALESCE($1.name,''), COALESCE($1.trip,''), $1.timestamp, 0
      WHERE NOT EXISTS (SELECT 1 FROM %%BOARD%%_users WHERE name = COALESCE($1.name,'') AND trip = COALESCE($1.trip,''));

    UPDATE %%BOARD%%_users SET postcount=postcount+1,
      firstseen = LEAST($1.timestamp, firstseen)
      WHERE name = COALESCE($1.name,'') AND trip = COALESCE($1.trip,'');
  END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION %%BOARD%%_delete_post(n_row "%%BOARD%%") RETURNS void AS $$
DECLARE
  d_day integer;
  d_image integer;
  d_sage integer;
  d_anon integer;
  d_trip integer;
  d_name integer;
BEGIN
  d_day := FLOOR($1.timestamp/86400)*86400;
  d_image := CASE WHEN $1.media_hash IS NOT NULL THEN 1 ELSE 0 END;
  d_sage := CASE WHEN $1.email = 'sage' THEN 1 ELSE 0 END;
  d_anon := CASE WHEN $1.name = 'Anonymous' AND $1.trip IS NULL THEN 1 ELSE 0 END;
  d_trip := CASE WHEN $1.trip IS NOT NULL THEN 1 ELSE 0 END;
  d_name := CASE WHEN COALESCE($1.name <> 'Anonymous' AND $1.trip IS NULL, TRUE) THEN 1 ELSE 0 END;

  UPDATE %%BOARD%%_daily SET posts=posts-1, images=images-d_image,
    sage=sage-d_sage, anons=anons-d_anon, trips=trips-d_trip,
    names=names-d_name WHERE day = d_day;

  IF (SELECT trip FROM %%BOARD%%_users WHERE trip = $1.trip) IS NOT NULL THEN
    UPDATE %%BOARD%%_users SET postcount=postcount-1,
      firstseen = LEAST($1.timestamp, firstseen)
      WHERE trip = $1.trip;
  ELSE
    UPDATE %%BOARD%%_users SET postcount=postcount-1,
      firstseen = LEAST($1.timestamp, firstseen)
      WHERE (name = $1.name OR $1.name IS NULL) AND (trip = $1.trip OR $1.trip IS NULL);
  END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION %%BOARD%%_before_insert() RETURNS trigger AS $$
BEGIN
  IF NEW.media_hash IS NOT NULL THEN
    SELECT %%BOARD%%_insert_image(NEW) INTO NEW.media_id;
  END IF;
  RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION %%BOARD%%_after_insert() RETURNS trigger AS $$
BEGIN
  IF NEW.op = true THEN
    PERFORM %%BOARD%%_create_thread(NEW);
  END IF;
  PERFORM %%BOARD%%_update_thread(NEW);
  PERFORM %%BOARD%%_insert_post(NEW);
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION %%BOARD%%_after_del() RETURNS trigger AS $$
BEGIN
  PERFORM %%BOARD%%_update_thread(OLD);
  IF OLD.op = true THEN
    PERFORM %%BOARD%%_delete_thread(OLD.num);
  END IF;
  PERFORM %%BOARD%%_delete_post(OLD);
  IF OLD.media_hash IS NOT NULL THEN
    PERFORM %%BOARD%%_delete_image(OLD.media_id);
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS %%BOARD%%_after_delete ON %%BOARD%%;
CREATE TRIGGER %%BOARD%%_after_delete AFTER DELETE ON %%BOARD%%
  FOR EACH ROW EXECUTE PROCEDURE %%BOARD%%_after_del();

DROP TRIGGER IF EXISTS %%BOARD%%_before_insert ON %%BOARD%%;
CREATE TRIGGER %%BOARD%%_before_insert BEFORE INSERT ON %%BOARD%%
  FOR EACH ROW EXECUTE PROCEDURE %%BOARD%%_before_insert();

DROP TRIGGER IF EXISTS %%BOARD%%_after_insert ON %%BOARD%%;
CREATE TRIGGER %%BOARD%%_after_insert AFTER INSERT ON %%BOARD%%
  FOR EACH ROW EXECUTE PROCEDURE %%BOARD%%_after_insert();
