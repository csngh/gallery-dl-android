package app.gallerydl.ui.components

import android.media.AudioAttributes
import android.media.SoundPool
import android.provider.Settings
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gallerydl.R
import kotlinx.coroutines.delay

private val SplashBackground = Color(0xFF0B1020)

/** The system splash shows the still logo; this takes over seamlessly in the same place and animates it. */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun SplashOverlay(playSound: Boolean, onFinished: () -> Unit) {
    val context = LocalContext.current
    val animationsOn = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }

    // With animations switched off in system settings the intro is skipped, as the user has asked for no motion.
    if (!animationsOn) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }

    val image = AnimatedImageVector.animatedVectorResource(R.drawable.ic_splash_animated)
    var atEnd by remember { mutableStateOf(false) }
    val wordmark = remember { Animatable(0f) }
    val overlay = remember { Animatable(1f) }

    if (playSound) StartupSound()

    LaunchedEffect(Unit) {
        atEnd = true                                  // starts the cards pop and the badge drop
        delay(450)
        wordmark.animateTo(1f, tween(400))            // the name fades in as the badge lands
        delay(750)
        overlay.animateTo(0f, tween(250))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(overlay.value)
            .background(SplashBackground)
            // The intro covers the app for a moment; swallow touches so nothing is pressed underneath.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        // Exactly centred, where the system splash drew the still logo, so the handoff does not move.
        Image(
            painter = rememberAnimatedVectorPainter(image, atEnd),
            contentDescription = null,
            modifier = Modifier.size(288.dp),
        )
        // The name floats below the visible logo instead of taking part in the layout, which would shift the logo.
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.offset(y = NameOffset).alpha(wordmark.value),
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFFE3E6F3),
        )
    }
}

/** The name's distance below the centre of the screen: just under the artwork's visible edge. */
private val NameOffset = 114.dp

/**
 * The short download sound. It is played as a "sonification" sound, so it follows the phone's silent and
 * vibrate modes and is never louder than the user's interface sounds.
 */
@Composable
private fun StartupSound() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val pool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        pool.setOnLoadCompleteListener { player, id, status ->
            if (status == 0) player.play(id, 1f, 1f, 1, 0, 1f)
        }
        pool.load(context, R.raw.splash_download, 1)
        onDispose { pool.release() }
    }
}
