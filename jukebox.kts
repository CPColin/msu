#!/usr/bin/env kotlinc/bin/kotlin

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

val NON_LOOPING_TRACKS = mapOf(
    1 to "zelda_title",
    8 to "mirror",
    10 to "pedestal_pull",
    19 to "boss_victory",
    29 to "ganon_reveal",
    33 to "epilogue",
    34 to "zelda_credits",
    99 to "smz3_credits",
    101 to "samus_fanfare",
    102 to "item_acquired",
    129 to "death_cry",
    130 to "metroid_credits"
)

val SAMPLE_RATE = 44100

val SAMPLE_SIZE = 2

// TODO: Read metadata from associated YAML and display it when playing.
data class Track(
    val file: File
) : Comparable<Track> {
    val number = """.*-(\d+)\.pcm""".toRegex().find(file.name)?.groupValues?.get(1)?.toInt() ?: 0

    // TODO: Currently inaccurate for SM standalone packs. Switch to using key when available.
    val nonLooping = number in NON_LOOPING_TRACKS

    // TODO: Sort by MSU pack first, then track number.
    override fun compareTo(other: Track) = compareValuesBy(this, other) { it.number }

    override fun toString() = file.name
}

var amplification = 1.0

var fadeSeconds = 10

// TODO: Implement playlist looping (and re-shuffling).

// TODO: Implement customizable loop count before fading out each track.

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

fun findTracks(path: String) =
    File(path)
        .walk()
        .filterNot(File::isDirectory)
        .filter { it.name.endsWith(".pcm") }
        // TODO: Also filter for MSU magic number and loop point
        .map { Track(file = it) }
        .sorted()

/**
 * Returns `true` if the given [buffer] is silence. We're giving "silence" some wiggle room here.
 * We're also not converting the bytes into samples first. This might cause false positives.
 */
fun isSilence(buffer: ByteArray) = buffer.all { it in (-5.toByte()..5.toByte()) }

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

// TODO: This function is too long. Break it up.
@OptIn(kotlin.time.ExperimentalTime::class)
fun playTrack(track: Track) {
    printNow("Playing $track")

    val audio = openAudio()
    val buffer = ByteArray(audio.bufferSize)
    val (file, loopPoint) = openFile(track.file)
    var loopsLeft = 2

    var fadeStarted: Duration? = null
    val started = TimeSource.Monotonic.markNow()

    endOfTrack@ while (true) {
        val bytesRead = file.read(buffer)

        if (bytesRead == -1) {
            // This shouldn't happen, because we always seek back to the loop point when we reach the end of the file,
            // but just in case.
            break
        }

        val fade = if (loopsLeft <= 0) {
            fadeStarted = fadeStarted ?: started.elapsedNow()

            val fadeElapsed = started.elapsedNow() - fadeStarted!!

            maxOf(0.0, (fadeSeconds.seconds - fadeElapsed).toDouble(DurationUnit.SECONDS) / fadeSeconds)
        } else {
            1.0
        }

        amplifyBuffer(buffer, amplification * fade)

        if (fadeStarted != null && isSilence(buffer)) {
            break@endOfTrack
        }

        audio.write(buffer, 0, bytesRead)

        if (file.filePointer >= file.length()) {
            if (track.nonLooping) {
                break@endOfTrack
            }

            loopsLeft--

            if (started.elapsedNow() >= singleLoopMinutes.minutes) {
                // Subtract an additional loop if we're past the limit.
                loopsLeft--
            }

            printNow("..")

            seekSample(file, loopPoint)
        }
    }

    audio.drain()

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

            -shuffle

                Shuffles the order the tracks are played, instead of sorting them by
                directory and track number

            -singleLoopMinutes $singleLoopMinutes

                The number of minutes after which a track will fade out after a single
                loop, instead of after the second loop
        
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
    println("Press Ctrl-C to quit.")
    println()

    val tracks = paths.flatMap { findTracks(it) }
    
    if (shuffle) {
        tracks.shuffled()
    } else {
        tracks
    }.forEach(::playTrack)
}

