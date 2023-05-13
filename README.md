# MSU Scripts and Packs

I'm making an MSU pack for the [SMZ3 Cas' Randomizer](https://github.com/Vivelin/SMZ3Randomizer/) for the first time and I'm doing it in Linux so none of the usual tools are working.
So, since I too am a tool, I'm making my own workflow for this.

## Scripts

I wrote a few Kotlin scripts to help out with the process.
Download the `kotlin-compiler-(version).zip` from [GitHub](https://github.com/JetBrains/kotlin/releases/) and extract it to the same directory as the scripts.
Edit the scripts so the "shebang" at the top points at the right location.
I have the scripts in `/opt/msu`, so that's what I've been using in the scripts.
You can also put the Kotlin compiler on your `PATH` environment variable.

(Sorry I don't currently have cross-platform support here.)

### `msupcm.main.kts`

This script converts source audio files into PCM files with the proper header for MSU support.
The script is mostly compatible with the JSON format that [msupcm++](https://github.com/qwertymodo/msupcmplusplus/) uses.
Run the script with no arguments to see usage notes.

My current workflow involves adding a track to the JSON, running the script with the track number and the `-raw` option, and importing the resulting (headerless) PCM into Audacity to look for loop points.

### `playmsu.main.kts`

This script plays an MSU-compatible PCM file.
The file must have a valid MSU header; that is, the `MSU1` magic number and a four-byte loop point.

When run with a single argument, the script will play the given track indefinitely.
Add the `-loop` argument to play the bit of audio surrounding the loop point over and over, to check for pops.
Add the `-writeloop` and a filename to write the above audio to a separate PCM file that can be imported into Audacity and visually inspected for pops.
