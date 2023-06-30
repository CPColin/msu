#!/usr/bin/env /opt/msu/kotlinc/bin/kotlin

@file:DependsOn("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0")

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.io.FileWriter
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

val CHANNELS = 2

val MSU_MAGIC = "MSU1"

val SAMPLE_BYTES = 2

@JsonIgnoreProperties(ignoreUnknown = true)
data class Msu(
    /**
     * The name of the album that the music in this pack came from. Individual tracks may override this value by
     * specifying a [Track.album] value.
     */
    val album: String?,

    /**
     * Amplification that should be applied to tracks in this pack, in decibels or linear power. This value overrides
     * the [rmsTarget] value. Individual tracks may override this value by specifying either [Track.rmsTarget] or
     * [Track.amplification].
     */
    val amplification: Double?,

    /**
     * The original artist who composed the music in this pack. Individual tracks may override this value by specifying
     * a [Track.artist] value.
     */
    val artist: String?,

    /**
     * When specified, indicates this pack is the child of the pack with the given filename.
     */
    @JsonProperty("child_of")
    val childOf: String?,

    val game: String,

    @JsonProperty("output_prefix")
    val outputPrefix: String,

    /**
     * The author of this pack.
     */
    @JsonProperty("pack_author")
    val packAuthor: String?,

    /**
     * The name of this pack.
     */
    @JsonProperty("pack_name")
    val packName: String,

    @JsonProperty("pack_version")
    val packVersion: Int?,

    /**
     * Target root-mean-square value, in decibels or linear power. Individual tracks may override this value by
     * specifying either [Track.rmsTarget] or [Track.amplification].
     */
    @JsonProperty("rms_target")
    val rmsTarget: Double?,

    val tracks: List<TrackBase>,

    val url: String
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

@JsonSubTypes(
    JsonSubTypes.Type(Track::class),
    JsonSubTypes.Type(TrackCopy::class),
    JsonSubTypes.Type(TrackImport::class),
    JsonSubTypes.Type(TrackMix::class)
)
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
sealed interface TrackBase {
    /**
     * The name of the album that the music in this track came from. This value overrides [Msu.album].
     */
    val album: String?

    /**
     * Amplification that should be applied to this track, in decibels or linear power. This value overrides
     * [rmsTarget], [Msu.amplification], and [Msu.rmsTarget].
     */
    val amplification: Double?

    /**
     * The original artist who composed this track. This value overrides [Msu.artist].
     */
    val artist: String?

    /**
     * When specified, indicates that this track should fade in (linearly) at the start for the given number of samples.
     */
    val fadeIn: Int?

    /**
     * When specified, indicates that this track should fade out (linearly) at the end for the given number of samples.
     */
    val fadeOut: Int?

    /**
     * The key that should be used for this track in the track list.
     */
    val key: String?

    val loopPoint: Int?

    /**
     * The [Msu] pack this track belongs to.
     */
    val msu: Msu

    /**
     * When specified, indicates that this track should end with the given number of silent samples. The [loopPoint] may
     * be placed within these silent samples.
     */
    val padEnd: Int?

    /**
     * When specified, indicates that this track should start with the given number of silent samples. The [loopPoint]
     * is relative to the start of the _unpadded_ track and thus may not be placed within these silent samples.
     */
    val padStart: Int?

    /**
     * Target root-mean-square normalization value, in decibels or linear power. Overrides both [Msu.rmsTarget] and
     * [Msu.amplification], when present.
     */
    val rmsTarget: Double?

    val title: String?

    val trackNumber: Int?

    val trimEnd: Int?

    val trimStart: Int
}

data class Track(
    override val album: String?,

    override val amplification: Double?,

    override val artist: String?,

    @JsonProperty("fade_in")
    override val fadeIn: Int?,

    @JsonProperty("fade_out")
    override val fadeOut: Int?,

    @JsonProperty("file")
    val filename: String,

    override val key: String?,

    @JsonProperty("loop")
    override val loopPoint: Int?,

    @JsonProperty("pad_end")
    override val padEnd: Int?,

    @JsonProperty("pad_start")
    override val padStart: Int?,

    @JsonProperty("rms_target")
    override val rmsTarget: Double?,

    override val title: String?,

    @JsonProperty("track_number")
    override val trackNumber: Int?,

    @JsonProperty("trim_end")
    override val trimEnd: Int?,

    @JsonProperty("trim_start")
    override val trimStart: Int
) : TrackBase {
    override lateinit var msu: Msu
}

data class TrackCopy(
    @JsonProperty("copy_of")
    val copyOf: Int,

    override val key: String,

    @JsonProperty("track_number")
    override val trackNumber: Int
) : TrackBase {
    lateinit var track: TrackBase

    override val album get() = track.album
    override val amplification get() = track.amplification
    override val artist get() = track.artist
    override val fadeIn get() = track.fadeIn
    override val fadeOut get() = track.fadeOut
    override val loopPoint get() = track.loopPoint
    override val msu get() = track.msu
    override val padEnd get() = track.padEnd
    override val padStart get() = track.padStart
    override val rmsTarget get() = track.rmsTarget
    override val title get() = track.title
    override val trimEnd get() = track.trimEnd
    override val trimStart get() = track.trimStart
}

data class TrackImport(
    /**
     * Indicates which parent JSON file and track number, separated by `#` to import this track from.
     */
    @JsonProperty("import_from")
    val importFrom: String,

    @JsonProperty("track_number")
    override val trackNumber: Int
) : TrackBase {
    lateinit var track: TrackBase

    override val album get() = track.album ?: track.msu.album
    override val amplification get() = track.amplification
    override val artist get() = track.artist ?: track.msu.artist
    override val fadeIn get() = track.fadeIn
    override val fadeOut get() = track.fadeOut
    override val key get() = track.key
    override val loopPoint get() = track.loopPoint
    override val msu get() = track.msu
    override val padEnd get() = track.padEnd
    override val padStart get() = track.padStart
    override val rmsTarget get() = track.rmsTarget
    override val title get() = track.title
    override val trimEnd get() = track.trimEnd
    override val trimStart get() = track.trimStart
}

data class TrackMix(
    override val album: String?,

    override val amplification: Double?,

    override val artist: String?,

    @JsonProperty("fade_in")
    override val fadeIn: Int?,

    @JsonProperty("fade_out")
    override val fadeOut: Int?,

    override val key: String?,

    @JsonProperty("loop")
    override val loopPoint: Int?,

    @JsonProperty("pad_end")
    override val padEnd: Int?,

    @JsonProperty("pad_start")
    override val padStart: Int?,

    @JsonProperty("rms_target")
    override val rmsTarget: Double?,

    @JsonProperty("sub_tracks")
    val subTracks: List<TrackBase>,

    override val title: String?,

    @JsonProperty("track_number")
    override val trackNumber: Int?,

    @JsonProperty("trim_end")
    override val trimEnd: Int?,

    @JsonProperty("trim_start")
    override val trimStart: Int
) : TrackBase {
    override lateinit var msu: Msu
}

fun byteCountToSampleCount(byteCount: Int) = byteCount / SAMPLE_BYTES / CHANNELS

/**
 * Converts the bytes starting at the given [index] of the given [pcmData] into a 16-bit, little-endian sample.
 * Returns zero if the index is out-of-range.
 */
fun bytesToSample(pcmData: ByteArray, index: Int) =
    if (index >= pcmData.size) {
        0
    } else {
        (pcmData[index + 1].toInt() shl 8) or
                (pcmData[index].toInt() and 0xff)
    }

/**
 * Computes and returns the amplification factor, in linear power, appropriate for the given [pcmData] using the values
 * from the given [Track] and its [Msu] pack, choosing the first available value, with the following priority:
 *
 * - [TrackBase.amplification]
 * - [TrackBase.rmsTarget]
 * - [Msu.amplification]
 * - [Msu.rmsTarget]
 * - `1.0` (no change from input)
 *
 * If one of the `rmsTarget` values is chosen, the current RMS value of the [pcmData] is computed and the returned
 * amplification value is what's needed to hit the chosen RMS target.
 */
fun computeAmplification(pcmData: ByteArray, track: TrackBase) =
    when {
        track.amplification != null -> track.amplification!!.toLinear()
        track.rmsTarget != null -> track.rmsTarget!!.toLinear() / computeRootMeanSquare(pcmData)
        track.msu.amplification != null -> track.msu.amplification!!.toLinear()
        track.msu.rmsTarget != null -> track.msu.rmsTarget!!.toLinear() / computeRootMeanSquare(pcmData)
        else -> 1.0
    }

/**
 * Computes and returns the fade factor resulting from the given parameters. The factor fades in and out linearly
 * between `0.0` and `1.0` when close to the ends of the track and a fade is specified. Note that it's possible for both
 * fades to overlap, in which case both fades are applied successively.
 */
fun computeFade(fadeIn: Int?, fadeOut: Int?, sampleIndex: Int, totalSampleCount: Int): Double {
    // Start with 1.0 to represent no fade being applied.
    var fade = 1.0

    if (fadeIn is Int && sampleIndex < fadeIn) {
        // We are within fadeIn samples from the start of the track, so fade in linearly.
        fade = fade * sampleIndex / fadeIn
    }

    if (fadeOut is Int && sampleIndex > (totalSampleCount - fadeOut)) {
        // We are within fadeOut samples from the end of the track, so fade out linearly.
        fade = fade * (totalSampleCount - sampleIndex) / fadeOut
    }

    return fade
}

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
        .let { it / byteCountToSampleCount(pcmData.size) }
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

    return process.inputStream.readBytes()
}

