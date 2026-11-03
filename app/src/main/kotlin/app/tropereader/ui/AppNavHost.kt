package app.tropereader.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.tropereader.domain.TvTropesUrls
import app.tropereader.ui.library.LibraryScreen
import app.tropereader.ui.linked.LinkedPageScreen
import app.tropereader.ui.linked.LinkedPageViewModel
import app.tropereader.ui.reader.ReaderScreen
import app.tropereader.ui.reader.ReaderViewModel
import app.tropereader.ui.work.WorkScreen
import app.tropereader.ui.work.WorkViewModel

/** Library → Work → Reader, with linked pages stacked on top (F7.1). */
@Composable
fun AppNavHost(sharedText: String?, onSharedTextConsumed: () -> Unit) {
    val nav = rememberNavController()
    val uriHandler = LocalUriHandler.current
    // Wiki pages open in the app; anything else (other sites, forums) in the phone's browser (F7.4).
    val openLink: (String) -> Unit = { url ->
        val page = TvTropesUrls.wikiPage(url)
        if (page != null) nav.navigate("linked/${Uri.encode(page)}") else uriHandler.openUri(url)
    }
    val openWork: (String) -> Unit = { nav.navigate("work/$it") { launchSingleTop = true } }

    NavHost(nav, startDestination = LIBRARY) {
        composable(LIBRARY) {
            LibraryScreen(sharedText = sharedText, onSharedTextConsumed = onSharedTextConsumed, onOpenWork = openWork)
        }
        composable("work/{${WorkViewModel.WORK_ID}}", listOf(navArgument(WorkViewModel.WORK_ID) { type = NavType.StringType })) {
            WorkScreen(onBack = nav::popBackStack, onOpenPage = { nav.navigate("reader/$it") })
        }
        composable("reader/{${ReaderViewModel.PAGE_ID}}", listOf(navArgument(ReaderViewModel.PAGE_ID) { type = NavType.StringType })) {
            ReaderScreen(onBack = nav::popBackStack, onLink = openLink)
        }
        composable("linked/{${LinkedPageViewModel.URL}}", listOf(navArgument(LinkedPageViewModel.URL) { type = NavType.StringType })) {
            LinkedPageScreen(onBack = nav::popBackStack, onLink = openLink, onOpenWork = openWork)
        }
    }
}

private const val LIBRARY = "library"
