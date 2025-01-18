#!/usr/bin/env kotlinc/bin/kotlin

@file:DependsOn("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.0")
@file:DependsOn("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0")
@file:OptIn(kotlin.time.ExperimentalTime::class)

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.io.RandomAccessFile
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeMark
import kotlin.time.TimeSource

val BITS_PER_BYTE = 8

val CHANNELS = 2

val LOOP_POINT_SIZE = 4

val MSU_MAGIC = "MSU1"

val MSU_MAGIC_SIZE = MSU_MAGIC.toByteArray().size

val NON_LOOPING_TRACKS = setOf(
    "zelda_title",
    "mirror",
    "pedestal_pull",
    "boss_victory",
    "ganon_reveal",
    "epilogue",
    "zelda_credits",
    "smz3_credits",
    "samus_fanfare",
    "item_acquired",
    "death_cry",
    "metroid_credits"
)

val SAMPLE_RATE = 44100

val SAMPLE_SIZE = 2

val TRACK_KEYS_SUPER_METROID = mapOf(
    1 to "samus_fanfare",
    2 to "item_acquired",
    3 to "item_room",
    4 to "metroid_opening_with_intro",
    5 to "metroid_opening_without_intro",
    6 to "crateria_landing_with_thunder",
    7 to "crateria_landing_without_thunder",
    8 to "crateria_space_pirates_appear",
    9 to "golden_statues",
    10 to "samus_aran_theme",
    11 to "green_brinstar",
    12 to "red_brinstar",
    13 to "upper_norfair",
    14 to "lower_norfair",
    15 to "inner_maridia",
    16 to "outer_maridia",
    17 to "tourian",
    18 to "mother_brain_battle",
    19 to "big_boss_battle_1",
    20 to "evacuation",
    21 to "chozo_statue_awakens",
    22 to "big_boss_battle_2",
    23 to "tension",
    24 to "plant_miniboss",
    25 to "ceres_station",
    26 to "wrecked_ship_powered_off",
    27 to "wrecked_ship_powered_on",
    28 to "theme_of_super_metroid",
    29 to "death_cry",
    30 to "metroid_credits",
    31 to "kraid_incoming",
    32 to "kraid_battle",
    33 to "phantoon_incoming",
    34 to "phantoon_battle",
    35 to "draygon_battle",
    36 to "ridley_battle",
    37 to "baby_incoming",
    38 to "the_baby",
    39 to "hyper_beam",
    40 to "metroid_game_over"
)

val TRACK_KEYS_ZELDA = mapOf(
    1 to "zelda_title",
    2 to "light_world",
    3 to "rainy_intro",
    4 to "bunny_theme",
    5 to "lost_woods",
    6 to "prologue",
    7 to "kakariko",
    8 to "mirror",
    9 to "dark_world",
    10 to "pedestal_pull",
    11 to "zelda_game_over",
    12 to "guards",
    13 to "dark_death_mountain",
    14 to "minigame",
    15 to "dark_woods",
    16 to "hyrule_castle",
    17 to "pendant_dungeon",
    18 to "cave_1",
    19 to "boss_victory",
    20 to "sanctuary",
    21 to "zelda_boss_battle",
    22 to "crystal_dungeon",
    23 to "shop",
    24 to "cave_2",
    25 to "zelda_rescued",
    26 to "crystal_retrieved",
    27 to "fairy",
    28 to "agahnims_floor",
    29 to "ganon_reveal",
    30 to "ganons_message",
    31 to "ganon_battle",
    32 to "triforce_room",
    33 to "epilogue",
    34 to "zelda_credits",
    35 to "eastern_palace",
    36 to "desert_palace",
    37 to "agahnims_tower",
    38 to "swamp_palace",
    39 to "palace_of_darkness",
    40 to "misery_mire",
    41 to "skull_woods",
    42 to "ice_palace",
    43 to "tower_of_hera",
    44 to "thieves_town",
    45 to "turtle_rock",
    46 to "ganons_tower",
    47 to "armos_knights",
    48 to "lanmolas",
    49 to "agahnim_1",
    50 to "arrghus",
    51 to "helmasaur_king",
    52 to "vitreous",
    53 to "mothula",
    54 to "kholdstare",
    55 to "moldorm",
    56 to "blind",
    57 to "trinexx",
    58 to "agahnim_2",
    59 to "ganons_tower_climb",
    60 to "light_world_2",
    61 to "dark_world_2"
)

val TRACK_KEYS_SMZ3 =
    TRACK_KEYS_ZELDA +
    TRACK_KEYS_SUPER_METROID.mapKeys { it.key + 100 } +
    (99 to "smz3_credits")

