#!/usr/bin/env /opt/msu/kotlinc/bin/kotlin

@file:DependsOn("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0")

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.io.OutputStream
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

val CHANNELS = 2

val MSU_MAGIC = "MSU1"

val SAMPLE_BYTES = 2

@JsonIgnoreProperties(ignoreUnknown = true)
data class Msu(
    /**
     * Target root-mean-square normalization, in decibels. This value applies whenever [Track.normalization] is `null`.
     */
    val normalization: Double,

    @JsonProperty("output_prefix")
    val outputPrefix: String,

    val tracks: List<Track>
)

/**
 * A sequence that converts the given [pcmData] from bytes into 16-bit, little-endian, signed samples. The returned
 * samples are from the left channel when the [left] flag is `true` and from the right channel otherwise.
 */
class SampleSequence(private val pcmData: ByteArray, private val left: Boolean): Sequence<Int> {
    override fun iterator() = object : Iterator<Int> {
        var index = if (left) 0 else 2

        override fun hasNext() = index < pcmData.size

        override fun next(): Int {
            index += 4

            return bytesToSample(pcmData, index - 4)
        }
    }
}

data class Track(
    @JsonProperty("file")
    val filename: String,

    @JsonProperty("loop")
    val loopPoint: Int,

    /**
     * Target root-mean-square normalization, in decibels. Defaults to [Msu.normalization] when `null`.
     */
    val normalization: Double?,

    val title: String,

    @JsonProperty("track_number")
    val trackNumber: Int,

    @JsonProperty("trim_end")
    val trimEnd: Int?,

    @JsonProperty("trim_start")
    val trimStart: Int
)

/**
 * Converts the bytes starting at the given [index] of the given [pcmData] into a 16-bit, little-endian sample.
 */
fun bytesToSample(pcmData: ByteArray, index: Int) =
    (pcmData[index + 1].toInt() shl 8) or
            (pcmData[index].toInt() and 0xff)

fun bytesToSamples(bytes: Int) = bytes / SAMPLE_BYTES / CHANNELS

/**
 * Computes and returns the (linear) Root Mean Square of the given [pcmData].
 */
fun computeRootMeanSquare(pcmData: ByteArray) =
    (computeRootMeanSquare(pcmData, true) + computeRootMeanSquare(pcmData, false)) / 2.0

fun computeRootMeanSquare(pcmData: ByteArray, left: Boolean) =
    SampleSequence(pcmData, left)
        // Square
        .map { it.toDouble().pow(2) }
        // Total
        .sum()
        // Mean
        .let { it / bytesToSamples(pcmData.size) }
        // Root Mean Square
        .let { sqrt(it) }
        // Relative to maximum value of a 16-bit signed sample
        .let { it / ((1 shl 15) - 1) }

fun convertToPcmData(filename: String): ByteArray {
    val process = ProcessBuilder(
        listOf(
            "ffmpeg",
            "-loglevel", "warning",
            "-i", filename,
            "-c:a", "pcm_s16le",
            "-f", "s16le",
            "-ac", "2",
            "-ar", "44100",
            "-"
        )
    ).redirectError(ProcessBuilder.Redirect.INHERIT)
        .redirectInput(ProcessBuilder.Redirect.INHERIT)
        .start()

    return process.getInputStream().readBytes()
}

/**
 * Converts the given value from [decibels] (relative to maximum) to its linear power equivalent.
 */
fun decibelsToLinear(decibels: Double): Double = 10.0.pow(decibels / 20.0)

fun linearToDecibels(linear: Double) = 20.0 * log10(linear)

/**
 * Normalizes the given [pcmData] by multiplying each sample such that the resulting RMS value will match the given
 * [normalization] value, as specified in decibels. Overwrites (and returns) the original array.
 */
fun normalizePcmData(pcmData: ByteArray, normalization: Double): ByteArray {
    val factor = decibelsToLinear(normalization) / computeRootMeanSquare(pcmData)
    var index = 0

    println("  Normalization factor: $factor")

    while (index < pcmData.size) {
        val sample = bytesToSample(pcmData, index)
        val normalizedSample = (sample.toDouble() * factor).toInt()
        val bytes = sampleToBytes(normalizedSample)

        pcmData[index] = bytes.first
        pcmData[index + 1] = bytes.second

        index += 2
    }

    return pcmData
}

