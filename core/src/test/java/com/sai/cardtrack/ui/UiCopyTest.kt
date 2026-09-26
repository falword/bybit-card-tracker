package com.sai.cardtrack.ui

import com.sai.cardtrack.domain.ExpenseCategories
import com.sai.cardtrack.sync.KeyPermissionCheck
import com.sai.cardtrack.sync.bybitAccessBanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiCopyTest {

    @Test
    fun `english copy translates category empty month and read-only banner`() {
        val copy = UiCopy(AppLocale.En)
        assertEquals("Food · Delivery", ExpenseCategories.labelFor("food_delivery", AppLocale.En))
        assertEquals("uncategorized", copy.uncategorized)
        assertEquals("No transactions yet", copy.noTransactions)
        assertEquals("no expenses", copy.noExpenses)
        assertEquals("Fees", copy.fees)
        assertEquals("Declines", copy.declines)
        assertEquals("Export CSV", copy.exportCsv)
        assertEquals("Export Koinly", copy.exportKoinly)
        assertEquals("Delete key and data", copy.wipe)
        assertEquals(
            "Delete the key and all transactions from this device?",
            copy.wipeConfirm
        )
        assertEquals(
            "The key and history were deleted on this device. Revoke the key in Bybit → API.",
            copy.wipeDone
        )
        assertEquals(
            "This key is not read-only. Create a new key: read-only, no trading or withdraw.",
            copy.notReadOnly
        )
        assertEquals(copy.noCard, bybitAccessBanner(10005, "permission denied", AppLocale.En))
    }

    @Test
    fun `russian copy keeps current category and banner text`() {
        val copy = UiCopy(AppLocale.Ru)
        assertEquals("Еда · Доставка", ExpenseCategories.labelFor("food_delivery", AppLocale.Ru))
        assertEquals("без категории", copy.uncategorized)
        assertEquals("Комиссии", copy.fees)
        assertEquals("Отказы", copy.declines)
        assertEquals("Экспорт CSV", copy.exportCsv)
        assertEquals("Экспорт Koinly", copy.exportKoinly)
        assertEquals("Удалить ключ и данные", copy.wipe)
        assertEquals(
            "Удалить ключ и все операции с этого устройства?",
            copy.wipeConfirm
        )
        assertEquals(
            "Ключ и история удалены с устройства. Отзови ключ в Bybit → API.",
            copy.wipeDone
        )
        assertEquals(KeyPermissionCheck.NOT_READONLY, copy.notReadOnly)
        assertEquals(copy.noCard, bybitAccessBanner(10005, "permission denied"))
    }

    @Test
    fun `account balance and include p2p are localized`() {
        assertEquals("Account balance", UiCopy(AppLocale.En).accountBalance)
        assertEquals("Include P2P", UiCopy(AppLocale.En).includeP2p)
        assertEquals("Баланс счёта", UiCopy.Ru.accountBalance)
        assertEquals("Учитывать P2P", UiCopy.Ru.includeP2p)
    }

    @Test
    fun `pending nothing found budget invalid and save are localized`() {
        val ru = UiCopy.Ru
        val en = UiCopy.En
        assertEquals("ожидание", ru.pending)
        assertEquals("pending", en.pending)
        assertEquals("Ничего не найдено", ru.nothingFound)
        assertEquals("Nothing found", en.nothingFound)
        assertEquals("Сохранить", ru.save)
        assertEquals("Save", en.save)
        assertEquals("Карта отклонила оплату. В расходы не входят.", ru.declinesHint)
        assertEquals("The card declined the charge. These are not expenses.", en.declinesHint)
        assertEquals(
            "Повторные списания раз в ~месяц и покупки из «Подписки»: стриминг, софт и медиа.",
            ru.subscriptionsHint
        )
        assertEquals(
            "Monthly repeats, plus purchases filed under Subscriptions, streaming, software, or media.",
            en.subscriptionsHint
        )
        assertEquals("Нет подписок", ru.noSubscriptions)
        assertEquals("No subscriptions", en.noSubscriptions)
        assertEquals("упрощённый", ru.simplified)
        assertEquals("simple", en.simplified)
        assertEquals("полный", ru.detailed)
        assertEquals("full", en.detailed)
        assertEquals("Таблица всех операций: дата, мерчант, сумма, категория.", ru.exportCsvHint)
        assertEquals("Для импорта в Koinly: покупки, возвраты и пополнения.", ru.exportKoinlyHint)
    }

    @Test
    fun `credentials lock and search filter labels are localized`() {
        val ru = UiCopy.Ru
        val en = UiCopy.En
        assertEquals("CardTrack", ru.appTitle)
        assertEquals("CardTrack", en.appTitle)
        assertEquals("API-ключ", ru.apiKeyLabel)
        assertEquals("API Key", en.apiKeyLabel)
        assertEquals("Секрет", ru.apiSecretLabel)
        assertEquals("Secret", en.apiSecretLabel)
        assertEquals("Все", ru.filterAll)
        assertEquals("All", en.filterAll)
        assertEquals("Покупки", ru.filterPurchases)
        assertEquals("Purchases", en.filterPurchases)
        assertEquals("От", ru.amountMin)
        assertEquals("Min", en.amountMin)
        assertEquals("До", ru.amountMax)
        assertEquals("Max", en.amountMax)
        assertEquals("кэшбек 0.20 USD", ru.cashbackLine.format("0.20", "USD"))
        assertEquals("cashback 0.20 USD", en.cashbackLine.format("0.20", "USD"))
        assertEquals("7 дней", ru.last7Days)
        assertEquals("7 days", en.last7Days)
        assertEquals("30 дней", ru.last30Days)
        assertEquals("30 days", en.last30Days)
        assertEquals("Даты", ru.customPeriod)
        assertEquals("Dates", en.customPeriod)
        assertEquals("Отмена", ru.cancel)
        assertEquals("Cancel", en.cancel)
        assertEquals("Сумма", ru.searchTotal)
        assertEquals("Total", en.searchTotal)
        assertEquals("Фильтры", ru.filters)
        assertEquals("Filters", en.filters)
        assertEquals("Сегодня", ru.today)
        assertEquals("Today", en.today)
        assertEquals("Вчера", ru.yesterday)
        assertEquals("Yesterday", en.yesterday)
        assertEquals("Потяни вниз, чтобы синхронизировать", ru.pullToSync)
        assertEquals("Pull down to sync", en.pullToSync)
        assertEquals("Сбросить фильтры", ru.resetFilters)
        assertEquals("Reset filters", en.resetFilters)
        assertEquals("Заменить ключ", ru.replaceKey)
        assertEquals("Replace key", en.replaceKey)
        assertTrue(ru.keyHintShort.contains("Read-only"))
        assertTrue(en.keyHintShort.contains("read-only"))
        assertEquals("Обновлено только что", ru.lastSyncJustNow)
        assertEquals("Updated just now", en.lastSyncJustNow)
        assertEquals("Обновлено в 12:00", ru.lastSyncAt("12:00"))
        assertEquals("Updated at 12:00", en.lastSyncAt("12:00"))
        assertEquals("Открыть", ru.openLock)
        assertEquals("Unlock", en.openLock)
        assertEquals("Ключ и история на этом телефоне", ru.lockHint)
        assertEquals("Key and history stay on this phone", en.lockHint)
        assertEquals("Подтвердите отпечаток или код устройства", ru.lockUnlockBody)
        assertEquals("Confirm fingerprint or device passcode", en.lockUnlockBody)
        assertEquals("Подготовка…", ru.lockPreparing)
        assertEquals("Preparing…", en.lockPreparing)
        assertEquals("Оформление", ru.appearance)
        assertEquals("Appearance", en.appearance)
        assertEquals("Экспорт", ru.export)
        assertEquals("Export", en.export)
    }

    @Test
    fun `key guide copy explains how to create a read-only key and why it is safe`() {
        val ru = UiCopy.Ru
        val en = UiCopy.En
        assertEquals("Как получить ключ", ru.keyGuideTitle)
        assertEquals("How to get a key", en.keyGuideTitle)
        assertEquals(
            "Ключ остаётся на телефоне (Android Keystore). Сервера у приложения нет. Read-only не умеет торговать и выводить.",
            ru.keySafety
        )
        assertEquals(
            "The key stays on this phone (Android Keystore). There is no app server. Read-only cannot trade or withdraw.",
            en.keySafety
        )
        assertEquals(8, ru.keyGuideSteps.size)
        assertEquals(8, en.keyGuideSteps.size)
        val ruGuide = ru.keyGuideSteps.joinToString("\n")
        val enGuide = en.keyGuideSteps.joinToString("\n")
        listOf("сайте Bybit", "48 часов", "основной аккаунт", "api-management", "Create New Key",
            "HMAC", "Read-only", "Bybit Card", "Account Transfer", "Withdraw", "IP", "2FA").forEach {
            assertTrue(ruGuide, ruGuide.contains(it))
        }
        listOf("Bybit website", "48 hours", "main account", "api-management", "Create New Key",
            "HMAC", "Read-only", "Bybit Card", "Account Transfer", "Withdraw", "IP", "2FA").forEach {
            assertTrue(enGuide, enGuide.contains(it))
        }
    }
}
