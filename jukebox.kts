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

val MSU_MAGIC = "MSU1"

val MSU_MAGIC_SIZE = MSU_MAGIC.toByteArray().size

val LOOP_POINT_SIZE = 4

val SAMPLE_SIZE = 2

val SAMPLE_RATE = 44100

// TODO: Read metadata from associated YAML and display it when playing.
data class Track(
    val file: File
) : Comparable<Track> {
    val number = """.*-(\d+)\.pcm""".toRegex().find(file.name)?.groupValues?.get(1)?.toInt() ?: 0

    // TODO: Sort by MSU pack first, then track number.
    override fun compareTo(other: Track) = compareValuesBy(this, other) { it.number }

    override fun toString() = file.name
}

// TODO: Implement a master amplification option and an argument for adjusting it.

var fadeSeconds = 10

// TODO: Implement playlist looping (and re-shuffling).

// TODO: Implement customizable loop count before fading out each track.

var shuffle = false

var singleLoopMinutes = 3

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
            "-fadeSeconds" -> fadeSeconds = parseIntArgument(arg, args)
            "-shuffle" -> shuffle = true
            "-singleLoopMinutes" -> singleLoopMinutes = parseIntArgument(arg, args)
            else -> paths.add(arg)
        }
    }

    return paths
}

fun parseIntArgument(arg: String, args: MutableList<String>): Int {
    val nextArg = args.removeFirstOrNull()

    if (nextArg == null) {
        error("Expected another argument after $arg")
    }

    val intArg = nextArg.toIntOrNull()

    if (intArg == null) {
        error("Expected a number after $arg but found '$nextArg'")
    }

    return intArg
}

// TODO: This function is too long. Break it up.
@OptIn(kotlin.time.ExperimentalTime::class)
fun playTrack(track: Track) {
    printNow("Playing $track")

    val audio = openAudio()
    val buffer = ByteArray(audio.bufferSize)
    val (file, loopPoint) = openFile(track.file)
    var loopsLeft = 2 // TODO: Set to zero for non-looping tracks

    var fadeStarted: Duration? = null
    val started = TimeSource.Monotonic.markNow()

    endOfTrack@ while (true) {
        val bytesRead = file.read(buffer)

        if (bytesRead == -1) {
            // This shouldn't happen, because we always seek back to the loop point when we reach the end of the file,
            // but just in case.
            break
        }

        if (loopsLeft <= 0) {
            fadeStarted = fadeStarted ?: started.elapsedNow()

            val fadeElapsed = started.elapsedNow() - fadeStarted!!

            val amplification = maxOf(0.0, (fadeSeconds.seconds - fadeElapsed).toDouble(DurationUnit.SECONDS) / fadeSeconds)

            if (amplification == 0.0) {
                break@endOfTrack
            }

            var index = 0

            while (index < buffer.size) {
                val sample = (bytesToSample(buffer, index) * amplification).toInt()
                val bytes = sampleToBytes(sample)

                buffer[index] = bytes.first
                buffer[index + 1] = bytes.second

                index += SAMPLE_SIZE
            }

            // TODO: Break early if amplification is still above zero but all the samples we just wrote were zero.
        }

        audio.write(buffer, 0, bytesRead)

        if (file.filePointer >= file.length()) {
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

