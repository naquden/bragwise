package se.atte.bragwise.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val WideBreakpoint = 600.dp
val FormMaxWidth = 600.dp

@Composable
fun windowWidthDp(): Dp = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.width.toDp()
}

@Composable
fun isWideScreen(): Boolean = windowWidthDp() >= WideBreakpoint

@Composable
fun listColumns(): Int {
    val w = windowWidthDp()
    return if (w < WideBreakpoint) 1 else (w / 360.dp).toInt().coerceIn(2, 3)
}

@Composable
fun CenteredMaxWidth(
    modifier: Modifier = Modifier,
    maxWidth: Dp = FormMaxWidth,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxSize()
                .then(modifier),
        ) {
            content()
        }
    }
}