fun printUsage() {
    println(
        """
        Usage:
        
        msupcm.main.kts
            Show this usage statement
        
        msupcm.main.kts (filename.json)
            Process all tracks in the given JSON file
            
        msupcm.main.kts (filename.json) (track number)
            Process the track in the given JSON file with the given track number 
            
        msupcm.main.kts (filename.json) (track number) -raw
            Process the track in the given JSON file with the given track number and skip the MSU PCM header 
        """.trimIndent()
    )
}

fun processTrack(filename: String, trackNumber: Int, raw: Boolean = false) {
    val mapper = jacksonObjectMapper()
    val msu = mapper.readValue<Msu>(File(filename))

    processTrack(msu, msu.tracks.find { it.trackNumber == trackNumber }!!, raw)
}

fun processTrack(msu: Msu, track: Track, raw: Boolean = false) {
    println("Processing #${track.trackNumber} - ${track.title}")

    val pcmData = convertToPcmData(track.filename)

    writeMsuPcm(msu, track, pcmData, raw)
}

fun processTracks(filename: String) {
    val mapper = jacksonObjectMapper()
    val msu = mapper.readValue<Msu>(File(filename))

    msu.tracks.forEach { processTrack(msu, it) }
}

/**
 * Converts the given 16-bit [sample] into its consituent bytes, in little-endian order.
 */
fun sampleToBytes(sample: Int) = sample.toByte() to (sample shr 8).toByte()

fun samplesToBytes(samples: Int) = samples * SAMPLE_BYTES * CHANNELS

fun trimPcmData(pcmData: ByteArray, samplesStart: Int, samplesEnd: Int?) =
    pcmData.copyOfRange(samplesToBytes(samplesStart), samplesEnd?.let { samplesToBytes(it) } ?: pcmData.size)

fun writeMsuPcm(msu: Msu, track: Track, pcmData: ByteArray, raw: Boolean) {
    val outputFilename = "${msu.outputPrefix}-${track.trackNumber}.pcm"

    File(outputFilename).outputStream().use {
        if (raw) {
            it.write(pcmData)
        } else {
            val (pcmData1, pcmData2) = if (track.loopPoint >= track.trimStart) {
                // Everything is in the right order already, so leave one section empty and add everything to the other
                // one.
                ByteArray(0) to trimPcmData(pcmData, track.trimStart, track.trimEnd)
            } else {
                // We're starting from a point inside what we want to loop, so flip the sections around so
                // [trimStart:timEnd] plays first and [loopPoint:trimStart] follows.
                trimPcmData(pcmData, track.trimStart, track.trimEnd) to
                        trimPcmData(pcmData, track.loopPoint, track.trimStart)
            }
            val loopPoint = if (track.loopPoint >= track.trimStart) {
                // If we're offsetting the start of the track, we have to adjust the loop point to account for that.
                track.loopPoint - track.trimStart
            } else {
                // We flipped the sections around above, so we can loop the entire file.
                0
            }

            // Write the magic number
            it.write(MSU_MAGIC.toByteArray())

            // Write the loop point (this function takes an Int and masks it to a Byte itself)
            it.write(loopPoint)
            it.write(loopPoint shr 8)
            it.write(loopPoint shr 16)
            it.write(loopPoint shr 24)

            // Normalize and write the audio data
            val normalizedPcmData = normalizePcmData(pcmData1 + pcmData2, track.normalization ?: msu.normalization)

            it.write(normalizedPcmData)
        }
    }
}

if (args.size == 1) {
    processTracks(args[0])
} else if (args.size == 2) {
    processTrack(args[0], args[1].toInt())
} else if (args.size == 3 && args[2] == "-raw"){
    processTrack(args[0], args[1].toInt(), true) // TODO: should ignore trims
} else {
    printUsage()
}
