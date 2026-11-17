package jp.local.okozukai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import org.junit.Rule
import org.junit.Test

class LaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recordEditBudgetGoalAndDelete() {
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
        compose.onNodeWithText("支出−650円").performClick()
        compose.onNodeWithText("金額（円）").performTextReplacement("600")
        compose.onNodeWithText("保存").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodes(androidx.compose.ui.test.hasText("支出−600円")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("＋記録").performClick()
        compose.onNodeWithText("収入").performClick()
        compose.onNodeWithText("金額（円）").performTextInput("50000")
        compose.onNodeWithText("バイト代").performClick()
        compose.onNodeWithText("保存").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodes(androidx.compose.ui.test.hasText("収入＋50,000円")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("予算").performClick()
        compose.onNodeWithText("予算を編集").performScrollTo().performClick()
        compose.onNodeWithText("月全体（円）").performTextInput("0")
        compose.onNodeWithText("食費（円）").performTextInput("1000")
        compose.onNodeWithText("キャンセル").performScrollTo()
        compose.onNodeWithText("予算を保存").performScrollTo().performClick()
        try {
            compose.waitUntil(10000) { compose.onAllNodes(androidx.compose.ui.test.hasText("予算 0円／実績 600円／600円超過")).fetchSemanticsNodes().isNotEmpty() }
        } catch(e:Throwable) {throw AssertionError(compose.onRoot().printToString(),e)}
        compose.onNodeWithText("目標").performClick()
        compose.onNodeWithText("目標を追加").performClick()
        compose.onNodeWithText("目標名").performTextInput("架空の旅行")
        compose.onNodeWithText("目標額（円）").performTextInput("10000")
        compose.onNodeWithText("現在の積立額（円）").performTextReplacement("12000")
        compose.onNodeWithText("目標を保存").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodes(androidx.compose.ui.test.hasText("達成")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("達成").assertIsDisplayed()
        compose.onNodeWithText("履歴").performClick()
        compose.onNodeWithText("支出−600円").performClick()
        compose.onNodeWithText("この記録を削除").performScrollTo().performClick()
        compose.onNodeWithText("確認して実行").performClick()
        compose.waitUntil(10000) { compose.onAllNodes(androidx.compose.ui.test.hasText("収支 50,000円")).fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) { compose.onAllNodes(androidx.compose.ui.test.hasText("収支 50,000円")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("収支 50,000円").assertIsDisplayed()
    }
}
