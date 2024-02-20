#!/usr/bin/env kotlinc/bin/kotlin

import java.io.File
import java.io.RandomAccessFile
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

val BITS_PER_BYTE = 8

val CHANNELS = 2

val MSU_MAGIC = "MSU1"

val MSU_MAGIC_SIZE = MSU_MAGIC.toByteArray().size

val LOOP_POINT_SIZE = 4

val SAMPLE_SIZE = 2

val SAMPLE_RATE = 44100

/**
 * Loads from the given [file][filename] the audio surrounding its loop point and returns it.
 */
fun loadLoopAudio(filename: String): ByteArray {
    val buffer = ByteArray(SAMPLE_RATE * 2)
    val (file, loopPoint) = openFile(filename)
    
    // Read the audio before the loop point
    file.seek(file.length() - SAMPLE_RATE)
    file.read(buffer, 0, SAMPLE_RATE)

    // Read the audio after the loop point
    seekSample(file, loopPoint)
    file.read(buffer, SAMPLE_RATE, SAMPLE_RATE)

    file.close()

    return buffer
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

/**
 * Opens the file with the given [filename] and returns it along with its loop point.
 */
fun openFile(filename: String): Pair<RandomAccessFile, UInt> {
    val file = RandomAccessFile(filename, "r")

    readMagicNumber(file)

    val loopPoint = readLoopPoint(file)

    println("Loop point: $loopPoint")

    return file to loopPoint
}

/**
 * Loads the given [file][filename] and plays it indefinitely.
 */
fun playFile(filename: String) {
    val audio = openAudio()
    val buffer = ByteArray(audio.bufferSize)
    val (file, loopPoint) = openFile(filename)

    while (true) {
        val bytesRead = file.read(buffer)

        if (bytesRead == -1) {
            // This shouldn't happen, because we always seek back to the loop point when we reach the end of the file,
            // but just in case.
            break
        }

        audio.write(buffer, 0, bytesRead)

        if (file.filePointer >= file.length()) {
            println("Looped!")

            seekSample(file, loopPoint)
        }
    }

    audio.drain()
}

/**
 * Loads the given [file][filename] and indefinitely plays the bit of audio surrounding its loop point so the user can
 * listen for pops or other discontinuities.
 */
fun playLoop(filename: String) {
    val audio = openAudio()
    val buffer = loadLoopAudio(filename)
    val silence = ByteArray(SAMPLE_RATE)

    while (true) {
        audio.write(buffer, 0, buffer.size)
        audio.write(silence, 0, silence.size)
    }
}

fun printUsage() {
    println(
        """
        Usage:
        
        playmsu.kts
            Show this usage statement
        
        playmsu.kts (filename.pcm)
            Play the MSU PCM file with the given name, looping indefinitely
            
        playmsu.kts (filename.pcm) -loop
            Play the point of the MSU PCM file with the given name where the loop happens
            
        playmsu.kts (filename.pcm) -writeloop (output filename)
            Writes the point of the MSU PCM file with the given name where the loop happens to the given output file
            
        Press Ctrl-C to quit.        
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
 * Seeks the given [file] to the given [sample], accounting for the size of each sample, the number of channels, and the
 * MSU PCM header.
 */
fun seekSample(file: RandomAccessFile, sample: UInt) {
    file.seek(MSU_MAGIC_SIZE + LOOP_POINT_SIZE + (sample.toLong() * SAMPLE_SIZE * CHANNELS))
}

/**
 * Loads the given [file][filename] and writes a small bit of audio from before and after its loop point to the given
 * [outputFilename]. The resulting file can be loaded in an audio editing tool and visually inspected for a
 * discontinuity between the two samples at the exact middle.
 */
fun writeLoop(filename: String, outputFilename: String) {
    val buffer = loadLoopAudio(filename)

    File(outputFilename).outputStream().use {
        it.write(buffer)
    }
}

if (args.size == 1) {
    playFile(args[0])
} else if (args.size == 2 && args[1] == "-loop") {
    playLoop(args[0])
} else if (args.size == 3 && args[1] == "-writeloop") {
    writeLoop(args[0], args[2])
} else {
    printUsage()
}
