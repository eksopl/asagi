Asagi migration from Fuuka (migrate_000.php)
========================================

Script to migrate from Fuuka fetcher to Asagi fetcher

__In this whole readme I used the board `tv` only as example.__

----

ATTENTION
---------
This script modifies a large part of your database and of your image folder.
You want to have a backup of all your databases and all your images before running this.
Also, please read all the content of this file.

Requirements
------------
* PHP 5.3+ (if you have any less than 5.3.10 _upgrade as soon as possible_)
* MySQL 5.5+ (lower is OK, but it won't enable you to use utf8mb4, which means you will _keep_ saving question marks instead of 4-byte symbols)
* The Fuuka database on the latest version available
* Being prepared to see this run for hours or even days, depending on the size of the database (users will not notice that it's processing)

Usage 
-----

Put this script in the same folder where your asagi.json resides and run it:

	$ php migrate_000.php

You can run it as many times as you want, until you are done: it is able to check where the process has stopped. Of course it will waste time in doing so, so let it run and leave it alone as much as possible.

You don't need to put offline your database as the migration will deal with creating temporary tables and rotating them with the live ones. __You can keep the dumpers running__. You will have some moments in which the single board is locked, but unless you have a table with several tens of millions of rows, you will only have like 5 minutes of downtime while the temporary table is being created.

Suggestions and possible issues
---------------
We're turning the large tables into InnoDB, which has particular memory requirements. Almost for sure will have to change settings in your `my.cnf`. Here's some suggestions.

* `innodb_buffer_pool_size = 6G` on a 16Gb RAM server, if not less, like `4G`. On sites you will find people saying that 80% of your server RAM is good, but they don't take in account Sphinx running and other applications. They consider it a MySQL-only server, which you might not be able to afford.
* `innodb_log_file_size = 1024M` or even `512M`. Don't try to match the suggested 25% over such amounts.
* If you change the above two variables, remember to go in your `../mysql/data` folder and remove the `ib_logfile0` and `ib_logfile1`, else MySQL won't boot. If unsure, look here: http://dba.stackexchange.com/a/1265
* `innodb_flush_log_at_trx_commit = 2` unless you care to have your database safe till the last query. With 2 you might lose one second of queries if MySQL crashes. This will speed up insertions. Absolutely have it on 2 during the migration.
* If you're on Linux or other non windows system, `innodb_flush_method = O_DIRECT` should improve performance. More on this: http://stackoverflow.com/a/2763487/644504
* If you have time to waste you should add `innodb_file_per_table`. It will help not having a humongous file containing all the InnoDB databases that won't shrink in filesize on row deletion. The issue in this is that you will have to backup all your InnoDB tables, delete them and restore them, to be able to  remove the humongous file. Here's the procedure: http://stackoverflow.com/a/3456885/644504 or, you could convert your InnoDB tables to MyISAM instead of backing up, then convert them back.
* Have a lot of swap available: we've had some spikes of memory usage that made Linux kill MySQL during the migration. If you've lived calm with 16Gb RAM and never saw your server swapping, this might come like a shock to you - it did for me and I was clueless for two days on why MySQL was going down. http://serverfault.com/a/218125 will help you adding a swap file. 2Gb to 10Gb of swap is nice to have.

You can be OCD about InnoDB by googling the hell out of it! Here's an interesting link from which I learned a lot http://www.mysqlperformanceblog.com/2007/11/01/innodb-performance-optimization-basics/

Notice that when MySQL 5.6 will be final the situation will improve for InnoDB drammatically, but I don't suggest using 5.6 betas. Rather, I suggest to have the latest stable MySQL release as InnoDB keeps being improved.


What exactly does this migration do
-----------------------------------
* turns `tv` to InnoDB. To avoid downtime, it will copy your live `tv` table into a `tv_temp` table. It will then create a `tv_new` InnoDB table and copy the posts in blocks of 2m rows from `tv_temp`. When the `tv_temp` posts are all copied, it will grab the latest posts from the live `tv` table to get up to date. When done, the live `tv` table will be named `tv_old` and the `tv_new` table will be renamed to `tv`.
* `media_id` column will be added to the board table, and a `id` column will be added to the `_images` table. This will allow for faster `media\_hash` search and faster `JOIN` on the table.
* changes the images' directory naming scheme to store only one copy of every image. The directory names will be based on the timestamp in the filename. The migration script will rename the old image directory to `_old` and create a new directory
* adds `op_filename`, `reply_filename` and `media_filename` columns to the `_images` table to locate the file. We're going to have two different files for OP and replies.
* updates the MySQL triggers and procedures to the latest versions.

Notes
-----

Migration made in April 2012. Blame Woxxy for mistakes in this file.