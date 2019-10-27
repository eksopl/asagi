# Asagi

**Asagi is no longer maintained and may not work properly anymore.**

[Asagi](http://eksopl.github.io/asagi/) was a reimplementation of Fuuka's dumper side in Java. It allowed you to dump and archive posts, images and all relevant data from imageboards into a local database and local image store. The original project only supported 4chan.

The first goal was to reach feature parity with Fuuka's dumper, and new features and enhancements followed (like the ability to use the JSON API).

To provide a frontend for said data (providing a web interface to use it as an archive is the usual reason to use a program like this to begin with), frontends like [Fuuka](/eksopl/fuuka) and [FoolFuuka](/FoolCode/FoolFuuka) existed. Asagi only supported FoolFuuka, since Asagi's schema and directory structure diverged slightly from Fuuka's (the biggest change is that it stores image files and thumbnails according to a new directory configuration that doesn't save duplicates).

Asagi successfully ran in a production environment for several years, although currently available forks and builds may suffer from severe  performance and memory issues due to changes in the 4chan API. The only supported frontend (FoolFuuka) usually handles downloading a compiled Asagi binary for you, but you can [download the historical Asagi releases manually](https://github.com/eksopl/asagi/releases/), if you wish, although again, they are most likely of no use to you due to the aforementioned API changes.

### What's with the name?
Asagi is the eldest of the Ayase sisters. Fuuka is the middle sister. The Ayase family lives next door to Yotsuba. Get it?
