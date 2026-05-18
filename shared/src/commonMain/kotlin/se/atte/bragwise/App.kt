package se.atte.bragwise

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import se.atte.bragwise.theme.BragwiseTheme
import se.atte.bragwise.ui.nav.AppDeps
import se.atte.bragwise.ui.nav.AppNav

@Composable
@Preview
fun App(deps: AppDeps = remember { AppDeps.stub() }) {
    BragwiseTheme {
        AppNav(deps = deps)
    }
}
