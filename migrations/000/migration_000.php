<?php

// only Command Line access allowed!
if(PHP_SAPI != 'cli')
{
	die('This file must be accessed via command line.');
}
 
// we definitely want to see all errors. multiple of these because PHP is retarded.
error_reporting(E_ALL);
ini_set('error_reporting', E_ALL);
ini_set('display_errors', 1);

// give us at least PHP 5.2 please
if(version_compare(PHP_VERSION, '5.2.0') < 0)
{
	die('This script is compatible only with PHP 5.2 or higher.'.PHP_EOL);
}


// CONSTANTS
define('BOARD_COLUMNS', 'doc_id, 0, id, num, subnum, parent, timestamp, preview, preview_w, preview_h, media, media_w, 
  	media_h, media_size, media_hash, media_filename AS orig_filename, spoiler, deleted, capcode, email, name, trip, title,
  	comment, delpass, sticky');

// load class that manages CLI parameters
include 'resources/parameters.php';

// these are only the necessary args, we don't really need any
$arg_array = array(
//	array('--help', 'SIMPLE'),
//	array('--board', 'FULL'),
//	array('--phase', 'FULL')
//	array('--with-downtime', 'FULL')
);

$args = new arg_parser($arg_array);

echo '
Migration from Fuuka fetcher to Asagi fetcher - 000
===================================================
';

// lousy help
if($args->passed('--help'))
{
	die('
This script updates your database and images folder to be compatible with the Asagi fetcher.
Make absolutely sure you\'ve read the manual you can find in the folder of this script.
	
Options:
--board <board>        Process the specified board only.
                       If this argument is not set, all boards will be processed sequentially.
--phase <num>          Run the phase specified only. [1] To InnoDB [2] Alter tables [3] Copy images [4] Clean up.
                       If this argument is not set, all phases will be run sequentially, on all selected boards.
--with-downtime        Do not minimize the downtime of migration to InnoDB with _temp tables.
--full-images          If you have full images stored, this will move them as well.
--move-images          Move the thumbnails instead of copying them. Can cause image loss if you stop or crash during the third phase.
--move-full-images     As above, but for full images.
--ignore-db-errors     Ignore the database errors. It shouldn\'t be necessary.
' . PHP_EOL);
}
	
$disable_db_errors = $args->passed('--ignore-db-errors') == TRUE;

$move_images = $args->passed('--move-images') == TRUE;


$json_path = '../../asagi.json';
if(!file_exists($json_path))
{
	die('Couldn\'t find asagi.json'.PHP_EOL);
}

$json = @json_decode(file_get_contents($json_path));
if(!is_object($json))
{
	die('asagi.json doesn\'t appear to contain valid JSON.'.PHP_EOL);
}

if(!isset($json->settings) || !isset($json->settings->default))
{
	die('Your asagi.json isn\'t properly structured.'.PHP_EOL);
}

// we don't want to deal with that nesting
$json = $json->settings;

// put the default somewhere else and $json will be only the boards
$defaults = $json->default;
unset($json->default);

// rock and roll
foreach($json as $shortname => $board)
{
	// to select the board just continue if it's not the right one
	if($args->passed('--board'))
	{
		if($shortname != $args->get_full_passed('--board'))
			continue;
	}

	
	// merge the values if the $board overwrites any $default settings
	$hostname = isset($board->hostname)?$board->host:$defaults->host;
	$username = isset($board->username)?$board->username:$defaults->username;
	$password = isset($board->password)?$board->password:$defaults->password;
	$database = isset($board->database)?$board->database:$defaults->database;
	$path = isset($board->path)?$board->path:$defaults->path;
	
	// we don't want the slash at the end of the path
	$path = rtrim($path, '/');
	
	// mysqli is awesome
	$db = new mysqli($hostname, $username, $password, $database);
	if ($db->connect_error) die($db->connect_error . PHP_EOL);

    // check utf8mb4 availability	
	$utf8mb4_available = $db->query("SHOW CHARACTER SET WHERE Charset = 'utf8mb4';");
	if($utf8mb4_available->num_rows)
	{
		$charset = 'utf8mb4';
		echo 'Detected MySQL server compatible with 4-byte characters, using utf8mb4 charset'.PHP_EOL;
	}
	else
	{
		$charset = 'utf8';
		echo 'Detected MySQL server not compatible with 4-byte characters, using utf8 charset.'.PHP_EOL;
		echo 'If you want to use utf8mb4, you must upgrade to MySQL 5.5+.'.PHP_EOL;
	}

	$db->set_charset($charset);
	
	
	/*
	|	PHASE 1: converting to InnoDB
	*/
	
	if(!$args->passed('--phase') || $args->get_full_passed('--phase') == 1)
	{
		// check if the table still needs conversion
		$table_status = $db->query('SHOW TABLE STATUS WHERE Name = \''. $db->real_escape_string($shortname) . '\';');
		if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
		while ($record = $table_status->fetch_object())
		{
			if(strtolower($record->Engine) != 'innodb')
			{
				// if we're here the table is still MyISAM
				
				if($args->passed('--with-downtime'))
				{
					// use the live table
					$myisam_table = $shortname;
					$myisam_table_temp = FALSE;
				}
				else
				{
					// check if the admin already did the clone necessary
					$temp_status = $db->query('SHOW TABLE STATUS WHERE Name = \''. $db->real_escape_string($shortname . '_temp') . '\';');
					if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
					if($temp_status->row_count != 1)
					{
						echo '-----------'. PHP_EOL;
						echo 'To avoid extended periods of downtime, the script needs you to manually copy the database file.' . PHP_EOL;
						echo 'Read the readme to know how to complete this passage.'.PHP_EOL;
						die('If you don\'t care about downtime, start the migration with the --with-downtime argument.' . PHP_EOL);
					}
					
					mysqli_free_result($temp_status);
					
					// if we found it, use the temp table
					$myisam_table = $shortname . '_temp';
					$myisam_table_temp = TRUE;
				}
				
				// we need to know where the query ends
				$max_doc_id_res = $db->query('SELECT MAX(doc_id) as max FROM `' . $myisam_table . '`;');
				if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
				while ($row = $max_doc_id_res->fetch_object())
				{
					$max_doc_id = $row->max;
				}
				mysqli_free_result($max_doc_id_res);
				
				// check if the _new table already exists and get the MAX(doc_id)
				$new_status = $db->query('SHOW TABLE STATUS WHERE Name = \''. $db->real_escape_string($shortname . '_new') . '\';');
				if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
				if($new_status->num_rows == 1)
				{
					$new_max_doc_id_res = $db->query('SELECT MAX(doc_id) as max FROM `' . $shortname . '_new`;');
					if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
					while ($row = $new_max_doc_id_res->fetch_object())
					{
						$new_max_doc_id = $row->max;
					}
					mysqli_free_result($new_max_doc_id_res);
					
					// check at which point of the 2000000s the job arrived, and make it redo
					// the entire last chunk not to risk missing entries
					$min_row = floor($new_max_doc_id/2000000)*2000000;
					echo 'Restarting migration to InnoDB for ' . $shortname . ' from row ' . $min_row . '.' . PHP_EOL;
				}
				else
				{
					// create a _new table
					echo 'Creating temporary table ' . $shortname . '_new.' . PHP_EOL;
					$board_sql = file_get_contents('resources/boards.sql');
					$board_sql = str_replace('%%BOARD%%', $shortname . '_new', $board_sql);
					$board_sql = str_replace('%%CHARSET%%', $charset, $board_sql);
					$db->query($board_sql);
					if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
					
					// we're starting from zero
					$min_row = 0;
				}
								
				// don't bother with loops if less than 2m entries
				if($max_doc_id <= 2000000)
				{
					echo 'Inserting `' . $shortname . '` in the new table.'.PHP_EOL;
					$db->query('INSERT IGNORE INTO `' . $shortname . '_new` SELECT ' . BOARD_COLUMNS . ' FROM `' . $myisam_table . '`;');
					if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
				}
				else
				{
					
					while($min_row + 2000000 < $max_doc_id)
					{
						echo 'Inserting rows from `' . $shortname . '` from ' . $min_row . ' to ' . ($min_row + 2000000) . '.'.PHP_EOL;
						$db->query('
							INSERT IGNORE INTO `' . $shortname . '_new` 
								SELECT ' . BOARD_COLUMNS . ' FROM `' . $myisam_table . '` 
								WHERE doc_id >= ' . $min_row . ' AND doc_id < ' . ($min_row + 2000000) . ';
						');
						if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
						$min_row += 2000000;
					}
					
					// one last chunk until this table ends
					// -10000 in case of temporary table because we want to grab the latest updated from live
					echo 'Inserting rows from `' . $shortname . '` from ' . $min_row . ' to ' . $max_doc_id . '.'.PHP_EOL;
					$db->query('
						INSERT IGNORE INTO `' . $shortname . '_new` 
							SELECT ' . BOARD_COLUMNS . ' FROM `' . $myisam_table . '` 
							WHERE doc_id >= ' . ($min_row) . ' 
								AND doc_id <= ' . ($max_doc_id - ($myisam_table_temp)?10000:0) . ' ;
					');
					if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
				}
				
				// if we used a temporary table, we need to get up to date with the live board
				// -10000 because we're grabbing the updated latest posts
				if($myisam_table_temp === TRUE)
				{
					echo 'Updating the rows from the live board.'.PHP_EOL;
					$db->query('
						INSERT IGNORE INTO `' . $shortname . '_new` 
							SELECT ' . BOARD_COLUMNS . ' FROM `' . $shortname . '` 
							WHERE doc_id >= ' . ($max_doc_id - 10000) . ' ;
					');
					if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
				}
				
				echo 'Rotating the new database for `' . $shortname . '` with the old one.'.PHP_EOL;
				$db->query('RENAME TABLE `' . $shortname . '` to `' . $shortname . '_old`;');
				$db->query('RENAME TABLE `' . $shortname . '_new` to `' . $shortname . '`;');
				echo 'The database for ' . $shortname . ' has been converted to InnoDB successfully.'.PHP_EOL;
				
			}
		}
		
		mysqli_free_result($table_status);
		
		echo 'Phase 1, in board `' . $shortname . '`: done.'. PHP_EOL;
		echo "\x07If running, disable the Fuuka fetcher! Right now it can't insert anything in the database.".PHP_EOL;
		echo 'The second phase will adapt the database to work with Asagi. After that, you will be able to run Asagi.' . PHP_EOL;
	}
	
	/*
	| PHASE 2: (partial) database alteration
	*/
	
	if(!$args->passed('--phase') || $args->get_full_passed('--phase') == 2)
	{
		echo 'Running phase two: adapting the database to Asagi\'s definitions.' . PHP_EOL;
		
		// what if the board was already InnoDB but doesn't have the extra media_id row?
		// just try it and ignore the error!
		echo 'Testing if `' . $shortname . '` table needs for alterations. If it does, this will take a bit.' . PHP_EOL;
		$db->query('
			ALTER TABLE `' . $shortname . '` 
			ADD COLUMN media_id int unsigned NOT NULL DEFAULT \'0\' AFTER doc_id,
			ADD INDEX media_id_index(media_id),
			CHANGE media_filename orig_filename varchar(20),
			ADD INDEX orig_filename_index(orig_filename)
		');
		if ($db->error) echo 'No alteration necessary.' . PHP_EOL;
		// make sure it happened
		$db->query('ALTER TABLE `' . $shortname . '` ADD COLUMN media_id int unsigned NOT NULL DEFAULT \'0\' AFTER doc_id');
		$db->query('ALTER TABLE `' . $shortname . '` ADD INDEX media_id_index(media_id)');
		$db->query('ALTER TABLE `' . $shortname . '` CHANGE media_filename orig_filename varchar(20)');
		$db->query('ALTER TABLE `' . $shortname . '` ADD INDEX orig_filename_index(orig_filename)');
	
		// this shouldn't be a problem considering that it only affects statistics for the time being
		echo 'Altering `' . $shortname . '_images` table.' . PHP_EOL;
		$db->query('
			ALTER TABLE '.$shortname.'_images 
			DROP PRIMARY KEY,
			ADD COLUMN media_id int unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
			ADD media_filename varchar(20) AFTER media_hash,
			ADD preview_op varchar(20) AFTER media_filename, 
			ADD preview_reply varchar(20) AFTER preview_op,
			ADD banned smallint unsigned NOT NULL DEFAULT \'0\' AFTER total,
			ADD UNIQUE INDEX media_hash_index(media_hash),
			DROP COLUMN num,
			DROP COLUMN parent,
			DROP COLUMN subnum,
			DROP COLUMN preview
		');
		// make sure everything is done
		$db->query('ALTER TABLE '.$shortname.'_images ADD COLUMN media_id int unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST');
		if ($db->error && strpos($db->error, 'it must be defined as a key') !== FALSE)
		{
			// we might still have the primary key on media_hash
			$db->query('ALTER TABLE '.$shortname.'_images DROP PRIMARY KEY');
			$db->query('ALTER TABLE '.$shortname.'_images ADD COLUMN media_id int unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST');
		}		
		$db->query('ALTER TABLE '.$shortname.'_images ADD media_filename varchar(20) AFTER media_hash');
		$db->query('ALTER TABLE '.$shortname.'_images ADD preview_op varchar(20) AFTER media_filename');
		$db->query('ALTER TABLE '.$shortname.'_images ADD preview_reply varchar(20) AFTER preview_op');
		$db->query('ALTER TABLE '.$shortname.'_images ADD banned smallint unsigned NOT NULL DEFAULT \'0\' AFTER total');
		$db->query('ALTER TABLE '.$shortname.'_images ADD UNIQUE INDEX media_hash_index(media_hash)');
		$db->query('ALTER TABLE '.$shortname.'_images DROP COLUMN num');
		$db->query('ALTER TABLE '.$shortname.'_images DROP COLUMN parent');
		$db->query('ALTER TABLE '.$shortname.'_images DROP COLUMN subnum');
		$db->query('ALTER TABLE '.$shortname.'_images DROP COLUMN preview');
		
		//if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);

		// this is actually going to lock everything
		// takes just 1-2 minutes seemingly
		$db->query('
			ALTER TABLE '.$shortname.'_threads 
			DROP COLUMN doc_id_p,
			DROP INDEX parent_index,
			ADD PRIMARY KEY (parent),			
			ADD INDEX time_op_index(time_op),
			ADD INDEX time_bump_index(time_bump)
		');			
		// make sure it's all done even if the user blocked the system
		$db->query('ALTER TABLE '.$shortname.'_threads DROP COLUMN doc_id_p');
		$db->query('ALTER TABLE '.$shortname.'_threads DROP INDEX parent_index');
		$db->query('ALTER TABLE '.$shortname.'_threads ADD PRIMARY KEY (parent)');
		$db->query('ALTER TABLE '.$shortname.'_threads ADD INDEX time_op_index(time_op)');
		$db->query('ALTER TABLE '.$shortname.'_threads ADD INDEX time_bump_index(time_bump)');
		//if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
		
		// at this point we're set to update the triggers
		$triggers_sql = file_get_contents('resources/triggers.sql');
		$triggers_sql = str_replace('%%BOARD%%', $shortname, $triggers_sql);
		$triggers_sql_array = explode('//', $triggers_sql);
		foreach($triggers_sql_array as $trigger)
		{	if($trigger)
				$db->query($trigger);
			if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
		}
		
		// at this point media_ids are also being filled correctly, so let's update the entire table
		// it could lock the table for 20 minutes for boards with 50m+ posts 
		echo 'Assigning the media_id to each post.' . PHP_EOL;
		//$db->query('UPDATE `' . $shortname . '` AS board SET media_id = (SELECT media_id FROM ' . $shortname . '_images WHERE media_hash = board.media_hash) WHERE board.media_hash IS NOT NULL;');
		if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
		
		
		
		// alter the users table
		echo 'Altering the ' . $shortname . '_users table.' . PHP_EOL;
		$db->query('
		    ALTER TABLE `' . $shortname . '_users`
		    DROP PRIMARY INDEX,
		    ADD COLUMN user_id int unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
            ADD UNIQUE INDEX name_trip_index(name, trip)
        ');
        
        /*
        // make sure everything is done
        $db->query('ALTER TABLE '.$shortname.'_threads ADD COLUMN user_id int unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST');
        if ($db->error && strpos($db->error, 'it must be defined as a key') !== FALSE)
        {
            $db->query('ALTER TABLE '.$shortname.'_threads DROP PRIMARY INDEX');
            $db->query('ALTER TABLE '.$shortname.'_threads ADD COLUMN user_id int unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST');
        }
        $db->query('ALTER TABLE '.$shortname.'_threads ADD UNIQUE INDEX name_trip_index(name, trip)');
        
        // update with latest names to match new updating pattern
        echo 'Updating the ' . $shortname . '_users table to show the latest usernames.' . PHP_EOL;
        $db->query('
            UPDATE IGNORE `' . $shortname . '_users` SET name = (
                SELECT COALESCE(gg.name, \'\') FROM (
                    SELECT timestamp`' . $shortname . '` 
                    WHERE `' . $shortname . '`.trip = `' . $shortname . '_users`.trip 
                    ORDER BY `' . $shortname . '`.timestamp DESC 
                    LIMIT 1
                ) AS gg
            ) 
            WHERE trip <> \'\'
        ');
		*/
		// This allows to run Asagi before starting phase 3
		// rename the images folder, and if your site supports the _old table you will still have visible images
		$old_path = $path . '/' . $shortname . '_old';
		$new_path = $path . '/' . $shortname;
		
		// there is a bit of confusion on what is old and new in the next two IFs, mind that.
		if(!file_exists($new_path))
		{
			die('The images path you specified doesn\'t exist.' . PHP_EOL);
		}
		if(!file_exists($old_path))
		{
			echo 'Changing the images\' folder ' . $shortname . ' to ' . $shortname . '_old.' . PHP_EOL;
			// move the live path to _old
			rename($new_path, $old_path);
			mkdir($new_path);
		}
		
		echo 'Phase 2, in board `' . $shortname . '`: done.'. PHP_EOL;
		echo "\x07You can now run Asagi.". PHP_EOL;
		echo 'The third phase will copy over the images. Until it\'s completed, you will see missing images.' . PHP_EOL;
	}	
	
	/*
	| PHASE 3: making a list of files to move over
	*/
	
	if(!$args->passed('--phase') || $args->get_full_passed('--phase') == 3)
	{
		$internal_board = $args->passed('--internal-board') == TRUE;
		$full_images = $args->passed('--full-images') == TRUE;
		$move_full_images = $args->passed('--move-full-images') == TRUE;
	
		// superpower this system by using a TEMPORARY TABLE
		$db->query('
			CREATE TEMPORARY TABLE ' . $shortname .  '_images_tmp (
				media_id int unsigned NOT NULL DEFAULT \'0\',
				preview_op varchar(20),
				preview_reply varchar(20),
				media_filename varchar(20),
				
				PRIMARY KEY (media_id)
			)
		');
		if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);

		// let's save the initial time so we can show how much is it taking, and statistics
		$init_time = time();
		$images_processed = 0;
	
		// rename the images folder, and if your site supports the _old table you will still have visible images
		$old_path = $path . '/' . $shortname . '_old';
		$new_path = $path . '/' . $shortname;
		
		// there is a bit of confusion on what is old and new in the next two IFs, mind that.
		if(!file_exists($new_path))
		{
			die('The images path you specified doesn\'t exist.' . PHP_EOL);
		}
		if(!file_exists($old_path))
		{
			// move the live path to _old
			rename($new_path, $old_path);
			mkdir($new_path);
		}

		// fetch the last position before stopping
		if(file_exists($shortname . 'image_process.txt'))
		{
			$offset = intval(file_get_contents($shortname . '_image_process.txt'));
		}
		else
		{
			// we need to know where the query ends
			$max_doc_id_res = $db->query('SELECT MAX(doc_id) as max FROM `' . $shortname . '`;');
			if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
			while ($row = $max_doc_id_res->fetch_object())
			{
				$max_doc_id = $row->max;
			}
			mysqli_free_result($max_doc_id_res);
			
			$offset = $max_doc_id;
		}
				
		while($offset > 0)
		{
			// savegame
			file_put_contents($shortname .'_image_process.txt', $offset);
			
			$images_res = $db->query('
				SELECT brd.media_id, num, parent, brd.preview
				FROM `' . $shortname . '` AS brd
				JOIN `' . $shortname . '_images` AS img
				ON brd.media_id = img.media_id
				WHERE doc_id < ' . $offset . ' AND doc_id >= ' . ($offset - 100000) . '
					AND brd.media_hash IS NOT NULL 
					AND (IF(brd.parent = 0, img.preview_op, img.preview_reply) IS NULL
						' . ((!$full_images)?:'OR img.media_filename IS NULL') . ' )
			');
			if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
			
			while($row = $images_res->fetch_object())
			{
				if(!$internal_board)
				{
					preg_match('/(\d+?)(\d{2})\d{0,3}$/', ($row->parent == 0)?$row->num:$row->parent, $subpath);
					for ($index = 1; $index <= 2; $index++)
					{
						if (!isset($subpath[$index]))
						$subpath[$index] = '';
					}
	
					$old_image_path_inner = str_pad($subpath[1], 4, "0", STR_PAD_LEFT) . str_pad($subpath[2], 2, "0", STR_PAD_LEFT);
					$old_image_path_inner = substr($old_image_path_inner, 0, 4) . '/' . substr($old_image_path_inner, 4, 2);
					$new_image_path_inner = substr($row->preview, 0, 4) . '/' . substr($row->preview, 4, 2);
					$old_image_path = $old_path . '/thumb/' . $old_image_path_inner . '/' . $row->preview;
					$old_full_image_path = $old_path . '/img/' . $old_image_path_inner . '/' . str_replace('s.', '.', $row->preview);
				}
				else
				{
					$old_image_path_inner = substr($row->preview, 0, 4) . '/' . substr($row->preview, 4, 2);
					$new_image_path_inner = substr($row->preview, 0, 4) . '/' . substr($row->preview, 4, 2);
					$old_image_path = $old_path . '/thumb/' . $old_image_path_inner . '/' . $row->preview;
					$old_full_image_path = $old_path . '/img/' . $old_image_path_inner . '/' . str_replace('s.', '.', $row->preview);
				}
				$new_image_path = $new_path . '/thumb/' . $new_image_path_inner . '/' . $row->preview;
				$new_full_image_path = $new_path . '/img/' . $new_image_path_inner . '/' . str_replace('s.', '.', $row->preview);
				if(file_exists($old_image_path))
				{
					if(!file_exists($new_image_path))
					{
						@mkdir($new_path . '/thumb/' . $new_image_path_inner, 0777, TRUE);
						if($move_images)
							rename($old_image_path, $new_image_path);
						else
							copy($old_image_path, $new_image_path);
							
						$db->query('INSERT INTO `' . $shortname .  '_images_tmp` 
							(media_id, preview_' . (($row->parent == 0)?'op':'reply') . ') 
							VALUES (\'' . $row->media_id . '\', \'' . $db->real_escape_string($row->preview) . '\')
							ON DUPLICATE KEY UPDATE preview_' . (($row->parent == 0)?'op':'reply') . ' = VALUES(preview_' . (($row->parent == 0)?'op':'reply') . ')
						');
						if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
					}
					
					
					if($full_images && file_exists($old_full_image_path) && !file_exists($new_full_image_path))
					{
						@mkdir($new_path . '/img/' . $new_image_path_inner, 0777, TRUE);
						if($move_full_images)
							rename($old_full_image_path, $new_full_image_path);
						else
							copy($old_full_image_path, $new_full_image_path);
							
						$db->query('INSERT INTO `' . $shortname .  '_images_tmp`
							(media_id, media_filename) 
							VALUES (\'' . $row->media_id . '\', \'' . $db->real_escape_string(str_replace('s.', '.', $row->preview)) . '\')
							ON DUPLICATE KEY UPDATE media_filename = VALUES(media_filename)
						');
						if ($db->error && !$disable_db_errors) die('[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
					}
					
					
					$images_processed++;
					
					// this is just a screen printing system
					if(time() - $init_time > 0)
					{
						if(!isset($last_output))
							$last_output = 0;
						
						$deleting = "";
						for($i = 0; $i < $last_output + 1; $i++)
						{
							$deleting .= "\x08";
						}
						$output = "Processing rows " . $offset . " to " . ($offset - 100000) . " - Processed: " . $images_processed . " - Images per hour: " . floor(($images_processed/(time() - $init_time))*3600);
						echo $deleting . str_pad($output, $last_output);
						$last_output = strlen($output);												
					}					
				}
			}
			
			$db->query('
				UPDATE ' . $shortname . '_images img, ' . $shortname . '_images_tmp temp
				SET img.preview_op = COALESCE(temp.preview_op, img.preview_op),
					img.preview_reply = COALESCE(temp.preview_reply, img.preview_reply)' .
					((!$full_images)?:', img.media_filename = COALESCE(temp.media_filename, img.media_filename)') .
				' WHERE img.media_id = temp.media_id
			');
			
			if ($db->error && !$disable_db_errors) die(PHP_EOL . '[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
			
			$db->query('TRUNCATE ' . $shortname . '_images_tmp;');
			if ($db->error && !$disable_db_errors) die(PHP_EOL . '[database error] ' . $db->error . PHP_EOL . 'You can ignore errors with the --ignore-db-errors argument. Use the --help argument to know more.' . PHP_EOL);
			
			mysqli_free_result($images_res);
			
			$offset -= 100000;
		}
		echo PHP_EOL; // the static output doesn't play well here
		echo 'Phase 3, in board `' . $shortname . '`: done.'. PHP_EOL;
	}
	
	/*
	| PHASE 4: remove _temp and _old tables
	*/
	
	if(!$args->passed('--phase') || $args->get_full_passed('--phase') == 4)
	{
		echo 'Removing temporary `' . $shortname . '_temp` table if still present.'.PHP_EOL;
		$db->query('DROP TABLE IF EXISTS `' . $shortname . '_temp`');
		echo 'Removing any old `' . $shortname . '_old` table if still present.'.PHP_EOL;
		$db->query('DROP TABLE IF EXISTS `' . $shortname . '_old`');

		echo 'Phase 4, in board `' . $shortname . '`: done.'. PHP_EOL;
		echo "\x07Remember to delete the old image folders as described in the readme!";
	}
	
}