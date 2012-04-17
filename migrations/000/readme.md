Fuuka fetcher to Asagi fetcher Migration
========================================
#### Note: This entire readme will be using the board `tv` as a reference.

DISCLAIMER
----------
This migration script makes large modifications to structure of the databases and image directory. A backup of all contents (both databases and images) is recommended before running this script. We will not be held accountable for your actions. Therefore, please read the entire content of this file before proceeding.


What are the exact modifications done in this migration?
--------------------------------------------------------
* __This changes the current database engine of MyISAM to InnoDB.__

	This will improve the safety of the database operations and introduce the ability to use transactions. Dropping the table-locking MyISAM greatly improves the performance, making it possible to run some slower queries without freezing the entire board. The memory usage will be higher, but it can be managed with the suggestions in the next section of this file.

* __The entire file structure of the image directory is changed completely.__

	This will decrease the current disk space usage by approximately 60% by only storing only one copy of every images. The folder structure will be generated using the filename of the image stored at the time.

* __A column will be appended to both the `tv` and `tv_images` table.__

    This will allow MySQL to search the `media_hash` columns and `JOIN` tables much faster.

* __The columns `op_filename`, `reply_filename` and `media_filename` will be appended to the `tv_images` table.__

    This will be used to locate the new path of the images matching the unique `media_hash`. Although we stated that all images are stored only once, thumbnails fall under a unique exemption and may store at most two images to match 4chan's thumbnail preview.

* __All MySQL triggers and procedures are updated to the latest revision.__


Suggestions and Possible Issues
----------------------
InnoDB has a reputation of having a much larger memory footprint than MyISAM. Since the current database engine has been converted to InnoDB, the configuration of the MySQL Server must be changed accordingly. All of the settings below were used on a server with 16GB RAM. Although these settings currently work with our tests, it is still recommended that settings are changed to suit the environment/server used. Unless the server is a Dedicated MySQL Server, please do not match your settings to ones found in tutorials online.

#### MySQL
* `innodb_buffer_size = 6G` [M-FN1]

    This is a very important setting for InnoDB and affects its performance. It is recommended to do serveral tests and find an optimal setting for this option. It should be noted that this setting should not be set too high causing the OS to forcefully kill MySQL or other processes.

* `innodb_log_file_size = 1024M` or `innodb_log_file_size = 512M`

    This is another important setting for InnoDB that allows for recovery during crashes or power failure. This setting should not be too high or it may take a long time before MySQL becomes operational again.

* `innodb_flush_log_at_trx_commit = 2`

    Due to performance issues, it is recommended to have this setting at `2` during migration. However, this can be changed depending on how safe you wish to have your database and wish to maintain all queries done to the database up until the crash.

* `innodb_flush_method = O_DIRECT` [M-FN2]

    This allows MySQL to bypass the OS Cache that should increase InnoDB performance. It mainly affects non-Windows systems.

* `innodb_file_per_table = 1` [M-FN3]

    This will allow InnoDB to store each table and its indexes in their own file instead of one large file containing all InnoDB data for the entire MySQL Server.

#### Operating System
* `Swap Memory = 10G`

    This is to allow MySQL or the OS to use additional memory in case it is needed to avoid any future problems.


#### Notes
* M-FN1: _If these settings are changed, the log files `ib_logfile0` and `ib_logfile1` must be removed from the system in order for MySQL to boot. Refer to http://dba.stackexchange.com/a/1265 for more information._
* M-FN2: _For more information, please read http://stackoverflow.com/a/2763487/644504 to learn more about this setting._
* M-FN3: _For instructions, please read and follow http://stackoverflow.com/a/3456885/644504 to apply on exising tables._


Requirements
------------
* Disk Space for Backup and Migration
* Fuuka r142+
* PHP 5.2+ (Preferred: 5.3.10+)
* MySQL 5.1+ (Preferred: 5.5+ for UTF8MB4/4-byte Char Support)

Usage
-----

The script is meant to be safe to stop and restart, but it's better if left alone, as fallback functions after a restart can be considerably slower. Run the phases sequentially and make sure they have finished their operations before going on. A notice is printed on screen when done. If unsure if the phase has completed, run the phase again.

It is suggested to use GNU Screen (or tmux, if you prefer it), to run your migration, so even if your SSH client disconnects it will keep running safely. A short guide:

	bash$ screen -S migration  # runs a new session named migration, just use the 
	CTRL+A, then D             # "detaches" the screen session
	bash$ screen -x migration  # "attaches" to the screen session
	screen$ exit               # closes the screen session

Ensure that the `migrations` folder is located in the same directory as the `asagi.json` configuration file.

1. If you already have Asagi running with other boards, leave it running. Open `asagi.json` and add the board you're going to work on.

1. Navigate to the folder containing the migration script.

        $ cd /path/to/asagi/migrations/000/

1. Run the first phase of the migration process, that will convert your database to InnoDB. Decide if you want to do it with or without downtime.
  * With downtime
	
      1. Stop your Fuuka fetcher for this board now. Run the following:
      
      1. Run the following:

				$ php migrate_000.php --board tv --phase 1 --with-downtime
  * Without downtime
	
		You will have to duplicate manually the board database files into temporary files. This is not necessary and probably better to avoid if you aren't savvy with MySQL.
      1. Shut down MySQL (in example on Debian `/etc/init.d/mysql stop`)

      1. Reach your MySQL data folder (in example `cd /usr/local/mysql/data/`)

      1. Enter the folder with the same name of your database (in example `cd fuuka/`)

      1. Start copying your table files:
	
		
				cp tv.MYI tv_temp.MYI
				cp tv.MYD tv_temp.MYD
				cp tv.frm tv_temp.frm
				chown mysql:mysql tv_temp*
				
			Do this for every board you need to convert.

      1. Start MySQL (in example on Debian `/etc/init.d/mysql start`)

      1. Run the command:
		
				$ php migrate_000.php --board tv --phase 1
				
      1. When done, stop the Fuuka fetcher for this board and make your board unavailable on your site
        
1. Run the second phase of the process, that will update your database definitions:


        $ php migrate_000.php --board tv --phase 2

1. Run or restart the Asagi fetcher and make your board available again.

1. Run the third phase of the process, that will copy the images without duplicates:

		$ php migrate_000.php --board tv --phase 3

1. Run the fourth phase of the process, that will remove the temporary tables used:

		$ php migrate_000.php --board tv --phase 4		
		
1. Remove the old images folders manually:

		$ cd /path/to/your/fuuka/board/
		$ rm -R tv_old/

Documentation
-------------
    $ php migrate_000.php --help
    ============================
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