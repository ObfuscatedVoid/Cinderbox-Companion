package com.sdvsync.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

@Immutable
class SteppedCornerShape private constructor(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    constructor(corner: Dp) : this(CornerSize(corner), CornerSize(corner), CornerSize(corner), CornerSize(corner))

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ): CornerBasedShape = SteppedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline {
        val ltr = layoutDirection == LayoutDirection.Ltr
        val topLeft = if (ltr) topStart else topEnd
        val topRight = if (ltr) topEnd else topStart
        val bottomRight = if (ltr) bottomEnd else bottomStart
        val bottomLeft = if (ltr) bottomStart else bottomEnd
        return Outline.Generic(
            Path().apply {
                moveTo(topLeft, 0f)
                lineTo(size.width - topRight, 0f)
                lineTo(size.width - topRight, topRight)
                lineTo(size.width, topRight)
                lineTo(size.width, size.height - bottomRight)
                lineTo(size.width - bottomRight, size.height - bottomRight)
                lineTo(size.width - bottomRight, size.height)
                lineTo(bottomLeft, size.height)
                lineTo(bottomLeft, size.height - bottomLeft)
                lineTo(0f, size.height - bottomLeft)
                lineTo(0f, topLeft)
                lineTo(topLeft, topLeft)
                close()
            }
        )
    }
}
