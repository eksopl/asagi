DROP PROCEDURE IF EXISTS "update_thread_%%BOARD%%";

CREATE PROCEDURE "update_thread_%%BOARD%%" (tnum INT)
BEGIN
  UPDATE
    "%%BOARD%%_threads" op
  SET
    op.time_last = (
      COALESCE(GREATEST(
        op.time_op,
        (SELECT MAX(timestamp) FROM "%%BOARD%%" re FORCE INDEX(thread_num_subnum_index) WHERE
          re.thread_num = tnum AND re.subnum = 0)
      ), op.time_op)
    ),
    op.time_bump = (
      COALESCE(GREATEST(
        op.time_op,
        (SELECT MAX(timestamp) FROM "%%BOARD%%" re FORCE INDEX(thread_num_subnum_index) WHERE
          re.thread_num = tnum AND (re.email <> 'sage' OR re.email IS NULL)
          AND re.subnum = 0)
      ), op.time_op)
    ),
    op.time_ghost = (
      SELECT MAX(timestamp) FROM "%%BOARD%%" re FORCE INDEX(thread_num_subnum_index) WHERE
        re.thread_num = tnum AND re.subnum <> 0
    ),
    op.time_ghost_bump = (
      SELECT MAX(timestamp) FROM "%%BOARD%%" re FORCE INDEX(thread_num_subnum_index) WHERE
        re.thread_num = tnum AND re.subnum <> 0 AND (re.email <> 'sage' OR
          re.email IS NULL)
    ),
    op.time_last_modified = (
      COALESCE(GREATEST(
        op.time_op,
        (SELECT GREATEST(MAX(timestamp), MAX(timestamp_expired)) FROM "%%BOARD%%" re FORCE INDEX(thread_num_subnum_index) WHERE
          re.thread_num = tnum)
      ), op.time_op)
    ),
    op.nreplies = (
      SELECT COUNT(*) FROM "%%BOARD%%" re FORCE INDEX(thread_num_subnum_index) WHERE
        re.thread_num = tnum
    ),
    op.nimages = (
      SELECT COUNT(media_hash) FROM "%%BOARD%%" re FORCE INDEX(thread_num_subnum_index) WHERE
        re.thread_num = tnum
    )
    WHERE op.thread_num = tnum;
END;

DROP PROCEDURE IF EXISTS "create_thread_%%BOARD%%";

CREATE PROCEDURE "create_thread_%%BOARD%%" (num INT, timestamp INT)
BEGIN
  INSERT IGNORE INTO "%%BOARD%%_threads" VALUES (num, timestamp, timestamp,
    timestamp, NULL, NULL, timestamp, 0, 0, 0, 0);
END;

DROP PROCEDURE IF EXISTS "delete_thread_%%BOARD%%";

CREATE PROCEDURE "delete_thread_%%BOARD%%" (tnum INT)
BEGIN
  DELETE FROM "%%BOARD%%_threads" WHERE thread_num = tnum;
END;

DROP PROCEDURE IF EXISTS "insert_image_%%BOARD%%";

CREATE PROCEDURE "insert_image_%%BOARD%%" (n_media_hash VARCHAR(25),
 n_media VARCHAR(20), n_preview VARCHAR(20), n_op INT)
BEGIN
  IF n_op = 1 THEN
    INSERT INTO "%%BOARD%%_images" (media_hash, media, preview_op, total)
    VALUES (n_media_hash, n_media, n_preview, 1)
    ON DUPLICATE KEY UPDATE
      media_id = LAST_INSERT_ID(media_id),
      total = (total + 1),
      preview_op = COALESCE(preview_op, VALUES(preview_op)),
      media = COALESCE(media, VALUES(media));
  ELSE
    INSERT INTO "%%BOARD%%_images" (media_hash, media, preview_reply, total)
    VALUES (n_media_hash, n_media, n_preview, 1)
    ON DUPLICATE KEY UPDATE
      media_id = LAST_INSERT_ID(media_id),
      total = (total + 1),
      preview_reply = COALESCE(preview_reply, VALUES(preview_reply)),
      media = COALESCE(media, VALUES(media));
  END IF;
END;

DROP PROCEDURE IF EXISTS "delete_image_%%BOARD%%";

CREATE PROCEDURE "delete_image_%%BOARD%%" (n_media_id INT)
BEGIN
  UPDATE "%%BOARD%%_images" SET total = (total - 1) WHERE media_id = n_media_id;
END;

DROP PROCEDURE IF EXISTS "insert_post_%%BOARD%%";

CREATE PROCEDURE "insert_post_%%BOARD%%" (p_timestamp INT, p_media_hash VARCHAR(25),
  p_email VARCHAR(100), p_name VARCHAR(100), p_trip VARCHAR(25))
