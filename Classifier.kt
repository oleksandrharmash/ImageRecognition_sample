import android.graphics.Bitmap
import android.graphics.RectF

interface Classifier {

    val statString: String?

    fun recognizeImage(bitmap: Bitmap): List<Recognition>

    fun enableStatLogging(debug: Boolean)

    fun close()
}
