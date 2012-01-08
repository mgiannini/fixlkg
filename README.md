# fixlkg

This is a small utility to fix the mp3 metadata information for the CDs
distributed with Randall Buth's "Living Koine Greek". In particular, this fixes
a minor annoyance with the first CD (Introduction Part One) where when you
import it into iTunes or Windows Media Player, it imports as 3 albums with
track names that do not match the file name on the disk.  This makes it very
difficult to match tracks to lessons when you are working through the material.

By running this utility on the CD it will fix the mp3s so that every track is
on the same album (with the name you specify) and (as an added bonus), gives
the album a nice cover picture.

NOTE: This problem does not exist for the CDs for Part 2a and 2b.  Each lesson
imports to your music library as separate album. If you'd prefer to see them
all in one album (and have the nice cover art) you could also run this utility
on those CDs, but it isn't really necessary.

## Installation

You will need to download the following two files:

1. fixlkg-1.0.0-standalone.jar
2. fixlkg.exe (for windows)

Copy these file to the same directory and open a command prompt there.

## Usage

Windows: From the command-line run the following command to fix the mp3s and
assign them an album name of "LKG CD 1".  This assumes your CD/DVD-ROM drive
is on the E:\ drive.  The output will be in the current directory in a folder
with the same name as the --album option.

    > fixlkg.exe --album "LKG CD 1" E:

*nix: Hopefully java is on your PATH.  Execute the following command (which
assumes your CD/DVD-ROM drive is mounted at /mnt/media)

    $ java -jar fixlkg-1.0.0-standalone.jar --album "LKG CD 1" /mnt/media

## License

Copyright (C) 2012 Matthew Giannini

Distributed under the Eclipse Public License, the same as Clojure.
