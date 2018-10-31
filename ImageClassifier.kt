import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.Trace
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayList
import java.util.Comparator
import java.util.PriorityQueue
import java.util.Vector
import org.tensorflow.contrib.android.TensorFlowInferenceInterface

class ImageClassifier : Classifier {

    // region  Config values
    internal var inputName: String? = null
    internal var outputName: String? = null
    internal var inputSize: Int = 0
    internal var imageMean: Int = 0
    internal var imageStd: Float = 0.toFloat()
    // endregion

    // region Pre-allocated buffers
    internal val labels = Vector<String>()
    internal var intValues: IntArray? = null
    internal var floatValues: FloatArray? = null
    internal var outputs: FloatArray? = null
    var outputNames: Array<String>? = null
    // endregion

    private var logStats = false

    var inferenceInterface: TensorFlowInferenceInterface? = null

    override val statString: String?
        get() = inferenceInterface?.statString

    override fun recognizeImage(bitmap: Bitmap): List<Classifier.Recognition> {
        Trace.beginSection("recognizeImage")
        Trace.beginSection("preprocessBitmap")

        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        intValues?.let {
            for (i in it.indices) {
                val `val` = it[i]
                floatValues?.set(i * 3 + 0, ((`val` shr 16 and 0xFF) - imageMean) / imageStd)
                floatValues?.set(i * 3 + 1, ((`val` shr 8 and 0xFF) - imageMean) / imageStd)
                floatValues?.set(i * 3 + 2, ((`val` and 0xFF) - imageMean) / imageStd)
            }
        }
        Trace.endSection()

        Trace.beginSection("feed")
        inferenceInterface?.feed(inputName, floatValues, 1, inputSize.toLong(), inputSize.toLong(), 3)
        Trace.endSection()

        Trace.beginSection("run")
        inferenceInterface?.run(outputNames, logStats)
        Trace.endSection()

        Trace.beginSection("fetch")
        inferenceInterface?.fetch(outputName, outputs)
        Trace.endSection()

        val priorityQueue = PriorityQueue(
                3,
                Comparator<Classifier.Recognition> { lhs, rhs ->
                    java.lang.Float.compare(rhs.confidence ?: 1f, lhs.confidence ?: 1f)
                })
        outputs?.let { outputs ->
            outputs.indices
                    .filter { outputs[it] > THRESHOLD }
                    .mapTo(priorityQueue) {
                        Classifier.Recognition(
                                "" + it, if (labels.size > it) labels[it] else "unknown", outputs[it], null)
                    }
        }
        val recognitions = ArrayList<Classifier.Recognition>()
        val recognitionsSize = Math.min(priorityQueue.size, MAX_RESULTS)
        (0 until recognitionsSize).forEach { recognitions.add(priorityQueue.poll()) }

        Trace.endSection()
        return recognitions
    }

    override fun enableStatLogging(debug: Boolean) {
        this.logStats = debug
    }

    override fun close() {
        inferenceInterface?.close()
    }

}

private const val MAX_RESULTS = 3
private const val THRESHOLD = 0.1f
private const val ASSET_FILE_PREFIX = "file:///android_asset/"

fun create(
        assetManager: AssetManager,
        modelFilename: String,
        labelFilename: String,
        inputSize: Int,
        imageMean: Int,
        imageStd: Float,
        inputName: String,
        outputName: String): Classifier {
    val classifier = ImageClassifier()
    classifier.inputName = inputName
    classifier.outputName = outputName

    val actualFilename = labelFilename.split(ASSET_FILE_PREFIX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
    try {
        val reader = BufferedReader(InputStreamReader(assetManager.open(actualFilename)))
        var line: String? = null
        while ({ line = reader.readLine(); line }() != null) classifier.labels.add(line)
        reader.close()
    } catch (e: IOException) {
        throw RuntimeException("Problem reading label file!", e)
    }

    classifier.inferenceInterface = TensorFlowInferenceInterface(assetManager, modelFilename)

    val operation = classifier.inferenceInterface?.graphOperation(outputName)
    val numClasses = operation?.output<Any>(0)?.shape()?.size(1)?.toInt()

    classifier.inputSize = inputSize
    classifier.imageMean = imageMean
    classifier.imageStd = imageStd

    classifier.outputNames = arrayOf(outputName)
    classifier.intValues = IntArray(inputSize * inputSize)
    classifier.floatValues = FloatArray(inputSize * inputSize * 3)
    classifier.outputs = FloatArray(numClasses ?: 0)

    return classifier
}