BEGIN
  DECLARE d_day INT;
  DECLARE d_image INT;
  DECLARE d_sage INT;
  DECLARE d_anon INT;
  DECLARE d_trip INT;
  DECLARE d_name INT;

  SET d_day = FLOOR(p_timestamp/86400)*86400;
  SET d_image = p_media_hash IS NOT NULL;
  SET d_sage = COALESCE(p_email = 'sage', 0);
  SET d_anon = COALESCE(p_name = 'Anonymous' AND p_trip IS NULL, 0);
  SET d_trip = p_trip IS NOT NULL;
  SET d_name = COALESCE(p_name <> 'Anonymous' AND p_trip IS NULL, 1);

  INSERT INTO "%%BOARD%%_daily" VALUES(d_day, 1, d_image, d_sage, d_anon, d_trip,
    d_name)
    ON DUPLICATE KEY UPDATE posts=posts+1, images=images+d_image,
    sage=sage+d_sage, anons=anons+d_anon, trips=trips+d_trip,
    names=names+d_name;

  IF (SELECT trip FROM "%%BOARD%%_users" WHERE trip = p_trip) IS NOT NULL THEN
    UPDATE "%%BOARD%%_users" SET postcount=postcount+1,
        firstseen = LEAST(p_timestamp, firstseen),
        name = COALESCE(p_name, '')
      WHERE trip = p_trip;
  ELSE
    INSERT INTO "%%BOARD%%_users" VALUES(
    NULL, COALESCE(p_name,''), COALESCE(p_trip,''), p_timestamp, 1)
    ON DUPLICATE KEY UPDATE postcount=postcount+1,
      firstseen = LEAST(VALUES(firstseen), firstseen),
      name = COALESCE(p_name, '');
  END IF;
END;

DROP PROCEDURE IF EXISTS "delete_post_%%BOARD%%";

CREATE PROCEDURE "delete_post_%%BOARD%%" (p_timestamp INT, p_media_hash VARCHAR(25), p_email VARCHAR(100), p_name VARCHAR(100), p_trip VARCHAR(25))
BEGIN
  DECLARE d_day INT;
  DECLARE d_image INT;
  DECLARE d_sage INT;
  DECLARE d_anon INT;
  DECLARE d_trip INT;
  DECLARE d_name INT;

  SET d_day = FLOOR(p_timestamp/86400)*86400;
  SET d_image = p_media_hash IS NOT NULL;
  SET d_sage = COALESCE(p_email = 'sage', 0);
  SET d_anon = COALESCE(p_name = 'Anonymous' AND p_trip IS NULL, 0);
  SET d_trip = p_trip IS NOT NULL;
  SET d_name = COALESCE(p_name <> 'Anonymous' AND p_trip IS NULL, 1);

  UPDATE "%%BOARD%%_daily" SET posts=posts-1, images=images-d_image,
    sage=sage-d_sage, anons=anons-d_anon, trips=trips-d_trip,
    names=names-d_name WHERE day = d_day;

  IF (SELECT trip FROM "%%BOARD%%_users" WHERE trip = p_trip) IS NOT NULL THEN
    UPDATE "%%BOARD%%_users" SET postcount = postcount-1 WHERE trip = p_trip;
  ELSE
    UPDATE "%%BOARD%%_users" SET postcount = postcount-1 WHERE
      name = COALESCE(p_name, '') AND trip = COALESCE(p_trip, '');
  END IF;
END;

DROP TRIGGER IF EXISTS "before_ins_%%BOARD%%";

CREATE TRIGGER "before_ins_%%BOARD%%" BEFORE INSERT ON "%%BOARD%%"
FOR EACH ROW
BEGIN
  IF NEW.media_hash IS NOT NULL THEN
    CALL insert_image_%%BOARD%%(NEW.media_hash, NEW.media_orig, NEW.preview_orig, NEW.op);
    SET NEW.media_id = LAST_INSERT_ID();
  END IF;
END;

DROP TRIGGER IF EXISTS "after_ins_%%BOARD%%";

CREATE TRIGGER "after_ins_%%BOARD%%" AFTER INSERT ON "%%BOARD%%"
FOR EACH ROW
BEGIN
  IF NEW.op = 1 THEN
    CALL create_thread_%%BOARD%%(NEW.num, NEW.timestamp);
  END IF;
  CALL update_thread_%%BOARD%%(NEW.thread_num);
  CALL insert_post_%%BOARD%%(NEW.timestamp, NEW.media_hash, NEW.email, NEW.name, NEW.trip);
END;

DROP TRIGGER IF EXISTS "after_del_%%BOARD%%";

CREATE TRIGGER "after_del_%%BOARD%%" AFTER DELETE ON "%%BOARD%%"
FOR EACH ROW
BEGIN
  CALL update_thread_%%BOARD%%(OLD.thread_num);
  IF OLD.op = 1 THEN
    CALL delete_thread_%%BOARD%%(OLD.num);
  END IF;
  CALL delete_post_%%BOARD%%(OLD.timestamp, OLD.media_hash, OLD.email, OLD.name, OLD.trip);
  IF OLD.media_hash IS NOT NULL THEN
    CALL delete_image_%%BOARD%%(OLD.media_id);
  END IF;
END;
