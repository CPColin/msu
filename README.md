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
* **2023-08-15, v1.4:** Swapped PoD and Skull Woods tracks, shortened Zelda Credits, and added a BCU Easter egg
* **2024-02-27, v1.4.1:** Added `msu_type` to the YAML so utilities don't have to guess (no audible changes)
* **2024-05-04, v1.5:** Added the SoM Menu music to the end of the SM Title tracks, so it doesn't have to loop silence
* **2024-07-09, v1.6:** Fixed the loop point on the Fairy track to include the pick-up notes from the original song's loop point. Also removed the "Hey" from Phantoon Battle.

### [Voss Gears and New Tetris Spheres](https://www.crappycomic.com/msu/Voss%20Gears%20and%20New%20Tetris%20Spheres.zip)

Songs from games composed by Neil Voss: The New Tetris (N64), Tetrisphere (N64), and Racing Gears Advance (GBA).
This pack is compatible with SMZ3 Cas' Randomizer version 9.3.0 and (presumably) mainline SMZ3.
There's a track list in the ZIP and also [here](https://www.crappycomic.com/msu/voss.yml).

This one is not available as standalone packs, because I was already stretching to fill out the SMZ3 track list as it was.

#### Version history

* **2024-02-16, v1.0:** Initial release
* **2024-02-16, v1.1:** Fixed incorrect keys in the YAML that stopped certain tracks from playing
* **2024-02-27, v1.1.1:** Added `msu_type` to the YAML so utilities don't have to guess (no audible changes)
* **2024-03-21, v1.2:** Adjusted a few loops and gave Armos Knights a different track so Hyper Beam would be unique
* **2024-04-24, v1.3:** Started the Helmasaur King music farther into the track so it's more intense

### [Don't Stop Me Now: Music from the Ogre Battle Series](https://www.crappycomic.com/msu/Don't%20Stop%20Me%20Now.zip)

Songs from the Ogre Battle series, including Ogre Battle: The March of the Black Queen, Tactics Ogre: Let Us Cling Together, and Ogre Battle 64: Person of Lordly Caliber, along with one song from Tactics Ogre: The Knight of Lodis.
This pack is compatible with SMZ3 Cas' Randomizer and (presumably) mainline SMZ3.
There's a track list in the ZIP and also [here](https://www.crappycomic.com/msu/dsmn.yml).

#### Version history

* **2025-01-30, v1.0:** Initial release
* **2025-02-11, v1.1:** Fixed loop on Kakariko Guards to account for the extra second of silence at the start

## Scripts

I wrote a few Kotlin scripts to help out with the process.
Download the `kotlin-compiler-(version).zip` from [GitHub](https://github.com/JetBrains/kotlin/releases/) and extract it to the same directory as the scripts.
You can also put the Kotlin compiler on your `PATH` environment variable and remove the relative path from the "shebangs" at the top of the scripts.

(Sorry I don't currently have cross-platform support here.)

### `msupcm.main.kts`

This script converts source audio files into PCM files with the proper header for MSU support.
The script is mostly compatible with the JSON format that [msupcm++](https://github.com/qwertymodo/msupcmplusplus/) uses.
Run the script with no arguments to see usage notes.

My current workflow involves adding a track to the JSON, running the script with the track number and the `-raw` option, and importing the resulting (headerless) PCM into Audacity to look for loop points.

Add `JAVA_OPTS="-Xmx1g"` to the start of the command to run the script if it runs out of heap space while processing a large file.

### `playmsu.kts`

This script plays an MSU-compatible PCM file.
The file must have a valid MSU header; that is, the `MSU1` magic number and a four-byte loop point.

When run with a single argument, the script will play the given track indefinitely.
Add the `-loop` argument to play the bit of audio surrounding the loop point over and over, to check for pops.
Add the `-writeloop` and a filename to write the above audio to a separate PCM file that can be imported into Audacity and visually inspected for pops.

### `jukebox.kts`

This script plays all the MSU-compatible PCM files you give it until either it runs out or you quit it.
Each track will play until it hits its loop point twice, at which point it will fade out over the next ten seconds.
Tracks that take a long time to hit their loop point the first time will start fading out as soon as they hit it.

Run the script with no arguments to see what of the above can be tweaked to your liking.

## Contact

* Discord: CPColin
* GitHub: https://github.com/CPColin/msu
* Bluesky: https://cpcolin.crappycomic.com
