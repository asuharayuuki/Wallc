package org.eu.suisei.wallc

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {
    private val preferences by lazy { getSharedPreferences("wallpapers", MODE_PRIVATE) }
    private val controller get() = (application as WallcApplication).controller()
    private var homeUri by mutableStateOf<Uri?>(null)
    private var lockUri by mutableStateOf<Uri?>(null)
    private var crop by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)

    private val homePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> acceptImage(uri, WallpaperTarget.HOME) }
    }
    private val lockPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> acceptImage(uri, WallpaperTarget.LOCK) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        homeUri = (savedInstanceState?.getString("home") ?: preferences.getString("home", null))?.let(Uri::parse)
        lockUri = (savedInstanceState?.getString("lock") ?: preferences.getString("lock", null))?.let(Uri::parse)
        crop = preferences.getBoolean("crop", true)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MiuixTheme(colors = colors) { WallpaperScreen() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("home", homeUri?.toString())
        outState.putString("lock", lockUri?.toString())
        super.onSaveInstanceState(outState)
    }

    @Composable
    private fun WallpaperScreen() {
        var backend by remember { mutableStateOf(controller.snapshot()) }
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }
        var handledCompletion by rememberSaveable { mutableLongStateOf(controller.completionId()) }
        val snackbar = remember { SnackbarHostState() }
        val homePreview by imagePreview(homeUri, WallpaperTarget.HOME)
        val lockPreview by imagePreview(lockUri, WallpaperTarget.LOCK)
        val target = if (selectedTab == 0) WallpaperTarget.HOME else WallpaperTarget.LOCK
        val uri = if (selectedTab == 0) homeUri else lockUri
        val bitmap = if (selectedTab == 0) homePreview else lockPreview
        val phoneAspect = remember { screenBounds().let { it.width().toFloat() / it.height() } }

        DisposableEffect(controller) {
            val listener = Runnable { backend = controller.snapshot() }
            controller.addListener(listener)
            onDispose { controller.removeListener(listener) }
        }
        LaunchedEffect(backend.completion) {
            if (backend.completion <= handledCompletion) return@LaunchedEffect
            handledCompletion = backend.completion
            if (!backend.succeeded) {
                error = getString(if (backend.completedTarget == 0) R.string.root_failed else R.string.wallpaper_failed)
            } else if (backend.completedTarget != 0) {
                snackbar.showSnackbar(getString(if (backend.completedTarget == WallpaperTarget.HOME) R.string.home_applied else R.string.lock_applied))
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(title = stringResource(R.string.wallpapers))
            },
            snackbarHost = { SnackbarHost(snackbar, Modifier.fillMaxWidth()) },
            bottomBar = {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                    Card(insideMargin = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.center_crop), modifier = Modifier.weight(1f))
                            Switch(checked = crop, enabled = !backend.busy, onCheckedChange = {
                                crop = it
                                preferences.edit { putBoolean("crop", it) }
                            })
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { selectImage(target) }, enabled = !backend.busy,
                                modifier = Modifier.weight(1f), insideMargin = PaddingValues(horizontal = 12.dp, vertical = 15.dp)) {
                                Icon(MiuixIcons.Photos, contentDescription = null, modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(stringResource(if (uri == null) R.string.select_image_action else R.string.replace_image))
                            }
                            Button(
                                onClick = { uri?.let { apply(it, target) } },
                                enabled = backend.ready && !backend.busy && bitmap != null,
                                modifier = Modifier.weight(1.1f),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 15.dp),
                            ) {
                                if (backend.busy && backend.activeTarget == target) {
                                    InfiniteProgressIndicator(size = 18.dp, color = MiuixTheme.colorScheme.disabledOnPrimaryButton)
                                } else {
                                    Icon(if (target == WallpaperTarget.HOME) MiuixIcons.Home else MiuixIcons.Lock,
                                        contentDescription = null, modifier = Modifier.size(19.dp))
                                }
                                Spacer(Modifier.width(7.dp))
                                Text(stringResource(if (backend.busy && backend.activeTarget == target) R.string.applying else R.string.apply_wallpaper))
                            }
                        }
                    }
                }
            },
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                val previewHeight = (maxHeight - if (backend.ready) 90.dp else 170.dp).coerceIn(220.dp, 480.dp)
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!backend.ready) {
                        RootPermissionRow(backend)
                        Spacer(Modifier.height(16.dp))
                    }
                    TabRow(
                        tabs = listOf(stringResource(R.string.home), stringResource(R.string.lock)),
                        selectedTabIndex = selectedTab,
                        onTabSelected = { if (!backend.busy) selectedTab = it },
                        maxWidth = 160.dp,
                    )
                    Spacer(Modifier.height(24.dp))
                    Card(
                        modifier = Modifier.width(previewHeight * phoneAspect).height(previewHeight),
                        cornerRadius = 28.dp,
                        insideMargin = PaddingValues(0.dp),
                        onClick = { if (!backend.busy) selectImage(target) },
                    ) {
                        Box(Modifier.fillMaxSize().squircleClip(28.dp), contentAlignment = Alignment.Center) {
                            if (bitmap != null) {
                                Image(bitmap.asImageBitmap(),
                                    contentDescription = stringResource(if (target == WallpaperTarget.HOME) R.string.home_preview else R.string.lock_preview),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
                            } else if (uri != null) {
                                InfiniteProgressIndicator(color = MiuixTheme.colorScheme.primary)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(MiuixIcons.Image, contentDescription = null, modifier = Modifier.size(40.dp),
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    Text(stringResource(R.string.select_image), style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            OverlayDialog(show = error != null, title = stringResource(R.string.operation_failed),
                summary = error, onDismissRequest = { error = null }) {
                TextButton(text = stringResource(R.string.done), onClick = { error = null },
                    modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
    }

    @Composable
    private fun RootPermissionRow(backend: BackendState) {
        Card(insideMargin = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.root_permission), modifier = Modifier.weight(1f))
                if (backend.busy) {
                    InfiniteProgressIndicator(size = 20.dp, color = MiuixTheme.colorScheme.primary)
                    Text(stringResource(R.string.authorizing), style = MiuixTheme.textStyles.footnote1)
                } else {
                    TextButton(text = stringResource(if (backend.completion > 0) R.string.retry else R.string.authorize),
                        onClick = controller::authorize, minHeight = 36.dp,
                        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary())
                }
            }
        }
    }

    @Composable
    private fun imagePreview(uri: Uri?, target: Int): State<Bitmap?> = produceState<Bitmap?>(null, uri) {
        value = null
        if (uri != null) {
            try {
                value = withContext(Dispatchers.IO) { ImagePreparer.decode(this@MainActivity, uri, 1440, 2_000_000) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { invalidateImage(uri, target) }
            catch (e: OutOfMemoryError) { invalidateImage(uri, target) }
        }
    }

    private fun acceptImage(uri: Uri, target: Int) {
        if (uri.scheme != "content") { error = getString(R.string.image_failed); return }
        var persist = true
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (e: SecurityException) { persist = false }
        val old = if (target == WallpaperTarget.HOME) homeUri else lockUri
        if (target == WallpaperTarget.HOME) homeUri = uri else lockUri = uri
        preferences.edit { putString(if (target == WallpaperTarget.HOME) "home" else "lock", if (persist) uri.toString() else null) }
        if (old != null && old != homeUri && old != lockUri) {
            try { contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (e: SecurityException) { /* The provider may only support a temporary grant. */ }
        }
    }

    private fun invalidateImage(uri: Uri, target: Int) {
        if ((if (target == WallpaperTarget.HOME) homeUri else lockUri) != uri) return
        if (target == WallpaperTarget.HOME) homeUri = null else lockUri = null
        preferences.edit { remove(if (target == WallpaperTarget.HOME) "home" else "lock") }
        error = getString(R.string.image_failed)
    }

    private fun selectImage(target: Int) {
        try { (if (target == WallpaperTarget.HOME) homePicker else lockPicker).launch(arrayOf("image/*")) }
        catch (e: android.content.ActivityNotFoundException) { error = getString(R.string.image_failed) }
    }

    private fun apply(uri: Uri, target: Int) {
        val bounds = screenBounds()
        controller.apply(uri, target, crop, bounds.width(), bounds.height())
    }

    @Suppress("DEPRECATION")
    private fun screenBounds(): Rect = if (Build.VERSION.SDK_INT >= 30) windowManager.maximumWindowMetrics.bounds
        else android.util.DisplayMetrics().let { windowManager.defaultDisplay.getRealMetrics(it); Rect(0, 0, it.widthPixels, it.heightPixels) }
}

private data class BackendState(val ready: Boolean, val busy: Boolean, val activeTarget: Int,
    val completion: Long, val completedTarget: Int, val succeeded: Boolean)

private fun WallpaperController.snapshot() = BackendState(diagnostics().getBoolean("ok"), busy(), activeTarget(),
    completionId(), completedTarget(), lastSucceeded())
