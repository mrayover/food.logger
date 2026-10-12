package com.foodlogger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiFlowTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 @Test fun homeLogMealCollapseAndPriorDayNavigation() {
  ui.waitUntil(15000) {ui.onAllNodesWithText("LOG FOOD").fetchSemanticsNodes().isNotEmpty()}
  ui.onNodeWithText("LOG FOOD").performScrollTo().performClick()
  ui.onNodeWithText("Breakfast").performClick()
  ui.onNodeWithText("+ Add to Breakfast").assertIsDisplayed()
  ui.onNodeWithText("Breakfast").performClick()
  ui.onNodeWithText("+ Add to Breakfast").assertDoesNotExist()
  ui.onNodeWithText("Breakfast").performClick()
  ui.onNodeWithText("+ Add to Breakfast").performClick()
  ui.onNodeWithText("Search foods or brands").assertExists()
  ui.onNodeWithText("Create food manually").performClick()
  ui.onNodeWithText("Name").performTextInput("UI test food")
  ui.onNodeWithText("Save food").performScrollTo().performClick()
  ui.waitUntil(5000) {ui.onAllNodesWithText("Add").fetchSemanticsNodes().isNotEmpty()}
  ui.onNodeWithText("Add",useUnmergedTree=true).performClick()
  ui.waitUntil(5000) {ui.onAllNodesWithText("UI test food").fetchSemanticsNodes().isNotEmpty()}
  ui.onNodeWithText("UI test food").assertExists()
 }
}
