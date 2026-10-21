package app.fitos.personal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.domain.training.ExerciseLibrary
import app.fitos.personal.ui.ExerciseAnimations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExerciseAnimationAssetsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyBuiltInExercise_hasALightAndADarkAnimation() {
        val names = context.assets.list("exercise_anim")!!.toSet()
        val missing = ExerciseLibrary.all.flatMap { e -> listOf(false, true).map { ExerciseAnimations.asset(e.key, it) } }
            .filter { it.substringAfter('/') !in names }
        assertEquals("missing animations", emptyList<String>(), missing)
        ExerciseLibrary.all.forEach { assertTrue(ExerciseAnimations.has(context, it.key)) }
    }

    @Test
    fun noAnimationWithoutAnExercise_andEachIsSmall() {
        val keys = ExerciseLibrary.all.map { it.key }.toSet()
        val names = context.assets.list("exercise_anim")!!
        val orphans = names.filter { it.removeSuffix("_light.mp4").removeSuffix("_dark.mp4") !in keys }
        assertEquals("animations without an exercise", emptyList<String>(), orphans)
        names.forEach { name ->
            val size = context.assets.open("exercise_anim/$name").use { it.readBytes().size.toLong() }
            assertTrue("$name is $size bytes", size in 10_000..MAX_BYTES)
        }
    }

    @Test
    fun unknownOrCustomExercise_hasNoAnimation() {
        assertTrue(!ExerciseAnimations.has(context, null))
        assertTrue(!ExerciseAnimations.has(context, "no_such_exercise"))
    }

    private companion object {
        const val MAX_BYTES = 400_000L
    }
}