/**
 * Converts this value to decibels, if it isn't in decibels already (as assumed when the value is negative).
 */
fun Double.toDecibels(): Double = if (this < 0) { this } else { 20.0 * log10(this) }

/**
 * Converts this value to linear power, if it isn't linear already (as assumed when the value is nonnegative).
 */
fun Double.toLinear(): Double = if (this >= 0) { this } else { 10.0.pow(this / 20.0) }

/**
 * Loads and returns the [Msu] with the given [filename], filling in any [TrackCopy] values with their source [Track]
 * values.
 */
fun loadMsu(filename: String): Msu {
    val mapper = jacksonObjectMapper()
    val msu = mapper.readValue<Msu>(File(filename))
    val parentMsus = mutableMapOf<String, Msu>()

    msu.tracks.forEach { track ->
        when (track) {
            is Track -> track.msu = msu
            is TrackCopy -> track.track = msu.tracks.find { it.trackNumber == track.copyOf }!!
            is TrackImport -> {
                val (parentFilename, parentTrackNumber) = track.importFrom.split("#")
                val parentMsu = parentMsus.getOrPut(parentFilename) { loadMsu(parentFilename) }

                track.track = parentMsu.tracks.find { it.trackNumber.toString() == parentTrackNumber }!!
            }
            is TrackMix -> {
                track.msu = msu
                // Assuming sub-tracks don't copy or import, for now.
                track.subTracks.forEach { (it as Track).msu = msu }
            }
            else -> error("TrackBase is sealed, so we shouldn't need this")
        }
    }

    return msu
}

