DROP PROCEDURE IF EXISTS `update_thread_%%BOARD%%`;

CREATE PROCEDURE `update_thread_%%BOARD%%` (tnum INT)
BEGIN
  UPDATE
    `%%BOARD%%_threads` op
  SET
    op.time_last = (
      COALESCE(GREATEST(
        op.time_op,
        (SELECT MAX(timestamp) FROM `%%BOARD%%` re FORCE INDEX(parent_index) WHERE
          re.parent = tnum AND re.subnum = 0)
        ), op.time_op)
      ),
      op.time_bump = (
        COALESCE(GREATEST(
          op.time_op,
          (SELECT MAX(timestamp) FROM `%%BOARD%%` re FORCE INDEX(parent_index) WHERE
            re.parent = tnum AND (re.email <> 'sage' OR re.email IS NULL)
            AND re.subnum = 0)
        ), op.time_op)
      ),
      op.time_ghost = (
        SELECT MAX(timestamp) FROM `%%BOARD%%` re FORCE INDEX(parent_index) WHERE
          re.parent = tnum AND re.subnum <> 0
      ),
      op.time_ghost_bump = (
        SELECT MAX(timestamp) FROM `%%BOARD%%` re FORCE INDEX(parent_index) WHERE
          re.parent = tnum AND re.subnum <> 0 AND (re.email <> 'sage' OR
            re.email IS NULL)
      ),
      op.nreplies = (
        SELECT COUNT(*) FROM `%%BOARD%%` re FORCE INDEX(parent_index) WHERE
          re.parent = tnum
      ),
      op.nimages = (
        SELECT COUNT(media_hash) FROM `%%BOARD%%` re FORCE INDEX(parent_index) WHERE
          re.parent = tnum
      )
    WHERE op.parent = tnum;
END;

DROP PROCEDURE IF EXISTS `create_thread_%%BOARD%%`;

CREATE PROCEDURE `create_thread_%%BOARD%%` (doc_id INT, num INT, timestamp INT)
BEGIN
  INSERT IGNORE INTO `%%BOARD%%_threads` VALUES (doc_id, num, timestamp, timestamp,
    timestamp, NULL, NULL, 0, 0);
END;

DROP PROCEDURE IF EXISTS `delete_thread_%%BOARD%%`;

CREATE PROCEDURE `delete_thread_%%BOARD%%` (tnum INT)
BEGIN
  DELETE FROM `%%BOARD%%_threads` WHERE parent = tnum;
END;

DROP PROCEDURE IF EXISTS `insert_image_%%BOARD%%`;

CREATE PROCEDURE `insert_image_%%BOARD%%` (n_media_hash VARCHAR(25), n_num INT,
  n_subnum INT, n_parent INT, n_preview VARCHAR(20))
BEGIN
  DECLARE o_parent INT;

  -- This should be a transaction, but MySquirrel doesn't support transactions
  -- inside triggers or stored procedures (stay classy, MySQL)
  SELECT parent INTO o_parent FROM `%%BOARD%%_images` WHERE media_hash = n_media_hash;
  IF o_parent IS NULL THEN
    INSERT INTO `%%BOARD%%_images` VALUES (n_media_hash, n_num, n_subnum, n_parent,
      n_preview, 1);
  ELSEIF o_parent <> 0 AND n_parent = 0 THEN
    UPDATE `%%BOARD%%_images` SET num = n_num, subnum = n_subnum, parent = n_parent,
      preview = n_preview, total = (total + 1)
      WHERE media_hash = n_media_hash;
  ELSE
    UPDATE `%%BOARD%%_images` SET total = (total + 1) WHERE
      media_hash = n_media_hash;
  END IF;
END;

DROP PROCEDURE IF EXISTS `delete_image_%%BOARD%%`;

CREATE PROCEDURE `delete_image_%%BOARD%%` (n_media_hash VARCHAR(25))
BEGIN
  UPDATE `%%BOARD%%_images` SET total = (total - 1) WHERE media_hash = n_media_hash;
