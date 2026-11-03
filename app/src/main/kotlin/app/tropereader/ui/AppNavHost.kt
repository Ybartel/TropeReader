package app.tropereader.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.tropereader.ui.library.LibraryScreen
import app.tropereader.ui.reader.ReaderScreen
import app.tropereader.ui.reader.ReaderViewModel
import app.tropereader.ui.work.WorkScreen
import app.tropereader.ui.work.WorkViewModel

/** Library → Work → Reader. */
@Composable
fun AppNavHost(sharedText: String?, onSharedTextConsumed: () -> Unit) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = LIBRARY) {
        composable(LIBRARY) {
            LibraryScreen(
                sharedText = sharedText,
                onSharedTextConsumed = onSharedTextConsumed,
                onOpenWork = { nav.navigate("work/$it") { launchSingleTop = true } },
            )
        }
        composable("work/{${WorkViewModel.WORK_ID}}", listOf(navArgument(WorkViewModel.WORK_ID) { type = NavType.StringType })) {
            WorkScreen(onBack = nav::popBackStack, onOpenPage = { nav.navigate("reader/$it") })
        }
        composable("reader/{${ReaderViewModel.PAGE_ID}}", listOf(navArgument(ReaderViewModel.PAGE_ID) { type = NavType.StringType })) {
            ReaderScreen(onBack = nav::popBackStack)
        }
    }
}

private const val LIBRARY = "library"
