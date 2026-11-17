package jp.local.okozukai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class LaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launchShowsSetupScreen() {
        compose.waitUntil(10000) { compose.onAllNodes(androidx.compose.ui.test.hasText("まだ記録がありません")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("まだ記録がありません").assertIsDisplayed()
        compose.onNodeWithText("＋記録").performClick()
        compose.onNodeWithText("金額（円）").performTextInput("６５０")
        compose.onNodeWithText("食費").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) { compose.onAllNodes(androidx.compose.ui.test.hasText("記録を追加")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("保存").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodes(androidx.compose.ui.test.hasText("支出−650円")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("支出−650円").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) { compose.onAllNodes(androidx.compose.ui.test.hasText("支出−650円")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("支出−650円").assertIsDisplayed()
    }
}
