/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.yearinmusic

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.ui.screens.yearinmusic.YearInMusicViewModel

internal val RecapBlack = Color(0xFF422850)
internal val RecapSurfaceHigh = Color(0xFFFFF5FA)
internal val RecapRed = Color(0xFFE96AA9)
internal val RecapRedDeep = Color(0xFF8666C8)
internal val RecapCream = Color(0xFFFFFBF7)
internal val RecapYellow = Color(0xFFFFD979)
internal val RecapGreen = Color(0xFF65BFAE)
internal val RecapPurple = Color(0xFFB9A3FF)
internal val RecapBlue = Color(0xFFA8DFFF)
internal val RecapSurface = Color(0xFFEDE0F8)
internal val RecapPink = Color(0xFFFFB5D3)
internal val RecapLime = Color(0xFFE7F46D)
internal val RecapInk = Color(0xFF422850)
internal val RecapMint = Color(0xFFA8E5D4)
internal val RecapPeach = Color(0xFFFFC6A5)
internal object RecapTokens {
    val SectionRadius = 24.dp
    val ItemRadius = 18.dp
    val ShareVerticalPadding = 14.dp
}
@Composable
fun YearInMusicScreen(
    navController: NavController,
    initialYear: Int? = null,
    viewModel: YearInMusicViewModel = hiltViewModel(),
) {
    YearInMusicRoute(
        navController = navController,
        viewModel = viewModel,
        initialYear = initialYear,
    )
}
@Composable
private fun YearInMusicRoute(
    navController: NavController,
    viewModel: YearInMusicViewModel,
    initialYear: Int?,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialYear) {
        if (initialYear != null) {
            viewModel.selectYear(initialYear)
        }
    }

    YearInMusicRecapScreen(
        navController = navController,
        uiState = uiState,
    )
}
