package app.fitos.personal

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
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
    waitUntil(timeoutMs) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    return onNode(matcher)
}

fun ComposeTestRule.waitForText(text: String) = waitFor(hasText(text))
fun ComposeTestRule.waitForDescription(text: String) = waitFor(hasContentDescription(text))

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

