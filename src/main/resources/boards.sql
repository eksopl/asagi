CREATE TABLE IF NOT EXISTS %%BOARD%% ( 
  doc_id int unsigned NOT NULL auto_increment, 
  media_id int unsigned NOT NULL DEFAULT '0', 
  id decimal(39,0) unsigned NOT NULL DEFAULT '0', 
  num int unsigned NOT NULL, 
  subnum int unsigned NOT NULL, 
  parent int unsigned NOT NULL DEFAULT '0', 
  timestamp int unsigned NOT NULL, 
  preview varchar(20), 
  preview_w smallint unsigned NOT NULL DEFAULT '0', 
  preview_h smallint unsigned NOT NULL DEFAULT '0', 
  media text, 
  media_w smallint unsigned NOT NULL DEFAULT '0', 
  media_h smallint unsigned NOT NULL DEFAULT '0', 
  media_size int unsigned NOT NULL DEFAULT '0', 
  media_hash varchar(25), 
  media_filename varchar(20), 
  spoiler bool NOT NULL DEFAULT '0', 
  deleted bool NOT NULL DEFAULT '0', 
  capcode enum('N', 'M', 'A', 'G') NOT NULL DEFAULT 'N', 
  email varchar(100), 
  name varchar(100), 
  trip varchar(25), 
  title varchar(100), 
  comment text,
  delpass tinytext, 
  sticky bool NOT NULL DEFAULT '0', 
  
  PRIMARY KEY (doc_id), 
  UNIQUE num_subnum_index (num, subnum), 
  INDEX id_index(id), 
  INDEX num_index(num), 
  INDEX subnum_index(subnum),
  INDEX parent_index(parent),
  INDEX timestamp_index(TIMESTAMP),
  INDEX media_hash_index(media_hash),
  INDEX email_index(email),
  INDEX name_index(name),
  INDEX trip_index(trip),
  INDEX fullname_index(name,trip),
  fulltext INDEX comment_index(COMMENT)
) engine=MyISAM CHARSET=%%CHARSET%%;

CREATE TABLE IF NOT EXISTS `%%BOARD%%_threads` (
  `parent` int unsigned NOT NULL,
  `time_op` int unsigned NOT NULL,
  `time_last` int unsigned NOT NULL,
  `time_bump` int unsigned NOT NULL,
  `time_ghost` int unsigned DEFAULT NULL,
  `time_ghost_bump` int unsigned DEFAULT NULL,
  `nreplies` int unsigned NOT NULL DEFAULT '0',
  `nimages` int unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`parent`),
  
  INDEX time_op_index (time_op),
  INDEX time_bump_index (time_bump),					
  INDEX time_ghost_bump_index (time_ghost_bump),
) ENGINE=InnoDB CHARSET=%%CHARSET%%;

CREATE TABLE IF NOT EXISTS `%%BOARD%%_users` (
  `name` varchar(100) NOT NULL DEFAULT '',
  `trip` varchar(25) NOT NULL DEFAULT '',
  `firstseen` int(11) NOT NULL,
  `postcount` int(11) NOT NULL,
  PRIMARY KEY (`name`, `trip`),
  
  INDEX firstseen_index (firstseen),
  INDEX postcount_index (postcount)
) ENGINE=InnoDB DEFAULT CHARSET=%%CHARSET%%;

CREATE TABLE IF NOT EXISTS `%%BOARD%%_images` (
  `id` int unsigned NOT NULL auto_increment,
  `media_hash` varchar(25) NOT NULL,
  `media_filename` varchar(20),
  `preview_op` varchar(20),
  `preview_reply` varchar(20),
  `total` int(10) unsigned NOT NULL DEFAULT '0',
  `banned` smallint unsigned NOT NULL DEFAULT '0', 
  PRIMARY KEY (`id`),
  UNIQUE media_hash_index (`media_hash`),
  INDEX total_index (total)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `%%BOARD%%_daily` (
  `day` int(10) unsigned NOT NULL,
  `posts` int(10) unsigned NOT NULL,
  `images` int(10) unsigned NOT NULL,
  `sage` int(10) unsigned NOT NULL,
  `anons` int(10) unsigned NOT NULL,
  `trips` int(10) unsigned NOT NULL,
  `names` int(10) unsigned NOT NULL,
  PRIMARY KEY (`day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;