val TRACK_KEYS = mapOf(
    "The Legend of Zelda: A Link to the Past" to TRACK_KEYS_ZELDA,
    "Super Metroid" to TRACK_KEYS_SUPER_METROID,
    "Super Metroid / A Link to the Past Combination Randomizer" to TRACK_KEYS_SMZ3
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PackInfo(
    val album: String?,

    @JsonProperty("pack_name")
    val name: String,

    val tracks: Map<String, TrackInfo>,

    @JsonProperty("msu_type")
    val type: String
)

data class Track(
    val file: File,

    val packInfo: PackInfo?
) : Comparable<Track> {
    val number = """.*-(\d+)\.pcm""".toRegex().find(file.name)?.groupValues?.get(1)?.toInt() ?: 0

    val key = TRACK_KEYS[packInfo?.type]?.get(number) ?: "unknown"

    val trackInfo = packInfo?.tracks?.get(key)

    val album = trackInfo?.album ?: packInfo?.album ?: file.name

    val name = trackInfo?.name ?: "Unknown"

    val nonLooping = key in NON_LOOPING_TRACKS

    override fun compareTo(other: Track) = compareValuesBy(this, other, {it.packInfo?.name}, { it.number })

    override fun toString() = "$number - $key - $album - $name"
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TrackInfo(
    val album: String?,

    val name: String
)

var amplification = 1.0

var fadeSeconds = 10

// TODO: Implement playlist looping (and re-shuffling).

var loopCount = 2

var shuffle = false

var singleLoopMinutes = 3

/**
 * Applies the given [amplification] factor to the samples in the given [buffer], in-place.
 */
fun amplifyBuffer(buffer: ByteArray, amplification: Double) {
    var index = 0

    while (index < buffer.size) {
        val sample = (bytesToSample(buffer, index) * amplification).toInt()
        val bytes = sampleToBytes(sample)

        buffer[index] = bytes.first
        buffer[index + 1] = bytes.second

        index += SAMPLE_SIZE
    }
}

/**
 * Converts the bytes starting at the given [index] of the given [buffer] into a 16-bit, little-endian sample.
 * Returns zero if the index is out-of-range.
 */
fun bytesToSample(buffer: ByteArray, index: Int) =
    if (index >= buffer.size) {
        0
    } else {
        (buffer[index + 1].toInt() shl 8) or
                (buffer[index].toInt() and 0xff)
    }

fun computeFade(started: TimeMark, fadeStarted: Duration): Double {
    val fadeElapsed = started.elapsedNow() - fadeStarted!!

    return maxOf(0.0, (fadeSeconds.seconds - fadeElapsed).toDouble(DurationUnit.SECONDS) / fadeSeconds)
}

fun findMetadata(path: String) =
    File(path)
        .walk()
        .filter { it.name.endsWith(".yml") }
        .map {
            it.name to ObjectMapper(YAMLFactory()).registerKotlinModule().readValue<PackInfo>(it)
        }

fun findTracks(metadata: Map<String, PackInfo>, path: String) =
    File(path)
        .walk()
        .filterNot(File::isDirectory)
        .filter { it.name.endsWith(".pcm") }
        .map {
            val metadataKey = """(.*)-\d+\.pcm""".toRegex().find(it.name)?.groupValues?.get(1)
            val packInfo = metadata[metadataKey + ".yml"]

            Track(
                file = it,
                packInfo = packInfo
            )
        }
        .sorted()

/**
 * Returns `true` if the given [buffer] is silence. We're giving "silence" some wiggle room here.
 * We're also not converting the bytes into samples first. This might cause false positives.
 */
fun isSilence(buffer: ByteArray) = buffer.all { it in (-5.toByte()..5.toByte()) }

/**
 * Sets the given [file] back to the given [loopPoint], if necessary. Returns what should be the new [loopsLeft] value,
 * depending on whether it just looped and when the track [started] playing. Returns `-1` if the track should stop
 * playing immediately, because it does not loop.
 */
fun loopTrackIfNecessary(file: RandomAccessFile, track: Track, loopPoint: UInt, started: TimeMark, loopsLeft: Int) =
    if (file.filePointer >= file.length()) {
        if (track.nonLooping) {
            // Stop playing this track immediately.
            -1
        } else {
            printNow("..")

            seekSample(file, loopPoint)

            if (started.elapsedNow() >= singleLoopMinutes.minutes) {
                // We're past the limit, so start fading out now.
                0
            } else {
                // Otherwise, keep looping, but decrement the counter (don't go below zero though).
                maxOf(0, loopsLeft - 1)
            }
        }
    } else {
        // We didn't loop this time, so return the original value.
        loopsLeft
    }

/**
 * Opens an [audio output line][SourceDataLine] using a format that matches the MSU PCM standard:
 *
 * - 44100 samples per second
 * - 16 bits per sample
 * - two channels
 * - signed
 * - little-endian
 */
fun openAudio(): SourceDataLine {
    val audioFormat = AudioFormat(SAMPLE_RATE.toFloat(), SAMPLE_SIZE * BITS_PER_BYTE, CHANNELS, true, false)
    val sourceDataLine = AudioSystem.getSourceDataLine(audioFormat)

    sourceDataLine.open()
    sourceDataLine.start()

    return sourceDataLine
}

fun openFile(file: File): Pair<RandomAccessFile, UInt> {
    val file = RandomAccessFile(file, "r")

    readMagicNumber(file)

    val loopPoint = readLoopPoint(file)

    return file to loopPoint
}

fun parseArguments(args: MutableList<String>): List<String> {
    val paths = mutableListOf<String>()

    while (args.isNotEmpty()) {
        when (val arg = args.removeFirst()) {
            "-amplification" -> amplification = parseNextArgument(arg, args)
            "-fadeSeconds" -> fadeSeconds = parseNextArgument(arg, args)
            "-loopCount" -> loopCount = parseNextArgument(arg, args)
            "-shuffle" -> shuffle = true
            "-singleLoopMinutes" -> singleLoopMinutes = parseNextArgument(arg, args)
            else -> paths.add(arg)
        }
    }

    return paths
}

inline fun <reified T> parseNextArgument(arg: String, args: MutableList<String>): T {
    val nextArg = args.removeFirstOrNull()

    if (nextArg == null) {
        error("Expected another argument after $arg")
    }

    val parsedArg =
        when (T::class) {
            Double::class -> nextArg.toDoubleOrNull()
            Int::class -> nextArg.toIntOrNull()
            else -> error("Unsupported argument type: ${T::class}")
        }

    if (parsedArg == null) {
        error("Expected a ${T::class} after $arg but found '$nextArg'")
    }

    return parsedArg as T
}

fun playTrack(track: Track) {
    printNow("Playing $track")

    val audio = openAudio()
    val buffer = ByteArray(audio.bufferSize)
    val (file, loopPoint) = openFile(track.file)
    var loopsLeft = loopCount

    var fadeStarted: Duration? = null
    val started = TimeSource.Monotonic.markNow()

    while (loopsLeft >= 0) {
        val bytesRead = file.read(buffer)

        if (bytesRead == -1) {
            // This shouldn't happen, because we always seek back to the loop point when we reach the end of the file,
            // but just in case.
            break
        }

        val fade = if (loopsLeft > 0) {
            1.0
        } else {
            fadeStarted = fadeStarted ?: started.elapsedNow()

            computeFade(started, fadeStarted)
        }

        amplifyBuffer(buffer, amplification * fade)

        if (fadeStarted != null && isSilence(buffer)) {
            break
        }

        audio.write(buffer, 0, bytesRead)

        loopsLeft = loopTrackIfNecessary(file, track, loopPoint, started, loopsLeft)
    }

    audio.drain()
    audio.close()

    println()
}

fun printNow(string: String) {
    print(string)
    System.out.flush()
}

fun printUsage() {
    println(
        """
        Usage:
        
        jukebox.kts [options] [paths]

        options

            -amplification $amplification

                Amplifies each track by the given value, in linear power (not decibels)

            -fadeSeconds $fadeSeconds

                The number of seconds the fade at the end of a track should last

            -loopCount $loopCount

                The maximum number of times each track should loop before fading out

            -shuffle

                Shuffles the order the tracks are played, instead of sorting them by
                directory and track number

            -singleLoopMinutes $singleLoopMinutes

                The number of minutes after which a track will fade out after a single
                loop, instead of after $loopCount loop(s)
        
        paths

            A list of directories or files that should be played

            Each directory will be recursively scanned to find MSU tracks beneath it.
            Specify "." to play everything that's visible from the current directory.
        """.trimIndent()
    )
}

/**
 * Reads and returns the four-byte, little-endian loop point from the given [file].
 */
fun readLoopPoint(file: RandomAccessFile): UInt {
    val bytes = ByteArray(LOOP_POINT_SIZE)

    file.readFully(bytes)

    return (bytes[3].toUByte().toUInt() shl 24) or
            (bytes[2].toUByte().toUInt() shl 16) or
            (bytes[1].toUByte().toUInt() shl 8) or
            bytes[0].toUByte().toUInt()
}

/**
 * Reads the [magic number][MSU_MAGIC] from the given [file] and throws an exception if it isn't found.
 */
fun readMagicNumber(file: RandomAccessFile) {
    val msuMagicBytes = ByteArray(MSU_MAGIC_SIZE)

    file.readFully(msuMagicBytes)

    val msuMagic = String(msuMagicBytes)

    if (msuMagic != MSU_MAGIC) {
        throw Exception("Specified file is missing the \"$MSU_MAGIC\" magic number: $msuMagic")
    }
}

/**
 * Converts the given 16-bit [sample] into its constituent bytes, in little-endian order.
 */
fun sampleToBytes(sample: Int) = sample.toByte() to (sample shr 8).toByte()

/**
 * Seeks the given [file] to the given [sample], accounting for the size of each sample, the number of channels, and the
 * MSU PCM header.
 */
fun seekSample(file: RandomAccessFile, sample: UInt) {
    file.seek(MSU_MAGIC_SIZE + LOOP_POINT_SIZE + (sample.toLong() * SAMPLE_SIZE * CHANNELS))
}

val paths = parseArguments(args.toMutableList())

if (paths.isEmpty()) {
    printUsage()
} else {
    println("Loading MSU packs...")

    val metadata = paths.flatMap { findMetadata(it) }.toMap()
    val tracks = paths.flatMap { findTracks(metadata, it) }

    println("Press Ctrl-C to quit.")
    println()

    if (shuffle) {
        tracks.shuffled()
    } else {
        tracks
    }.forEach(::playTrack)
}

