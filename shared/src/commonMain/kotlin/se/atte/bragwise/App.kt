package se.atte.bragwise

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import se.atte.bragwise.theme.BragwiseTheme
import se.atte.bragwise.ui.nav.AppNav

@Composable
@Preview
fun App() {
    BragwiseTheme {
        AppNav()
    }
}
