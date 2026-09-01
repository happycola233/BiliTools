package com.happycola233.bilitools.ui.me

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.happycola233.bilitools.ui.history.HistoryActivity
import com.happycola233.bilitools.ui.login.LoginActivity
import com.happycola233.bilitools.ui.login.LoginViewModel
import com.happycola233.bilitools.ui.settings.SettingsActivity

/** 主壳中的“我”页入口；平台导航与生命周期副作用在这里完成。 */
@Composable
fun MeRoute(
    viewModel: LoginViewModel,
    contentTopPadding: Dp,
    onOpenParseUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val loginState by viewModel.state.collectAsState()

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLoginState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BiliToolsMeContent(
        loginState = loginState,
        contentTopPadding = contentTopPadding,
        onOpenLogin = {
            context.startActivity(Intent(context, LoginActivity::class.java))
        },
        onOpenHistory = {
            if (viewModel.state.value.isLoggedIn) {
                context.startActivity(Intent(context, HistoryActivity::class.java))
            }
        },
        onOpenFavorite = favorite@{
            if (!viewModel.state.value.isLoggedIn) return@favorite
            val mid = viewModel.state.value.currentMid
                ?: viewModel.state.value.userInfo?.mid
                ?: return@favorite
            onOpenParseUrl("https://space.bilibili.com/$mid/favlist")
        },
        onOpenWatchLater = watchLater@{
            if (!viewModel.state.value.isLoggedIn) return@watchLater
            onOpenParseUrl("https://www.bilibili.com/watchlater")
        },
        onOpenSettings = {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        },
        onLogout = viewModel::logout,
        modifier = modifier,
    )
}
