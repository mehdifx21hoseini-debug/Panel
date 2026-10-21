package app.fitos.personal

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/**
 * Room delivers data on its own threads, which the test clock does not track, so screens that
 * load from the database are awaited explicitly.
 */
fun ComposeTestRule.waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 5_000): SemanticsNodeInteraction {
    waitUntil(timeoutMs) {
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() || scrollListsTo(matcher)
    }
    return onNode(matcher)
}

/** Lazy lists only compose what is on screen; ask every scrollable list to bring [matcher] in. */
private fun ComposeTestRule.scrollListsTo(matcher: SemanticsMatcher): Boolean {
    val lists = onAllNodes(hasScrollToNodeAction())
    return lists.fetchSemanticsNodes().indices.any { i ->
        runCatching { lists[i].performScrollToNode(matcher) }.isSuccess
    } && onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
}

fun ComposeTestRule.waitForText(text: String) = waitFor(hasText(text))

/** A clickable node by the label it gives its click action (onClickLabel). */
fun hasClickLabel(label: String) = SemanticsMatcher("click label '$label'") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }
fun ComposeTestRule.waitForDescription(text: String, substring: Boolean = false) = waitFor(hasContentDescription(text, substring = substring))

/** An editable text field by its label. */
fun ComposeTestRule.field(label: String) = waitFor(hasText(label) and hasSetTextAction())

/** Scrolls when the node sits in a scrolling container (buttons pinned below are not). */
fun SemanticsNodeInteraction.scrollIfNeeded(): SemanticsNodeInteraction =
    runCatching { performScrollTo() }.getOrDefault(this)

fun ComposeTestRule.tapText(text: String) = waitForText(text).scrollIfNeeded().performClick()

/** Picks [option] from the [index]th dropdown labelled [menu] (e.g. the day menu of a date field). */
fun ComposeTestRule.pickFromMenu(menu: String, option: String, index: Int = 0) {
    waitFor(hasText(menu) and hasClickAction())
    onAllNodes(hasText(menu) and hasClickAction())[index].scrollIfNeeded().performClick()
    waitForText(option).scrollIfNeeded().performClick()
}


/** Runs the main looper until [condition] holds, for code that is not tied to a composition. */
fun awaitMain(timeoutMs: Long = 10_000, condition: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        if (System.currentTimeMillis() > end) throw AssertionError("condition not met in $timeoutMs ms")
        Thread.sleep(10)
    }
}
