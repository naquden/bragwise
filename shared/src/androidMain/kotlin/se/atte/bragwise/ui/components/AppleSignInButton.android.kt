package se.atte.bragwise.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.apple_logo
import bragwise.shared.generated.resources.auth_apple
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * `supportsAppleSignIn` is false on Android, so this is never shown — it
 * exists only so the `expect` in commonMain compiles on every target.
 */
@Composable
actual fun AppleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    isDark: Boolean,
) {
    val containerColor = if (isDark) Color.White else Color.Black
    val contentColor = if (isDark) Color.Black else Color.White

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.38f),
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size(20.dp)) {
                Image(painter = painterResource(Res.drawable.apple_logo), contentDescription = null)
            }
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(Res.string.auth_apple),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}