/**
 * Performs mixing on the given [pcmData] by converting each sample to a [Double], applying requested processing,
 * converting the sample back to a 16-bit value, and writing it back into the array. We're doing the mixing in floating
 * point because repeated conversion back into [Int] values could lose precision.
 */
fun mixPcmData(pcmData: ByteArray, track: TrackBase) {
    val amplification = computeAmplification(pcmData, track)
    var index = 0
    val totalSampleCount = byteCountToSampleCount(pcmData.size)

    println("  Amplification: $amplification")

    while (index < pcmData.size) {
        val sampleIndex = byteCountToSampleCount(index)
        val sample = bytesToSample(pcmData, index) *
                amplification *
                computeFade(track.fadeIn, track.fadeOut, sampleIndex, totalSampleCount)
        val bytes = sampleToBytes(sample.toInt())

        pcmData[index] = bytes.first
        pcmData[index + 1] = bytes.second

        index += SAMPLE_BYTES
    }
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
    val msu = loadMsu(filename)

    processTrack(msu, msu.tracks.find { it.trackNumber == trackNumber }!!, raw)
}

fun processTrack(msu: Msu, track: TrackBase, raw: Boolean = false) {
    println("Processing #${track.trackNumber} - ${track.key}")

    val rawPcmData = track.loadPcmData()
    val (pcmData, loopPoint) = renderTrack(track, rawPcmData)

    writeMsuPcm(msu, track, pcmData, loopPoint, raw)
}

fun processTracks(filename: String) {
    val msu = loadMsu(filename)

    writeMsuFile(msu)
    writeMsuTrackList(msu)

    msu.tracks.forEach { processTrack(msu, it) }
}

fun renderSubTracks(subTracks: List<TrackBase>): ByteArray {
    val pcmDatas = subTracks.map { renderTrack(it, it.loadPcmData()).first }
    val pcmData = ByteArray(pcmDatas.maxOf { it.size })

    var index = 0

    while (index < pcmData.size) {
        val sample = pcmDatas.sumOf { bytesToSample(it, index) }
        val bytes = sampleToBytes(sample)

        pcmData[index] = bytes.first
        pcmData[index + 1] = bytes.second

        index += SAMPLE_BYTES
    }

    return pcmData
}

fun renderTrack(track: TrackBase, rawPcmData: ByteArray): Pair<ByteArray, Int> {
    val padEnd = track.padEnd ?: 0
    val padStart = track.padStart ?: 0
    val pcmData = if (track.loopPoint == null || track.loopPoint!! >= track.trimStart) {
        // Everything is in the right order already.
        trimPcmData(rawPcmData, track.trimStart, track.trimEnd)
    } else {
        // We're starting from a point inside what we want to loop, so flip the sections around so
        // [trimStart:timEnd] plays first and [loopPoint:trimStart] follows.
        trimPcmData(rawPcmData, track.trimStart, track.trimEnd) +
                trimPcmData(rawPcmData, track.loopPoint!!, track.trimStart)
    }
    val loopPoint = if (track.loopPoint == null) {
        // We didn't specify a loop point, so loop the whole (trimmed) file.
        0
    } else if (track.loopPoint!! >= track.trimStart) {
        // If we're offsetting the start of the track, we have to adjust the loop point to account for that.
        track.loopPoint!! - track.trimStart + padStart
    } else {
        // We flipped the sections around above, so we can loop the entire file.
        0 + padStart
    }

    require(loopPoint < padStart + byteCountToSampleCount(pcmData.size) + padEnd) {
        "Loop point is at or beyond last sample of audio. Resulting file would crash emulators!"
    }

    mixPcmData(pcmData, track)

    val padEndBytes = sampleCountToByteCount(padEnd)
    val padStartBytes = sampleCountToByteCount(padStart)
    val paddedPcmData = ByteArray(padStartBytes + pcmData.size + padEndBytes)

    pcmData.copyInto(paddedPcmData, padStartBytes)

    return paddedPcmData to loopPoint
}

