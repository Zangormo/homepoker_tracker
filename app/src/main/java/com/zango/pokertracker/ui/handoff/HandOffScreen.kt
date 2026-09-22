package com.zango.pokertracker.ui.handoff

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.zango.pokertracker.R
import com.zango.pokertracker.ui.common.MinTouchTarget
import com.zango.pokertracker.ui.common.resolve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shows the running game as a QR code for the next host to scan, with a file as the way round it
 * when the phones are apart or the game has outgrown one code.
 */
@Composable
fun HandOffScreen(
    onBack: () -> Unit,
    onRemoved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HandOffViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var askingToRemove by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HandOffEvent.Removed -> onRemoved()
                is HandOffEvent.Message -> snackbarHostState.showSnackbar(event.text.resolve(context))
            }
        }
    }

    // A code that dims away while the other host is still lining up their camera is no use.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.handoff_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val payload = state.payload
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.isMissing || payload == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.live_game_missing)) }

            else -> HandOffContent(
                gameName = state.gameName,
                payload = payload,
                fitsInQrCode = state.fitsInQrCode,
                onSendFile = { shareGameFile(context, state.gameName, payload) },
                onDone = { askingToRemove = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (askingToRemove) {
        AlertDialog(
            onDismissRequest = { askingToRemove = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.handoff_remove_title)) },
            text = { Text(stringResource(R.string.handoff_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askingToRemove = false
                        viewModel.onRemoveFromThisPhone()
                    },
                ) { Text(stringResource(R.string.handoff_remove_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        askingToRemove = false
                        onBack()
                    },
                ) { Text(stringResource(R.string.handoff_keep)) }
            },
        )
    }
}

@Composable
private fun HandOffContent(
    gameName: String,
    payload: String,
    fitsInQrCode: Boolean,
    onSendFile: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(gameName, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)

        if (fitsInQrCode) {
            val qr by produceState<ImageBitmap?>(initialValue = null, payload) {
                value = withContext(Dispatchers.Default) { qrCode(payload) }
            }
            // Always black on white, whatever the theme: scanners read dark modules on a light
            // ground, and the quiet zone round the code is part of what they look for.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.onSurface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    qr?.let {
                        Image(
                            bitmap = it,
                            contentDescription = stringResource(R.string.handoff_qr_description),
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } ?: CircularProgressIndicator()
                }
            }
            Text(
                stringResource(R.string.handoff_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                stringResource(R.string.handoff_too_big),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        if (fitsInQrCode) {
            OutlinedButton(
                onClick = onSendFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MinTouchTarget),
            ) { Text(stringResource(R.string.handoff_send_file_instead)) }
        } else {
            Button(
                onClick = onSendFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MinTouchTarget),
            ) { Text(stringResource(R.string.handoff_send_file)) }
        }
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(MinTouchTarget),
        ) { Text(stringResource(R.string.handoff_done)) }
    }
}

private const val QR_BLACK = 0xFF000000.toInt()
private const val QR_WHITE = 0xFFFFFFFF.toInt()

/** One pixel per module; the Image scales it up without smoothing, so the edges stay sharp. */
private fun qrCode(text: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
        ),
    )
    val size = matrix.width
    val pixels = IntArray(size * size) { index ->
        if (matrix[index % size, index / size]) QR_BLACK else QR_WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Writes the game to a file in the cache and offers it to whatever the host sends things with.
 * The file is overwritten on the next handover, so the cache never collects old nights.
 */
private fun shareGameFile(context: Context, gameName: String, payload: String) {
    val directory = File(context.cacheDir, HANDOFF_DIRECTORY).apply { mkdirs() }
    val safeName = gameName.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { "game" }
    val file = File(directory, "$safeName.$HANDOFF_EXTENSION")
    file.writeText(payload)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.handoff", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/octet-stream")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.handoff_share_chooser)))
}

private const val HANDOFF_DIRECTORY = "handoff"
private const val HANDOFF_EXTENSION = "pokergame"
