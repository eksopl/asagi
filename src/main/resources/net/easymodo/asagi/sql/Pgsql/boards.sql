CREATE TABLE %%BOARD%%_threads (
  parent integer NOT NULL,
  time_op integer NOT NULL,
  time_last integer NOT NULL,
  time_bump integer NOT NULL,
  time_ghost integer DEFAULT NULL,
  time_ghost_bump integer DEFAULT NULL,
  nreplies integer NOT NULL DEFAULT '0',
  nimages integer NOT NULL DEFAULT '0',
  PRIMARY KEY (parent)
);

CREATE INDEX %%BOARD%%_threads_time_op_index on %%BOARD%%_threads (time_op);
CREATE INDEX %%BOARD%%_threads_time_bump_index on %%BOARD%%_threads (time_bump);					
CREATE INDEX %%BOARD%%_threads_time_ghost_bump_index on %%BOARD%%_threads (time_ghost_bump);

CREATE TABLE %%BOARD%%_users (
  name character varying(100) NOT NULL DEFAULT '',
  trip character varying(25) NOT NULL DEFAULT '',
  firstseen integer NOT NULL,
  postcount integer NOT NULL,
  
  PRIMARY KEY (name, trip)
);

CREATE INDEX %%BOARD%%_users_firstseen_index on %%BOARD%%_users (firstseen);
CREATE INDEX %%BOARD%%_users_postcount_index on %%BOARD%%_users (postcount);

CREATE TABLE %%BOARD%%_images (
  id SERIAL NOT NULL,
  media_hash character varying(25) NOT NULL,
  media_filename character varying(20),
  preview_op character varying(20),
  preview_reply character varying(20),
  total integer NOT NULL DEFAULT '0',
  banned smallint NOT NULL DEFAULT '0',
  
  PRIMARY KEY (id),
  UNIQUE (media_hash)
);

CREATE INDEX %%BOARD%%_images_total_index on %%BOARD%%_images (total);

CREATE TABLE %%BOARD%%_daily (
  day integer NOT NULL,
  posts integer NOT NULL,
  images integer NOT NULL,
  sage integer NOT NULL,
  anons integer NOT NULL,
  trips integer NOT NULL,
  names integer NOT NULL,
  
  PRIMARY KEY (day)
);

CREATE TABLE %%BOARD%% (
  doc_id SERIAL NOT NULL,
  id numeric(39,0) DEFAULT 0 NOT NULL,
  num integer NOT NULL,
  subnum integer NOT NULL,
  parent integer,
  "timestamp" integer,
  preview character varying(20),
  preview_w integer DEFAULT 0 NOT NULL,
  preview_h integer DEFAULT 0 NOT NULL,
  media text,
  media_w integer DEFAULT 0 NOT NULL,
  media_h integer DEFAULT 0 NOT NULL,
  media_size integer DEFAULT 0 NOT NULL,
  media_hash character varying(25),
  media_filename character varying(20),
  spoiler boolean DEFAULT false NOT NULL,
  deleted boolean DEFAULT false NOT NULL,
  capcode character(1) DEFAULT 'N' NOT NULL CHECK (capcode = ANY (ARRAY['N', 'M', 'A', 'G'])),
  email character varying(100),
  name character varying(100),
  trip character varying(25),
  title character varying(100),
  comment text,
  delpass text,
  sticky boolean DEFAULT false NOT NULL,
  media_id integer,
    
  PRIMARY KEY (doc_id),
  FOREIGN KEY (media_id) REFERENCES %%BOARD%%_images(id),
  UNIQUE (num, subnum)
);

CREATE INDEX %%BOARD%%_id_index on %%BOARD%% (id);
CREATE INDEX %%BOARD%%_num_index on %%BOARD%% (num);
CREATE INDEX %%BOARD%%_subnum_index on %%BOARD%% (subnum);
CREATE INDEX %%BOARD%%_parent_index on %%BOARD%% (parent);
CREATE INDEX %%BOARD%%_timestamp_index on %%BOARD%% (timestamp);
CREATE INDEX %%BOARD%%_media_hash_index on %%BOARD%% USING hash (media_hash) ;
CREATE INDEX %%BOARD%%_email_index on %%BOARD%% (email);
CREATE INDEX %%BOARD%%_name_index on %%BOARD%% (name);
CREATE INDEX %%BOARD%%_trip_index on %%BOARD%% (trip);
CREATE INDEX %%BOARD%%_fullname_index on %%BOARD%% (name,trip);