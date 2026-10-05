package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * ============================================================================
 * FACE VERIFIER — Get Verified live selfie challenge (PRD §34–§39)
 *
 * The user takes a FRESH selfie with the system camera (the "live camera
 * task"); this engine then verifies that at least ONE of their uploaded
 * profile photos matches the live face with ≥ 60% similarity.
 *
 * Pipeline (fully on-device, ML Kit + plain bitmap math — no new deps):
 *   1. Detect the largest face in the live selfie (ML Kit, ACCURATE mode,
 *      classification ON for the eyes-open liveness signal).
 *   2. Quality gates: a face must exist, be reasonably large and be captured
 *      with open eyes.
 *   3. For every uploaded profile photo (local content:// or Supabase CDN
 *      https URL): detect its largest face, crop both faces with margin,
 *      scale to 48×48 grayscale, brightness-normalize (zero mean / unit
 *      variance) and compare:
 *        - shift-tolerant full-resolution correlation (structure), and
 *        - 8×8 block-pooled correlation (low-frequency "face gestalt").
 *      score = 0.45 * full + 0.55 * pooled   …  best photo wins.
 *   4. verified = bestScore >= 0.60.
 *
 * Everything is defensive: decode failures, downloads timing out, ML Kit
 * being unavailable or a photo without a detectable face never throw — they
 * degrade to a clear `reason` on the failed result instead.
 * ============================================================================
 */
object FaceVerifier {

    /** Product spec: "any one of the uploaded images must match ≥ 60%". */
    const val MATCH_THRESHOLD = 0.60f

    private const val VECTOR_SIZE = 48
    private const val POOL_SIZE = 8
    private const val MAX_DECODE_DIM = 1000
    private const val MAX_DOWNLOAD_BYTES = 10L * 1024 * 1024
    private const val MIN_SELFIE_FACE_FRACTION = 0.10f
    private const val MIN_REFERENCE_FACE_PX = 40

    /** Outcome consumed by the UI (VerificationSheet + SparkViewModel). */
    data class Result(
        val verified: Boolean,
        /** Best similarity across all compared photos, 0f..1f. */
        val bestScore: Float,
        /** Same as bestScore, as a friendly 0..100 integer. */
        val bestScorePercent: Int,
        /** How many profile photos actually participated in the comparison. */
        val comparedPhotos: Int,
        /** Whether ML Kit found a face in the live selfie at all. */
        val faceDetected: Boolean,
        /** Human-readable failure explanation (null when verified). */
        val reason: String? = null
    )

    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /**
     * Runs the full live-selfie verification. Never throws.
     *
     * @param selfieUri content URI of the just-captured camera selfie
     * @param referencePhotoUris the user's uploaded profile photos
     * @param threshold similarity required against any one reference
     */
    suspend fun verifyLiveSelfie(
        context: Context,
        selfieUri: Uri,
        referencePhotoUris: List<String>,
        threshold: Float = MATCH_THRESHOLD
    ): Result = withContext(Dispatchers.IO) {
        if (referencePhotoUris.isEmpty()) {
            return@withContext failed(0f, 0, false,
                "Add at least one profile photo before starting the verification.")
        }

        // ---- 1. Load the live selfie + detect its face ------------------
        val selfieBitmap = loadBitmap(context, selfieUri)
        if (selfieBitmap == null) {
            return@withContext failed(0f, 0, false,
                "We couldn't read the captured selfie — please try again.")
        }
        val selfieFace = largestFace(InputImage.fromBitmap(selfieBitmap, 0))
        if (selfieFace == null) {
            return@withContext failed(0f, 0, false,
                "We couldn't find a face in your selfie. Face the camera directly and try again.")
        }

        // Quality gate: the face must fill a reasonable part of the frame.
        val minSide = minOf(selfieBitmap.width, selfieBitmap.height).toFloat()
        val box = selfieFace.boundingBox
        if (box.width() < minSide * MIN_SELFIE_FACE_FRACTION ||
            box.height() < minSide * MIN_SELFIE_FACE_FRACTION
        ) {
            return@withContext failed(0f, 0, true,
                "Your face is too far away — move closer to the camera and retake the selfie.")
        }

        // Liveness signal: both eyes should be open in a live capture.
        val leftEye = selfieFace.leftEyeOpenProbability
        val rightEye = selfieFace.rightEyeOpenProbability
        if (leftEye != null && rightEye != null &&
            (leftEye < 0.30f || rightEye < 0.30f)
        ) {
            return@withContext failed(0f, 0, true,
                "Keep your eyes open and look straight at the camera, then retake the selfie.")
        }

        val selfieVector = faceVector(selfieBitmap, box)
        if (!selfieBitmap.isRecycled) selfieBitmap.recycle()
        if (selfieVector == null) {
            return@withContext failed(0f, 0, true,
                "The captured selfie was too low quality — try again in better lighting.")
        }

        // ---- 2. Compare against every uploaded profile photo -------------
        var best = 0f
        var compared = 0
        for (photoUri in referencePhotoUris.take(3)) {
            val uri = runCatching { Uri.parse(photoUri) }.getOrNull() ?: continue
            if (uri == selfieUri) continue
            val bitmap = loadBitmap(context, uri) ?: continue
            val face = largestFace(InputImage.fromBitmap(bitmap, 0))
            if (face == null || face.boundingBox.width() < MIN_REFERENCE_FACE_PX) {
                if (!bitmap.isRecycled) bitmap.recycle()
                continue
            }
            val vector = faceVector(bitmap, face.boundingBox)
            if (vector != null) {
                compared += 1
                val score = similarity(selfieVector, vector)
                if (score > best) best = score
            }
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        if (compared == 0) {
            return@withContext failed(0f, 0, true,
                "We couldn't detect a clear face in your profile photos. " +
                        "Re-upload a clear, front-facing photo and try again.")
        }

        val verified = best >= threshold
        Result(
            verified = verified,
            bestScore = best,
            bestScorePercent = (best * 100f).roundToInt().coerceIn(0, 100),
            comparedPhotos = compared,
            faceDetected = true,
            reason = if (verified) null else
                "Your live selfie matched your profile photos at only " +
                        "${(best * 100f).roundToInt()}% — we need at least " +
                        "${(threshold * 100f).roundToInt()}%. Retake it in good " +
                        "lighting, facing the camera with a neutral expression."
        )
    }

    // =====================================================================
    // DETECTION
    // =====================================================================

    /** Largest detected face, or null. Never throws. */
    private suspend fun largestFace(image: InputImage): Face? = runCatching {
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
        )
        try {
            suspendCancellableCoroutine { cont ->
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        cont.resume(
                            faces.maxByOrNull {
                                it.boundingBox.width().toFloat() * it.boundingBox.height()
                            }
                        )
                    }
                    .addOnFailureListener { cont.resume(null) }
            }
        } finally {
            detector.close()
        }
    }.getOrNull()

    // =====================================================================
    // BITMAP LOADING
    // =====================================================================

    /**
     * Decodes a local (content/file) or remote (http/https) photo. The camera
     * and most gallery sources store rotation in JPEG EXIF — it is applied
     * here so face detection and crops always see an upright image.
     */
    private fun loadBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
        val bytes = when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                http.newCall(Request.Builder().url(uri.toString()).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val b = resp.body?.bytes() ?: return@runCatching null
                    if (b.isEmpty() || b.size > MAX_DOWNLOAD_BYTES) return@runCatching null
                    b
                }
            }
            else -> {
                val b = context.contentResolver.openInputStream(uri)
                    ?.use { stream -> stream.readBytes() }
                    ?: return@runCatching null
                if (b.isEmpty() || b.size > MAX_DOWNLOAD_BYTES) return@runCatching null
                b
            }
        }
        val decoded = decodeSampled(bytes) ?: return@runCatching null
        applyExifRotation(bytes, decoded)
    }.getOrNull()

    /** Rotates the bitmap when its EXIF orientation tag demands it. */
    private fun applyExifRotation(sourceBytes: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            java.io.ByteArrayInputStream(sourceBytes).use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val rotated = runCatching {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrNull() ?: return bitmap
        if (rotated !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return rotated
    }

    /** Bounds-first decode with inSampleSize so huge photos stay cheap. */
    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_DECODE_DIM ||
            bounds.outHeight / (sample * 2) >= MAX_DECODE_DIM
        ) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    // =====================================================================
    // FACE VECTORS + SIMILARITY
    // =====================================================================

    /**
     * Crop around the ML Kit face box (with margin for hair/chin), scale to
     * 48×48 grayscale and normalize to zero mean / unit variance. Returns
     * null when the crop is degenerate.
     */
    private fun faceVector(bitmap: Bitmap, box: Rect): FloatArray? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val marginX = box.width() * 0.20f
        val marginTop = box.height() * 0.35f
        val marginBottom = box.height() * 0.15f
        val left = (box.left - marginX).coerceAtLeast(0f).toInt()
        val top = (box.top - marginTop).coerceAtLeast(0f).toInt()
        val right = (box.right + marginX).coerceAtMost(w.toFloat()).toInt()
        val bottom = (box.bottom + marginBottom).coerceAtMost(h.toFloat()).toInt()
        val cropW = right - left
        val cropH = bottom - top
        if (cropW < 8 || cropH < 8) return null

        val crop = Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
        val scaled = Bitmap.createScaledBitmap(crop, VECTOR_SIZE, VECTOR_SIZE, true)
        val px = IntArray(VECTOR_SIZE * VECTOR_SIZE)
        scaled.getPixels(px, 0, VECTOR_SIZE, 0, 0, VECTOR_SIZE, VECTOR_SIZE)

        val v = FloatArray(px.size)
        var sum = 0f
        for (i in px.indices) {
            val p = px[i]
            val gray = 0.299f * ((p shr 16) and 0xFF) +
                    0.587f * ((p shr 8) and 0xFF) +
                    0.114f * (p and 0xFF)
            v[i] = gray
            sum += gray
        }
        val mean = sum / v.size
        var variance = 0f
        for (x in v) variance += (x - mean) * (x - mean)
        val std = sqrt(variance / v.size).coerceAtLeast(1f)
        for (i in v.indices) v[i] = (v[i] - mean) / std

        if (scaled !== crop && !scaled.isRecycled) scaled.recycle()
        if (!crop.isRecycled) crop.recycle()
        return v
    }

    /**
     * Blended similarity:
     *   0.45 × shift-tolerant correlation of the 48×48 normalized crops
     *          (best of 25 ±2px offsets — compensates ML Kit box jitter)
     * + 0.55 × correlation of the 8×8 block-pooled crops (overall gestalt —
     *          keeps the score stable across lighting/exposure differences).
     */
    private fun similarity(a: FloatArray, b: FloatArray): Float {
        val full = shiftTolerantCosine(a, b)
        val pooled = cosine(pool(a), pool(b))
        return (0.45f * full + 0.55f * pooled).coerceIn(0f, 1f)
    }

    /** Best cosine over ±2px offsets in both axes (25 evaluations). */
    private fun shiftTolerantCosine(a: FloatArray, b: FloatArray): Float {
        var best = -1f
        for (dy in -2..2) {
            for (dx in -2..2) {
                var dot = 0f
                var na = 0f
                var nb = 0f
                for (y in 0 until VECTOR_SIZE) {
                    val by = y + dy
                    if (by < 0 || by >= VECTOR_SIZE) continue
                    val byRow = by * VECTOR_SIZE
                    val aRow = y * VECTOR_SIZE
                    for (x in 0 until VECTOR_SIZE) {
                        val bx = x + dx
                        if (bx < 0 || bx >= VECTOR_SIZE) continue
                        val av = a[aRow + x]
                        val bv = b[byRow + bx]
                        dot += av * bv
                        na += av * av
                        nb += bv * bv
                    }
                }
                val d = sqrt(na) * sqrt(nb)
                if (d > 0f) {
                    val c = dot / d
                    if (c > best) best = c
                }
            }
        }
        return best.coerceIn(-1f, 1f)
    }

    /** Pearson correlation — both inputs are already zero-mean/unit-variance. */
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val d = sqrt(na) * sqrt(nb)
        return if (d <= 0f) 0f else (dot / d).coerceIn(-1f, 1f)
    }

    /** Mean-pools a 48×48 vector into an 8×8 one, then re-normalizes it. */
    private fun pool(v: FloatArray): FloatArray {
        val block = VECTOR_SIZE / POOL_SIZE
        val out = FloatArray(POOL_SIZE * POOL_SIZE)
        for (by in 0 until POOL_SIZE) {
            for (bx in 0 until POOL_SIZE) {
                var acc = 0f
                for (y in 0 until block) {
                    val row = (by * block + y) * VECTOR_SIZE + bx * block
                    for (x in 0 until block) acc += v[row + x]
                }
                out[by * POOL_SIZE + bx] = acc / (block * block)
            }
        }
        var mean = 0f
        for (x in out) mean += x
        mean /= out.size
        var variance = 0f
        for (x in out) variance += (x - mean) * (x - mean)
        val std = sqrt(variance / out.size).coerceAtLeast(1e-4f)
        for (i in out.indices) out[i] = (out[i] - mean) / std
        return out
    }

    private fun failed(best: Float, compared: Int, faceDetected: Boolean, reason: String) =
        Result(
            verified = false,
            bestScore = best,
            bestScorePercent = (best * 100f).roundToInt().coerceIn(0, 100),
            comparedPhotos = compared,
            faceDetected = faceDetected,
            reason = reason
        )
}
