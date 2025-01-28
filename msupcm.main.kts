#!/usr/bin/env kotlinc/bin/kotlin

@file:DependsOn("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0")

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
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
    val packName: String?,

    @JsonProperty("pack_version")
    val packVersion: Int?,

    /**
     * Target root-mean-square value, in decibels or linear power. Individual tracks may override this value by
     * specifying either [Track.rmsTarget] or [Track.amplification].
     */
    @JsonProperty("rms_target")
    val rmsTarget: Double?,

    @JsonProperty("tracks")
    val trackInfos: List<TrackInfo>,

    /**
     * The type of this pack, which will be copied to the YAML file, to make it easier to determine which game(s) the
     * pack is meant for, without resorting to guessing based on the presence of certain track numbers.
     *
     * Typical values include:
     *
     * - The Legend of Zelda: A Link to the Past
     * - Super Metroid
     * - Super Metroid / A Link to the Past Combination Randomizer
     *
     * See also: https://github.com/MattEqualsCoder/MSURandomizer/tree/main/Docs/YamlTemplates
     */
    val type: String,

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

data class TrackInfo(
    /**
     * The name of the album that the music in this track came from. This value overrides [Msu.album].
     */
    @JsonProperty("album")
    val album: String?,

    /**
     * Amplification that should be applied to this track, in decibels or linear power. This value overrides
     * [rmsTarget], [Msu.amplification], and [Msu.rmsTarget].
     */
    @JsonProperty("amplification")
    val amplification: Double?,

    /**
     * The original artist who composed this track. This value overrides [Msu.artist].
     */
    @JsonProperty("artist")
    val artist: String?,

    /**
     * When specified, indicates that this track should fade in (linearly) at the start for the given number of samples.
     */
    @JsonProperty("fade_in")
    val fadeIn: Int?,

    /**
     * When specified, indicates that this track should fade out (linearly) at the end for the given number of samples.
     */
    @JsonProperty("fade_out")
    val fadeOut: Int?,

    /**
     * When specified, indicates that the source audio for this track should come from a file with the given name. This
     * value is ignored when either [inheritFrom] or [subTracks] is specified.
     */
    @JsonProperty("file")
    val filename: String?,

    /**
     * When specified, indicates which parent track to inherit property values from. An integer value here identifies
     * another track in this MSU. A string value separated by `#` indicates another JSON file and the track within it.
     */
    @JsonProperty("inherit_from")
    @JsonAlias("copy_of", "import_from")
    val inheritFrom: String?,

    /**
     * The key that should be used for this track in the track list.
     */
    @JsonProperty("key")
    val key: String?,

    @JsonProperty("loop")
    val loopPoint: Int?,

    /**
     * When specified, indicates that this track should end with the given number of silent samples. The [loopPoint] may
     * be placed within these silent samples.
     */
    @JsonProperty("pad_end")
    val padEnd: Int?,

    /**
     * When specified, indicates that this track should start with the given number of silent samples. The [loopPoint]
     * is relative to the start of the _unpadded_ track and thus may not be placed within these silent samples.
     */
    @JsonProperty("pad_start")
    val padStart: Int?,

    /**
     * Target root-mean-square normalization value, in decibels or linear power. Overrides both [Msu.rmsTarget] and
     * [Msu.amplification], when present.
     */
    @JsonProperty("rms_target")
    val rmsTarget: Double?,

    @JsonProperty("sub_tracks")
    val subTracks: List<TrackInfo>?,

    @JsonProperty("title")
    val title: String?,

    @JsonProperty("track_number")
    val trackNumber: Int?,

    @JsonProperty("trim_end")
    val trimEnd: Int?,

    @JsonProperty("trim_start")
    val trimStart: Int?,
)

class Track(val msu: Msu, trackInfo: TrackInfo) {
    val album: String? by lazy { trackInfo.album ?: parentTrack?.album ?: msu.album ?: parentTrack?.msu?.album }
    val amplification: Double? by lazy { trackInfo.amplification ?: parentTrack?.amplification }
    val artist: String? by lazy { trackInfo.artist ?: parentTrack?.artist ?: msu.artist ?: parentTrack?.msu?.artist }
    val fadeIn: Int? by lazy { trackInfo.fadeIn ?: parentTrack?.fadeIn }
    val fadeOut: Int? by lazy { trackInfo.fadeOut ?: parentTrack?.fadeOut }
    private val filename: String? by lazy { trackInfo.filename ?: parentTrack?.filename }
    val key: String? by lazy { trackInfo.key ?: parentTrack?.key }
    val loopPoint: Int? by lazy { trackInfo.loopPoint ?: parentTrack?.loopPoint }
    val padEnd: Int? by lazy { trackInfo.padEnd ?: parentTrack?.padEnd }
    val padStart: Int? by lazy { trackInfo.padStart ?: parentTrack?.padStart }
    private val parentTrack: Track? by lazy {
        if (trackInfo.inheritFrom == null) {
            null
        } else if (trackInfo.inheritFrom.contains("#")) {
            val (parentFilename, parentTrackNumber) = trackInfo.inheritFrom.split("#")
            val parentMsu = loadMsu(parentFilename)

            parentMsu.tracks.find { it.trackNumber.toString() == parentTrackNumber }
        } else {
            msu.tracks.find { it.trackNumber.toString() == trackInfo.inheritFrom }
        }
    }
    val rmsTarget: Double? by lazy { trackInfo.rmsTarget ?: parentTrack?.rmsTarget }
    private val subTracks: List<Track>? by lazy {
        (trackInfo.subTracks?.map { Track(msu, it) } ?: parentTrack?.subTracks)
    }
    val title: String? by lazy { trackInfo.title ?: parentTrack?.title }
    val trackNumber: Int? by lazy { trackInfo.trackNumber ?: parentTrack?.trackNumber }
    val trimEnd: Int? by lazy { trackInfo.trimEnd ?: parentTrack?.trimEnd }
    val trimStart: Int by lazy { trackInfo.trimStart ?: parentTrack?.trimStart ?: 0 }

