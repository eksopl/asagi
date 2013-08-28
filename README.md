# Asagi

[Asagi](http://eksopl.github.io/asagi/) is a reimplementation of Fuuka's dumper side in Java. It allows you to dump and archive posts, images and all relevant data from imageboards into a local database and local image store (at the moment, only 4chan is supported).

The first goal was to reach feature parity with Fuuka's dumper. New features and enhancements are to follow next.

To provide a frontend for said data (providing a web interface to use it as an archive is the usual reason to use a program like this to begin with), you'll have to use [Fuuka](/eksopl/fuuka) or even [FoolFuuka](/FoolCode/FoolFuuka). Currently, only FoolFuuka is supported, since Asagi's schema and directory structure have diverged slightly from Fuuka's (the biggest change is that it stores image files and thumbnails according to a new directory configuration that doesn't save duplicates).

Asagi has been running successfully in a production environment for up to some years by some sites. The only currently supported frontend (FoolFuuka) usually handles downloading a compiled Asagi binary for you, but you can [download Asagi releases manually](https://github.com/eksopl/asagi/releases/), if you wish.

Its short term goals are to:

* Be efficient
* Be resilient to network weirdness, bad data and other bad behavior
* Be clearly written
* Follow Fuuka's internal structure as much as possible

Its long term goals are to eventually be:

* Extendable to other imageboards
* Extendable to other means of data feeding (local rather than HTTP, for example)

It uses:

* MySQL Connector/J to talk to MySQL
* PostgreSQL JDBC Driver to talk to PostgreSQL
* Apache HttpComponents to perform HTTP requests
* Joda Time to be able to do anything with dates in Java without losing one's sanity
* Gson to read the configuration file
* Guava for small extras that are nice to have here and there
* JNA to be able to perform chown/chmod/getgrnam on Unix platforms
* Maven for dependency resolution and building

## FAQ

### Why would you do this?
Fuuka's dumper is a bit too memory hungry. This was a result out of a frustrating afternoon trying to figure out how to make Fuuka's dumper use less memory. At the end, I was only able to shave off something like 10 MiB. This can be a pain for people that wish to run many dumpers at the same time. Also, while Perl is great for a lot of things, it's not the best choice when it comes to doing things with threads. And yes, I'm saying that I rewrote something in Java to save memory. Stop laughing.

### No seriously, why Java?
I can write Java in cruise control mode without having to think too hard, so it made for an amusing passtime for a while. Plus, Java threads and concurrent data structures are adequate to what I needed.

### JSON for the configuration file?
I just couldn't think of any good markup language for the configuration file. Amazing, isn't it?

* XML is the worst thing to happen to the world of software engineering, and if you think XML is good, then I don't want to have this discussion with you.
* YAML, while well intended, is completely unusuable for configuration files due to its whitespace sensitivity. While you can specify a language with forced indentation of code and have programmers happy with it, you cannot reasonably expect a regular user that just wants to get software working to know (or care) about indentation, spaces vs tabs, etc.
* Java property files are pretty simple. Unfortunatly, they're too simple -- they're restricted to latin1 and they don't support lists. Even if you try to get around those limitations by using Apache Commons Configuration, you still can't get lists inside lists, which I needed.
* INI files, as with Java property files, actually have a reasonable format overall, but they have the same issue as property files. On top of that, their grammar and syntax isn't well defined by anything or anyone.
* JSON is pretty okay. It falls short because its grammar is overly strict to be used in configuration files, in my opinion. Forgetting a comma will mean trouble, inserting an extra comma: likewise. Its huge gotcha is that it has no way of inserting comments, unacceptable for most configuration files.
* I'm sure there's tons of really good markup languages out there, but unfortunatly, they're either not very popular (which means that it's going to be hard to find a module/library/extension/plugin to deal with it in pretty much every environment), or I just haven't heard of them.

So out of all of those, even after weighing downsides, I ended up with JSON.

### What's with the name?
Asagi is the eldest of the Ayase sisters. Fuuka is the middle sister. The Ayase family lives next door to Yotsuba. Get it?

### I have a question not listed here
You can email eksopl at gmail with your question. If it's a bug, please use the [issue tracker](/eksopl/asagi/issues) instead.
