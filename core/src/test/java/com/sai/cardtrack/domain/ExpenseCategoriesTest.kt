package com.sai.cardtrack.domain

import com.sai.cardtrack.ui.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCategoriesTest {

    @Test
    fun `parents match popular expense trackers`() {
        assertEquals(
            listOf(
                "food", "transport", "shopping", "entertainment", "subscriptions",
                "health", "comms", "housing", "travel", "other"
            ),
            ExpenseCategories.PARENTS.map { it.id }
        )
        assertEquals("Еда", ExpenseCategories.PARENTS[0].label)
        assertEquals("Здоровье", ExpenseCategories.find("health")?.label)
        assertEquals("Жильё", ExpenseCategories.find("housing")?.label)
        assertEquals("Путешествия", ExpenseCategories.find("travel")?.label)
        assertEquals("Возврат", ExpenseCategories.REFUND_LABEL)
    }

    @Test
    fun `food children cover groceries dining coffee and delivery`() {
        assertEquals(
            listOf("groceries", "dining", "coffee", "food_delivery"),
            ExpenseCategories.childrenOf("food").map { it.id }
        )
        assertEquals("Продукты", ExpenseCategories.find("groceries")?.label)
        assertEquals("Кафе и рестораны", ExpenseCategories.find("dining")?.label)
        assertEquals("Кофейни", ExpenseCategories.find("coffee")?.label)
        assertEquals("Доставка", ExpenseCategories.find("food_delivery")?.label)
    }

    @Test
    fun `transport children cover taxi transit fuel and parking`() {
        assertEquals(
            listOf("taxi", "transit", "fuel", "parking"),
            ExpenseCategories.childrenOf("transport").map { it.id }
        )
    }

    @Test
    fun `shopping children cover clothes electronics home and marketplaces`() {
        assertEquals(
            listOf("clothes", "electronics", "home_goods", "marketplaces"),
            ExpenseCategories.childrenOf("shopping").map { it.id }
        )
    }

    @Test
    fun `entertainment children cover events games and hobbies`() {
        assertEquals(
            listOf("events", "games", "hobbies"),
            ExpenseCategories.childrenOf("entertainment").map { it.id }
        )
    }

    @Test
    fun `subscriptions children cover streaming software and media`() {
        assertEquals(
            listOf("streaming", "software", "media"),
            ExpenseCategories.childrenOf("subscriptions").map { it.id }
        )
    }

    @Test
    fun `health children cover pharmacy clinic and fitness`() {
        assertEquals(
            listOf("pharmacy", "clinic", "fitness"),
            ExpenseCategories.childrenOf("health").map { it.id }
        )
        assertEquals("health", ExpenseCategories.find("pharmacy")?.parentId)
        assertEquals("Аптека", ExpenseCategories.find("pharmacy")?.label)
    }

    @Test
    fun `comms housing travel and other have practical children`() {
        assertEquals(listOf("mobile", "internet"), ExpenseCategories.childrenOf("comms").map { it.id })
        assertEquals(listOf("rent", "utilities", "home_repair"), ExpenseCategories.childrenOf("housing").map { it.id })
        assertEquals(listOf("hotels", "tickets", "tours"), ExpenseCategories.childrenOf("travel").map { it.id })
        assertEquals(listOf("gifts", "education", "fees"), ExpenseCategories.childrenOf("other").map { it.id })
    }

    @Test
    fun `label for parent stays short and child includes parent`() {
        assertEquals("Еда", ExpenseCategories.labelFor("food"))
        assertEquals("Еда · Продукты", ExpenseCategories.labelFor("groceries"))
        assertEquals("Здоровье · Аптека", ExpenseCategories.labelFor("pharmacy"))
        assertNull(ExpenseCategories.labelFor(null))
        assertNull(ExpenseCategories.labelFor("unknown"))
    }

    @Test
    fun `every id is unique and every child points to a parent`() {
        val ids = ExpenseCategories.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ExpenseCategories.ALL.filter { it.parentId != null }.forEach { child ->
            assertTrue(child.id, ExpenseCategories.PARENTS.any { it.id == child.parentId })
        }
    }

    @Test
    fun `sheet chips show parents then only children`() {
        assertEquals(ExpenseCategories.PARENTS, ExpenseCategories.sheetChoices(null))
        val foodSheet = ExpenseCategories.sheetChoices("food")
        assertEquals(
            listOf("groceries", "dining", "coffee", "food_delivery"),
            foodSheet.map { it.id }
        )
        assertTrue(foodSheet.none { it.id == "food" })
    }

    @Test
    fun `drilled picker leads with the parent choice and short child names`() {
        val choices = ExpenseCategories.pickerChoices("food", AppLocale.Ru, "Общая")

        assertEquals("food", choices.first().id)
        assertEquals("Общая", choices.first().label)
        assertEquals(
            listOf("Продукты", "Кафе и рестораны", "Кофейни", "Доставка"),
            choices.drop(1).map { it.label }
        )
    }
}