    /**
     * Loads the PCM data for this track, be it from the source file, the parent track, or from mixing the sub-tracks of
     * this track.
     */
    fun loadPcmData(): ByteArray =
        subTracks?.let(::renderSubTracks)
            ?: parentTrack?.loadPcmData()
            ?: convertToPcmData(filename!!)

    /**
     * Returns the source of the audio data for this track.
     */
    fun source(): String =
        subTracks?.let { it.joinToString(", ") { track -> track.source() } }
            ?: parentTrack?.source()
            ?: filename!!
}

val msuCache = mutableMapOf<String, Msu>()

val trackCache = mutableMapOf<Msu, List<Track>>()

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
 * - [Track.amplification]
 * - [Track.rmsTarget]
 * - [Msu.amplification]
 * - [Msu.rmsTarget]
 * - `1.0` (no change from input)
 *
 * If one of the `rmsTarget` values is chosen, the current RMS value of the [pcmData] is computed and the returned
 * amplification value is what's needed to hit the chosen RMS target.
 */
fun computeAmplification(pcmData: ByteArray, track: Track) =
    when {
        track.amplification != null -> track.amplification!!.toLinear()
        track.rmsTarget != null -> track.rmsTarget!!.toLinear() / computeRootMeanSquare(pcmData)
        track.msu.amplification != null -> track.msu.amplification.toLinear()
        track.msu.rmsTarget != null -> track.msu.rmsTarget.toLinear() / computeRootMeanSquare(pcmData)
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
 * Loads and returns the [Msu] with the given [filename].
 */
fun loadMsu(filename: String): Msu {
    return msuCache.getOrPut(filename) {
        val msu = jacksonObjectMapper().readValue<Msu>(File(filename))

        trackCache[msu] = msu.trackInfos.map { Track(msu, it) }

        msu
    }
}

/**
 * Performs mixing on the given [pcmData] by converting each sample to a [Double], applying requested processing,
 * converting the sample back to a 16-bit value, and writing it back into the array. We're doing the mixing in floating
 * point because repeated conversion back into [Int] values could lose precision.
 */
fun mixPcmData(pcmData: ByteArray, track: Track) {
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

val Msu.tracks
    get() = trackCache[this] ?: error("Tracks were not loaded yet for MSU: $packName")

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

fun processTrack(msu: Msu, track: Track, raw: Boolean = false) {
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

fun renderSubTracks(subTracks: List<Track>): ByteArray {
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

fun renderTrack(track: Track, rawPcmData: ByteArray): Pair<ByteArray, Int> {
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

fun trimPcmData(pcmData: ByteArray, sampleCountStart: Int, sampleCountEnd: Int?) =
    pcmData.copyOfRange(
        sampleCountToByteCount(sampleCountStart),
        sampleCountEnd?.let { sampleCountToByteCount(it) } ?: pcmData.size
    )

/**
 * Wraps this string in quotation marks and escapes any existing quotation marks, for YAML.
 */
fun String.wrap() = "\"${this.replace("\"", "\\\"")}\""

/**
 * Creates the empty `.msu` file necessary for emulators to recognize this as an MSU pack. Also creates containing
 * directories, if the [output prefix][Msu.outputPrefix] names any.
 */
fun writeMsuFile(msu: Msu) {
    val file = File(msu.outputPrefix + ".msu")

    file.parentFile?.mkdirs()
    file.createNewFile()
}

fun writeMsuPcm(msu: Msu, track: Track, pcmData: ByteArray, loopPoint: Int, raw: Boolean) {
    val outputFile = File("${msu.outputPrefix}-${track.trackNumber}.pcm")

    outputFile.parentFile?.mkdirs()

    outputFile.outputStream().use {
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
        msu.packName?.let { file.write("pack_name: ${it.wrap()}\n") }
        msu.packAuthor?.let { file.write("pack_author: ${it.wrap()}\n") }
        msu.packVersion?.let { file.write("pack_version: $it\n") }
        file.write("msu_type: ${msu.type.wrap()}\n")
        msu.artist?.let { file.write("artist: ${it.wrap()}\n") }
        msu.album?.let { file.write("album: ${it.wrap()}\n") }
        file.write("tracks:\n")
        msu.tracks.forEach { track ->
            file.write("  ${track.key}: # ${track.trackNumber}\n")
            file.write("    name: ${track.title?.wrap() ?: track.source().wrap()}\n")
            track.artist?.takeIf { it != msu.artist }?.let { file.write("    artist: ${it.wrap()}\n") }
            track.album?.takeIf { it != msu.album }?.let { file.write("    album: ${it.wrap()}\n") }
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