fun sampleCountToByteCount(sampleCount: Int) = sampleCount * SAMPLE_BYTES * CHANNELS

/**
 * Converts the given 16-bit [sample] into its constituent bytes, in little-endian order.
 */
fun sampleToBytes(sample: Int) = sample.toByte() to (sample shr 8).toByte()

/**
 * Loads the PCM data for this track, be it from the source file, the copied or imported track, or from mixing the
 * sub-tracks of this track.
 *
 * This would be a member of [TrackBase], but that makes Jackson get very confused about the subtypes, for some reason.
 * I'm sick of trying to get Jackson to play along when I'd rather bang my head against *actual* functionality, so hell
 * with it.
 */
fun TrackBase.loadPcmData(): ByteArray =
    when (this) {
        is Track -> convertToPcmData(this.filename)
        is TrackCopy -> this.track.loadPcmData()
        is TrackImport -> this.track.loadPcmData()
        is TrackMix -> renderSubTracks(this.subTracks)
        else -> error("TrackBase is sealed, so we shouldn't need this")
    }

/**
 * Returns the source of the audio data for this track.
 *
 * This would also be part of [TrackBase], but would likely lead to the same Jackson problems.
 */
fun TrackBase.source(): String =
    when (this) {
        is Track -> this.filename
        is TrackCopy -> this.track.source()
        is TrackImport -> this.track.source()
        is TrackMix -> this.subTracks.joinToString(", ") { it.source() }
        else -> error("TrackBase is sealed, so we shouldn't need this")
    }

fun trimPcmData(pcmData: ByteArray, sampleCountStart: Int, sampleCountEnd: Int?) =
    pcmData.copyOfRange(
        sampleCountToByteCount(sampleCountStart),
        sampleCountEnd?.let { sampleCountToByteCount(it) } ?: pcmData.size
    )

/**
 * Creates the empty `.msu` file necessary for emulators to recognize this as an MSU pack. Also creates containing
 * directories, if the [output prefix][Msu.outputPrefix] names any.
 */
fun writeMsuFile(msu: Msu) {
    val file = File(msu.outputPrefix + ".msu")

    file.parentFile?.mkdirs()
    file.createNewFile()
}

fun writeMsuPcm(msu: Msu, track: TrackBase, pcmData: ByteArray, loopPoint: Int, raw: Boolean) {
    val outputFilename = "${msu.outputPrefix}-${track.trackNumber}.pcm"

    File(outputFilename).outputStream().use {
        if (raw) {
            it.write(pcmData)
        } else {
            // Write the magic number
            it.write(MSU_MAGIC.toByteArray())

            // Write the loop point (this function takes an Int and masks it to a Byte itself)
            it.write(loopPoint)
            it.write(loopPoint shr 8)
            it.write(loopPoint shr 16)
            it.write(loopPoint shr 24)

            // Write the processed audio data
            it.write(pcmData)
        }
    }
}

fun writeMsuTrackList(msu: Msu) {
    FileWriter(msu.outputPrefix + ".yml").use { file ->
        file.write("pack_name: ${msu.packName}\n")
        file.write("pack_author: ${msu.packAuthor}\n")
        msu.packVersion?.let { file.write("pack_version: $it\n") }
        msu.artist?.let { file.write("artist: $it\n") }
        msu.album?.let { file.write("album: $it\n") }
        file.write("tracks:\n")
        msu.tracks.forEach { track ->
            file.write("  ${track.key}:\n")
            file.write("    name: ${track.title ?: track.source()}\n")
            if (track.artist is String && track.artist != msu.artist) {
                file.write("    artist: ${track.artist}\n")
            }
            if (track.album is String && track.album != msu.album) {
                file.write("    album: ${track.album}\n")
            }
        }
    }
}

if (args.size == 1) {
    processTracks(args[0])
} else if (args.size == 2) {
    processTrack(args[0], args[1].toInt())
} else if (args.size == 3 && args[2] == "-raw"){
    processTrack(args[0], args[1].toInt(), true)
} else {
    printUsage()
}