END;

DROP PROCEDURE IF EXISTS `insert_post_%%BOARD%%`;

CREATE PROCEDURE `insert_post_%%BOARD%%` (p_timestamp INT, p_media_hash VARCHAR(25),
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

  INSERT INTO %%BOARD%%_daily VALUES(d_day, 1, d_image, d_sage, d_anon, d_trip,
    d_name)
    ON DUPLICATE KEY UPDATE posts=posts+1, images=images+d_image,
    sage=sage+d_sage, anons=anons+d_anon, trips=trips+d_trip,
    names=names+d_name;

  -- Also should be a transaction. Lol MySQL.  
  IF (SELECT trip FROM a_users WHERE trip = p_trip) IS NOT NULL THEN
    UPDATE %%BOARD%%_users SET postcount=postcount+1,
      firstseen = LEAST(p_timestamp, firstseen)
      WHERE trip = p_trip;
  ELSE
    INSERT INTO %%BOARD%%_users VALUES(COALESCE(p_name,''), COALESCE(p_trip,''), p_timestamp, 1)
    ON DUPLICATE KEY UPDATE postcount=postcount+1,
    firstseen = LEAST(VALUES(firstseen), firstseen);
  END IF;
END;

DROP PROCEDURE IF EXISTS `delete_post_%%BOARD%%`;

CREATE PROCEDURE `delete_post_%%BOARD%%` (p_timestamp INT, p_media_hash VARCHAR(25), p_email VARCHAR(100), p_name VARCHAR(100), p_trip VARCHAR(25))
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

  UPDATE a_daily SET posts=posts-1, images=images-d_image,
    sage=sage-d_sage, anons=anons-d_anon, trips=trips-d_trip,
    names=names-d_name WHERE day = d_day;

  -- Also should be a transaction. Lol MySQL.
  IF (SELECT trip FROM %%BOARD%%_users WHERE trip = p_trip) IS NOT NULL THEN
    UPDATE %%BOARD%%_users SET postcount = postcount-1 WHERE trip = p_trip;
  ELSE
    UPDATE %%BOARD%%_users SET postcount = postcount-1 WHERE
      name = COALESCE(p_name, '') AND trip = COALESCE(p_trip, '');
  END IF;
END;

DROP TRIGGER IF EXISTS `after_ins_%%BOARD%%`;

CREATE TRIGGER `after_ins_%%BOARD%%` AFTER INSERT ON `%%BOARD%%`
FOR EACH ROW
BEGIN
  IF NEW.parent = 0 THEN
    CALL create_thread_%%BOARD%%(NEW.doc_id, NEW.num, NEW.timestamp);
  END IF;
  CALL update_thread_%%BOARD%%(NEW.parent);
  CALL insert_post_%%BOARD%%(NEW.timestamp, NEW.media_hash, NEW.email, NEW.name,
    NEW.trip);
  IF NEW.media_hash IS NOT NULL THEN
    CALL insert_image_%%BOARD%%(NEW.media_hash, NEW.num, NEW.subnum, NEW.parent,
      NEW.preview);
  END IF;
END;

DROP TRIGGER IF EXISTS `after_del_%%BOARD%%`;

CREATE TRIGGER `after_del_%%BOARD%%` AFTER DELETE ON `%%BOARD%%`
FOR EACH ROW
BEGIN
  CALL update_thread_%%BOARD%%(OLD.parent);
  IF OLD.parent = 0 THEN
    CALL delete_thread_%%BOARD%%(OLD.num);
  END IF;
  CALL delete_post_%%BOARD%%(OLD.timestamp, OLD.media_hash, OLD.email, OLD.name,
    OLD.trip);
  IF OLD.media_hash IS NOT NULL THEN
    CALL delete_image_%%BOARD%%(OLD.media_hash);
  END IF;
END;