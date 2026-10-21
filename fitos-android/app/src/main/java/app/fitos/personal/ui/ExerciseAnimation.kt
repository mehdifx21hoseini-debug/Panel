package app.fitos.personal.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.personal.R

/**
 * The bundled 3D animation of a built-in exercise: assets/exercise_anim/<key>_light.mp4 and
 * <key>_dark.mp4, drawn on the stage colour of each theme so the video blends into its frame.
 */
object ExerciseAnimations {
    private const val DIR = "exercise_anim"
    private var names: Set<String>? = null

    private fun names(context: Context): Set<String> =
        names ?: (context.assets.list(DIR)?.toSet() ?: emptySet()).also { names = it }

    fun has(context: Context, key: String?): Boolean = key != null && "${key}_light.mp4" in names(context)

    fun asset(key: String, dark: Boolean) = "$DIR/${key}_${if (dark) "dark" else "light"}.mp4"

    /** Plays by default unless the system's animations are turned off; then it waits for a tap. */
    fun autoplay(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}

/** A looping, silent player for one asset; tap to pause and play. Nothing is drawn until a frame is ready. */
@Composable
fun ExerciseAnimation(key: String, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = FitTheme.colors
    val asset = ExerciseAnimations.asset(key, colors.isDark)
    var playing by rememberSaveable(key) { mutableStateOf(ExerciseAnimations.autoplay(context)) }
    val player = remember(asset) { LoopPlayer(context, asset) }
    player.setPlaying(playing)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.setVisible(false)
                Lifecycle.Event.ON_RESUME -> player.setVisible(true)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }

    val description = stringResource(R.string.exercise_animation, name)
    val state = stringResource(if (playing) R.string.exercise_animation_playing else R.string.exercise_animation_paused)
    val toggle = stringResource(if (playing) R.string.exercise_animation_pause else R.string.exercise_animation_play)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .clickable(onClickLabel = toggle, role = Role.Button) { playing = !playing }
            .semantics {
                contentDescription = description
                stateDescription = state
            },
    ) {
        AndroidView(
            factory = { ctx -> TextureView(ctx).apply { isOpaque = false; surfaceTextureListener = player } },
            modifier = Modifier.fillMaxSize(),
        )
        if (!playing) {
            Box(
                Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape).background(colors.surface.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(FitIcons.Play, contentDescription = null, tint = colors.primary, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

/** MediaPlayer bound to a TextureView's surface; looping, muted, released with the view. */
private class LoopPlayer(private val context: Context, private val asset: String) : TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var playing = false
    private var visible = true

    fun setPlaying(on: Boolean) {
        playing = on
        sync()
    }

    fun setVisible(on: Boolean) {
        visible = on
        sync()
    }

    private fun sync() {
        val p = player ?: return
        if (!prepared) return
        if (playing && visible) {
            if (!p.isPlaying) p.start()
        } else if (p.isPlaying) {
            p.pause()
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val s = Surface(texture)
        surface = s
        runCatching {
            val p = MediaPlayer()
            context.assets.openFd(asset).use { fd -> p.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            p.setSurface(s)
            p.isLooping = true
            p.setVolume(0f, 0f)
            p.setOnPreparedListener {
                prepared = true
                // Show the first frame even when paused.
                it.seekTo(0)
                sync()
            }
            p.prepareAsync()
            player = p
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun release() {
        player?.release()
        player = null
        prepared = false
        surface?.release()
        surface = null
    }
}
