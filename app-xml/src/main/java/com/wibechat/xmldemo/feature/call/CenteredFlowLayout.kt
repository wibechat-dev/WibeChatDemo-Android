package com.wibechat.xmldemo.feature.call

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.wibechat.xmldemo.ui.dp

/**
 * Lays children out in centred rows and wraps to a new row when one is full
 * (like Compose FlowRow with centred arrangement). The call controls use it
 * so they fit on narrow phones and in landscape.
 */
class CenteredFlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    private val hGap = 16.dp
    private val vGap = 16.dp

    /** Children per row, from the last measure. */
    private var rows: List<List<View>> = emptyList()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val visible = (0 until childCount).map(::getChildAt).filter { it.visibility != GONE }
        visible.forEach { measureChild(it, MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST), heightMeasureSpec) }

        val result = mutableListOf<MutableList<View>>()
        var rowWidth = 0
        visible.forEach { child ->
            val w = child.measuredWidth
            val row = result.lastOrNull()
            if (row == null || rowWidth + hGap + w > maxWidth) {
                result += mutableListOf(child)
                rowWidth = w
            } else {
                row += child
                rowWidth += hGap + w
            }
        }
        rows = result
        val height = rows.sumOf { row -> row.maxOf { it.measuredHeight } } + vGap * (rows.size - 1).coerceAtLeast(0)
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(height + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val innerWidth = r - l - paddingLeft - paddingRight
        var y = paddingTop
        rows.forEach { row ->
            val rowWidth = row.sumOf { it.measuredWidth } + hGap * (row.size - 1)
            var x = paddingLeft + (innerWidth - rowWidth) / 2
            val rowHeight = row.maxOf { it.measuredHeight }
            row.forEach { child ->
                child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                x += child.measuredWidth + hGap
            }
            y += rowHeight + vGap
        }
    }
}
