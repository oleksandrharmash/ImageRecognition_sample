import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

import org.tensorflow.demo.Classifier.Recognition

private const val TEXT_SIZE_DIP = 24f

class RecognitionScoreView(context: Context, set: AttributeSet) : View(context, set), ResultsView {

    // region Variable
    private var results: List<Recognition>? = null
    private val textSizePx: Float = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics)
    private val foregroundPaint: Paint = Paint()
    private val backgroundPaint: Paint = Paint()
    // endregion

    // region Initial
    init {
        foregroundPaint.textSize = textSizePx
        backgroundPaint.color = -0x33bd7a0c
    }
    //endregion


    override fun setResults(results: List<Recognition>) {
        this.results = results
        postInvalidate()
    }

    public override fun onDraw(canvas: Canvas) {
        val x = 10
        var y = (foregroundPaint.textSize * 1.5f).toInt()

        canvas.drawPaint(backgroundPaint)

        results?.let { results ->
            for (recognition in results) {
                canvas.drawText("${recognition.title} : ${recognition.confidence}", x.toFloat(), y.toFloat(), foregroundPaint)
                y += (foregroundPaint.textSize * 1.5f).toInt()
            }
        }
    }
}
