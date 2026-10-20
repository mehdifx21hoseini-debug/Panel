package app.fitos.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitos.core.designsystem.R
import app.fitos.core.designsystem.theme.FitTheme

/** Fluent 3D emoji (Microsoft, MIT licence), bundled offline. Content markers only, never UI icons. */
enum class FitEmoji(@param:DrawableRes val res: Int) {
    APPLE(R.drawable.emoji_apple),
    BARCHART(R.drawable.emoji_barchart),
    BELL(R.drawable.emoji_bell),
    BICEPS(R.drawable.emoji_biceps),
    BOLT(R.drawable.emoji_bolt),
    BREAD(R.drawable.emoji_bread),
    BULB(R.drawable.emoji_bulb),
    BULLSEYE(R.drawable.emoji_bullseye),
    CALENDAR(R.drawable.emoji_calendar),
    CAMERA(R.drawable.emoji_camera),
    CHARTUP(R.drawable.emoji_chartup),
    CHECK(R.drawable.emoji_check),
    COOL(R.drawable.emoji_cool),
    CUPSTRAW(R.drawable.emoji_cupstraw),
    DROPLET(R.drawable.emoji_droplet),
    EGG(R.drawable.emoji_egg),
    FIRE(R.drawable.emoji_fire),
    FLOPPY(R.drawable.emoji_floppy),
    HOUSE(R.drawable.emoji_house),
    LIFTING(R.drawable.emoji_lifting),
    LOCK(R.drawable.emoji_lock),
    LOTUS(R.drawable.emoji_lotus),
    MILK(R.drawable.emoji_milk),
    MOON(R.drawable.emoji_moon),
    MUSCLE_FLAG(R.drawable.emoji_muscle_flag),
    NEUTRAL(R.drawable.emoji_neutral),
    PERSEVERING(R.drawable.emoji_persevering),
    PLATE(R.drawable.emoji_plate),
    POULTRY(R.drawable.emoji_poultry),
    RICE(R.drawable.emoji_rice),
    ROCKET(R.drawable.emoji_rocket),
    RULER(R.drawable.emoji_ruler),
    SALAD(R.drawable.emoji_salad),
    SCALE(R.drawable.emoji_scale),
    SHOE(R.drawable.emoji_shoe),
    SLEEPING(R.drawable.emoji_sleeping),
    SPARKLES(R.drawable.emoji_sparkles),
    STOPWATCH(R.drawable.emoji_stopwatch),
    SUN(R.drawable.emoji_sun),
    TROPHY(R.drawable.emoji_trophy),
    WALKING(R.drawable.emoji_walking),
    WAVE(R.drawable.emoji_wave),
}

/** An emoji on a soft rounded tile (corner = 32% of size, emoji = 66%). Decorative: the row label carries meaning. */
@Composable
fun EmojiTile(
    emoji: FitEmoji,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = FitTheme.colors.surfaceContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        FitEmojiImage(emoji, Modifier.padding(size * 0.17f).fillMaxSize())
    }
}

/** The bare emoji image, e.g. inline after a greeting. */
@Composable
fun FitEmojiImage(emoji: FitEmoji, modifier: Modifier = Modifier) {
    Image(painter = painterResource(emoji.res), contentDescription = null, modifier = modifier)
}
