# MSU Packs and Scripts

I made an MSU pack for the [SMZ3 Cas' Randomizer](https://github.com/Vivelin/SMZ3Randomizer/) for the first time and did it in Linux so none of the usual tools worked.
So, since I too am a tool, I made my own workflow for this.

## Packs

### [Trials and Secrets](https://www.crappycomic.com/msu/Trials%20and%20Secrets.zip)

Songs from Secret of Mana and Trials of Mana, with the former being used in Super Metroid and the latter being used in ALttP.
This pack is compatible with SMZ3 Cas' Randomizer version 9.3.0 and (presumably) mainline SMZ3.
There's a track list in the ZIP and also [here](https://www.crappycomic.com/msu/trials_and_secrets_msu.yml).

Also available as standalone packs (SM has not been specfically tested):

* **SM:** [Secret of Zebes](https://www.crappycomic.com/msu/Secret%20of%20Zebes.zip) ([track list](https://www.crappycomic.com/msu/secret_of_zebes_msu.yml))
* **Z3:** [Trials of Hyrule](https://www.crappycomic.com/msu/Trials%20of%20Hyrule.zip) ([track list](https://www.crappycomic.com/msu/trials_of_hyrule_msu.yml))

#### Version history

* **2023-05-20, v1.0:** Initial release
* **2023-05-21, v1.0.1:** Standalone packs released
* **2023-06-12, v1.0.2:** Corrected track name for "Lost Woods" and using new sub-track feature for a few tracks (no audible changes)
* **2023-06-25, v1.0.3:** Updated to be compatible with SMZ3 Cas' Randomizer version 9.3.0 and (presumably) mainline SMZ3 (no audible changes)
* **2023-06-28, v1.1:** Fixed an issue in the script that was giving the standalone packs slight volume differences from the combined pack
* **2023-06-30, v1.2:** Fixed an issue in the script that was applying `pad_start` at the end, too, instead of `pad_end`, causing the Mother Brain tracks to pause before looping
* **2023-07-10, v1.3:** Moved "Raven" from Tower of Hera to Thieves' Town (replacing "Breezin") and added "Few Paths Forbidden" to Tower of Hera

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

Add `JAVA_OPTS="-Xmx1g"` to the start of the command to run the script if it runs out of heap space while processing a large file.

### `playmsu.main.kts`

This script plays an MSU-compatible PCM file.
The file must have a valid MSU header; that is, the `MSU1` magic number and a four-byte loop point.

When run with a single argument, the script will play the given track indefinitely.
Add the `-loop` argument to play the bit of audio surrounding the loop point over and over, to check for pops.
Add the `-writeloop` and a filename to write the above audio to a separate PCM file that can be imported into Audacity and visually inspected for pops.

## Contact

* Discord: CPColin
* GitHub: https://github.com/CPColin/msu
* Twitter: https://twitter.com/CPColin
