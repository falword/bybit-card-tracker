package com.sai.cardtrack.ui

import com.sai.cardtrack.domain.ExpenseCategories
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class UiCopy(val locale: AppLocale) {
    private val javaLocale: Locale = if (locale == AppLocale.En) Locale.ENGLISH else Locale("ru")
    private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", javaLocale)
    private val shortMonthFormatter = DateTimeFormatter.ofPattern("LLL", javaLocale)

    val appTitle: String get() = "CardTrack"
    val apiKeyLabel: String get() = t("API-ключ", "API Key")
    val apiSecretLabel: String get() = t("Секрет", "Secret")
    val filterAll: String get() = t("Все", "All")
    val filterPurchases: String get() = t("Покупки", "Purchases")
    val amountMin: String get() = t("От", "Min")
    val amountMax: String get() = t("До", "Max")
    val last7Days: String get() = t("7 дней", "7 days")
    val last30Days: String get() = t("30 дней", "30 days")
    val customPeriod: String get() = t("Даты", "Dates")
    val cancel: String get() = t("Отмена", "Cancel")
    val searchTotal: String get() = t("Сумма", "Total")
    val settings: String get() = t("Настройки", "Settings")
    val search: String get() = t("Поиск", "Search")
    val searchHint: String get() = t("Мерчант, сумма, MCC, id", "Merchant, amount, MCC, id")
    val filters: String get() = t("Фильтры", "Filters")
    val today: String get() = t("Сегодня", "Today")
    val yesterday: String get() = t("Вчера", "Yesterday")
    val pullToSync: String get() = t("Потяни вниз, чтобы синхронизировать", "Pull down to sync")
    val resetFilters: String get() = t("Сбросить фильтры", "Reset filters")
    val replaceKey: String get() = t("Заменить ключ", "Replace key")
    val keyHintShort: String get() = t(
        "Read-only ключ основного аккаунта: Bybit Card и Wallet (Account Transfer).",
        "Main-account read-only key: Bybit Card and Wallet (Account Transfer)."
    )
    val exportCsvHint: String get() = t(
        "Таблица всех операций: дата, мерчант, сумма, категория.",
        "A table of every operation: date, merchant, amount, category."
    )
    val exportKoinlyHint: String get() = t(
        "Для импорта в Koinly: покупки, возвраты и пополнения.",
        "For Koinly import: purchases, refunds, and top-ups."
    )
    val declinesHint: String get() = t(
        "Карта отклонила оплату. В расходы не входят.",
        "The card declined the charge. These are not expenses."
    )
    val subscriptionsHint: String get() = t(
        "Повторные списания раз в ~месяц и покупки из «Подписки»: стриминг, софт и медиа.",
        "Monthly repeats, plus purchases filed under Subscriptions, streaming, software, or media."
    )
    val bybitKey: String get() = t("Ключ Bybit", "Bybit key")
    val back: String get() = t("Назад", "Back")
    val keyHint: String get() = t(
        "Ключ только с основного аккаунта, Read-only. Отметь Bybit Card и Wallet (Account Transfer). Без торговли, вывода и субаккаунта.",
        "Use a main-account read-only key. Enable Bybit Card and Wallet (Account Transfer). No trading, withdraw, or subaccount."
    )
    val keySafety: String get() = t(
        "Ключ остаётся на телефоне (Android Keystore). Сервера у приложения нет. Read-only не умеет торговать и выводить.",
        "The key stays on this phone (Android Keystore). There is no app server. Read-only cannot trade or withdraw."
    )
    val keyGuideTitle: String get() = t("Как получить ключ", "How to get a key")
    val keyGuideSteps: List<String>
        get() = listOf(
            t(
                "Ключ создаётся только на сайте Bybit в браузере — в приложении Bybit этого раздела нет. Если аккаунт младше 48 часов, Bybit может не дать создать ключ.",
                "Create the key on the Bybit website in a browser — the Bybit app has no API section. If the account is younger than 48 hours, Bybit may block key creation."
            ),
            t(
                "Войди в основной аккаунт (не субаккаунт). Справа сверху открой профиль → API или страницу bybit.com/app/user/api-management.",
                "Sign in to the main account (not a subaccount). Open the profile icon → API, or bybit.com/app/user/api-management."
            ),
            t(
                "Нажми Create New Key.",
                "Tap Create New Key."
            ),
            t(
                "Выбери System-generated API Keys (HMAC). Не RSA и не стороннее приложение.",
                "Choose System-generated API Keys (HMAC). Not RSA and not a third-party app."
            ),
            t(
                "Поставь Read-only.",
                "Set Read-only."
            ),
            t(
                "В правах отметь Bybit Card и Wallet → Account Transfer. Остальное не включай: без торговли, Withdraw и субаккаунтов.",
                "Enable Bybit Card and Wallet → Account Transfer. Leave the rest off: no trading, Withdraw, or subaccounts."
            ),
            t(
                "IP restriction не ставь: на телефоне адрес меняется, и Bybit отклонит ключ.",
                "Leave IP restriction empty: a phone IP changes, and Bybit will reject the key."
            ),
            t(
                "Подтверди 2FA. Сразу скопируй API Key и Secret — секрет показывают один раз. Вставь оба поля ниже.",
                "Confirm 2FA. Copy the API Key and Secret right away — the secret is shown once. Paste both fields below."
            )
        )
    val lastSyncPrefix: String get() = t("Последняя синхронизация: ", "Last sync: ")
    val checkAndSignIn: String get() = t("Проверить и войти", "Check and sign in")
    val saveKey: String get() = t("Сохранить ключ", "Save key")
    val openLock: String get() = t("Открыть", "Unlock")
    val lockHint: String get() = t(
        "Ключ и история на этом телефоне",
        "Key and history stay on this phone"
    )
    val lockUnlockBody: String get() = t(
        "Подтвердите отпечаток или код устройства",
        "Confirm fingerprint or device passcode"
    )
    val lockPreparing: String get() = t("Подготовка…", "Preparing…")
    val appearance: String get() = t("Оформление", "Appearance")
    val export: String get() = t("Экспорт", "Export")
    val dbOpenFailed: String get() = t(
        "Не удалось открыть базу. Удали данные приложения.",
        "Could not open the database. Clear app data."
    )
    val keyProtectionReset: String get() = t(
        "Защита ключа сброшена. Введи ключ Bybit снова.",
        "Device key protection was reset. Enter the Bybit key again."
    )
    val lockScreenRequired: String get() = t(
        "Сначала поставь PIN, пароль или отпечаток на телефоне. Без защиты экрана ключ Bybit сохранить нельзя.",
        "Set a PIN, password, or fingerprint on this phone first. CardTrack will not store a Bybit key without a lock screen."
    )
    val exportCsv: String get() = t("Экспорт CSV", "Export CSV")
    val exportKoinly: String get() = t("Экспорт Koinly", "Export Koinly")
    val wipe: String get() = t("Удалить ключ и данные", "Delete key and data")
    val wipeConfirm: String get() = t(
        "Удалить ключ и все операции с этого устройства?",
        "Delete the key and all transactions from this device?"
    )
    val wipeDone: String get() = t(
        "Ключ и история удалены с устройства. Отзови ключ в Bybit → API.",
        "The key and history were deleted on this device. Revoke the key in Bybit → API."
    )
    val language: String get() = t("Язык", "Language")
    val theme: String get() = t("Тема", "Theme")
    val system: String get() = t("Система", "System")
    val russian: String get() = "Русский"
    val english: String get() = "English"
    val dark: String get() = t("Тёмная", "Dark")
    val light: String get() = t("Светлая", "Light")
    val previousMonth: String get() = t("Предыдущий месяц", "Previous month")
    val nextMonth: String get() = t("Следующий месяц", "Next month")
    val transactions: String get() = t("Операции", "Transactions")
    val noTransactions: String get() = t("Пока нет операций", "No transactions yet")
    val nothingFound: String get() = t("Ничего не найдено", "Nothing found")
    val pending: String get() = t("ожидание", "pending")
    val save: String get() = t("Сохранить", "Save")
    val pickType: String get() = t("выбери тип", "pick a type")
    val backToCategories: String get() = t("назад к категориям", "back to categories")
    val generalCategory: String get() = t("Общая", "General")
    val accountBalance: String get() = t("Баланс счёта", "Account balance")
    val includeP2p: String get() = t("Учитывать P2P", "Include P2P")
    val includeP2pHint: String get() = t(
        "Покупки и продажи P2P в доходах и расходах. Отмены скрыты и взаимозачитываются. По умолчанию выкл.",
        "Count P2P buys and sells in income and expenses. Cancels stay hidden and net out. Off by default."
    )
    val monthTotal: String get() = t("Итог месяца", "Month total")
    val expenses: String get() = t("Расходы", "Expenses")
    val income: String get() = t("Доходы", "Income")
    val cashback: String get() = t("Кэшбек", "Cashback")
    val fees: String get() = t("Комиссии", "Fees")
    val declines: String get() = t("Отказы", "Declines")
    val noDeclines: String get() = t("Нет отказов", "No declines")
    val noDeclineReason: String get() = t("без причины", "no reason")
    val subscriptions: String get() = t("Подписки", "Subscriptions")
    val subscriptionsThisMonth: String get() = t("Подписки за месяц", "Subscriptions this month")
    fun subscriptionsShareOfExpenses(percent: String): String {
        return t("$percent от расходов", "$percent of expenses")
    }
    val noSubscriptions: String get() = t("Нет подписок", "No subscriptions")
    val nextOn: String get() = t("следующий %s", "next %s")
    val commissionLine: String get() = t("%s %s комиссия", "%s %s fee")
    val cashbackLine: String get() = t("кэшбек %s %s", "cashback %s %s")
    val categories: String get() = t("Категории", "Categories")
    val categoriesHint: String get() = t("разбивка и сравнение месяцев", "breakdown and month compare")
    val openCategories: String get() = t("Открыть категории", "Open categories")
    val justNow: String get() = t("сейчас", "just now")
    val lastSyncJustNow: String get() = t("Обновлено только что", "Updated just now")
    fun lastSyncAt(whenLabel: String): String = t("Обновлено в $whenLabel", "Updated at $whenLabel")
    val noExpenses: String get() = t("нет расходов", "no expenses")
    val compare: String get() = t("сравнить", "compare")
    val toBreakdown: String get() = t("разбивка", "breakdown")
    val simplified: String get() = t("упрощённый", "simple")
    val detailed: String get() = t("полный", "full")
    val category: String get() = t("Категория", "Category")
    val uncategorized: String get() = t("без категории", "uncategorized")
    val topUp: String get() = t(ExpenseCategories.TOPUP_LABEL, "Top-up")
    val refund: String get() = t(ExpenseCategories.REFUND_LABEL, "Refund")
    val cashbackLabel: String get() = t(ExpenseCategories.CASHBACK_LABEL, "Cashback")
    val p2p: String get() = t(ExpenseCategories.P2P_LABEL, "P2P")
    val earn: String get() = t("Earn", "Earn")
    val syncInterrupted: String get() = t(
        "Синхронизация оборвалась. Потяни список вниз, чтобы повторить.",
        "Sync stopped. Pull the list down to try again."
    )
    val verifyInternalError: String get() = t(
        "Не удалось проверить ключ: внутренняя ошибка приложения.",
        "Could not verify the key: internal app error."
    )
    val notReadOnly: String get() = t(
        "Ключ не read-only. Создай новый ключ: только чтение, без торговли и вывода.",
        "This key is not read-only. Create a new key: read-only, no trading or withdraw."
    )
    val hasWithdraw: String get() = t(
        "У ключа есть право вывода. Сними Withdraw и оставь только чтение.",
        "This key can withdraw. Remove Withdraw and keep read-only only."
    )
    val subaccount: String get() = t(
        "Ключ с субаккаунта. Создай read-only ключ на основном аккаунте.",
        "This is a subaccount key. Create a read-only key on the main account."
    )
    val wallet: String get() = t(
        "У ключа нет права Wallet (переводы). Отметь Account Transfer, только чтение, без вывода.",
        "The key has no Wallet (transfer) permission. Enable Account Transfer, read-only, no withdraw."
    )
    val noCard: String get() = t(
        "У ключа нет права Card (BitCard). Создай read-only ключ на основном аккаунте и отметь Bybit Card.",
        "The key has no Card (BitCard) permission. Create a read-only key on the main account and enable Bybit Card."
    )
    val unmatchedIp: String get() = t(
        "IP не совпадает со списком в ключе. Убери IP restriction или добавь текущий адрес.",
        "IP does not match the list on the key. Remove the IP restriction or add this address."
    )
    val paramIllegal: String get() = t(
        "Bybit отклонил параметры запроса. Часто так отвечает запрос возвратов с фильтром дат.",
        "Bybit rejected the request parameters. Refund queries with a date filter often fail this way."
    )
    val clockSkew: String get() = t(
        "Время на телефоне расходится с Bybit. Включи автоматические дату и время.",
        "The phone clock is out of sync with Bybit. Turn on automatic date and time."
    )
    val invalidKey: String get() = t(
        "Ключ недействителен или создан для другого окружения (mainnet/testnet).",
        "The key is invalid or was created for another environment (mainnet/testnet)."
    )
    val badSignature: String get() = t(
        "Подпись не сошлась. Проверь Secret и что копируешь его целиком, без пробелов.",
        "The signature did not match. Check the Secret and that you copied it in full, with no spaces."
    )
    val rateLimit: String get() = t(
        "Bybit временно отклонил запрос из‑за лимита частоты. Подожди минуту и обнови.",
        "Bybit temporarily rejected the request because of a rate limit. Wait a minute and refresh."
    )
    val authRejected: String get() = t(
        "Bybit не принял аутентификацию ключа.",
        "Bybit rejected key authentication."
    )
    val accountRestricted: String get() = t(
        "Аккаунт ограничен. Проверь статус аккаунта в Bybit.",
        "The account is restricted. Check the account status in Bybit."
    )
    val regionBlocked: String get() = t(
        "Bybit недоступен в этом регионе. Попробуй другую сеть или отключи VPN.",
        "Bybit is not available in this region. Try another network or turn off VPN."
    )
    val serverError: String get() = t(
        "Сервер Bybit вернул ошибку. Повтори позже.",
        "The Bybit server returned an error. Try again later."
    )
    val ipRateLimit: String get() = t(
        "Слишком много запросов с этого IP. Подожди и обнови.",
        "Too many requests from this IP. Wait and refresh."
    )
    val keyExpired: String get() = t(
        "Срок API-ключа истёк. Создай новый read-only ключ с правом Card.",
        "The API key has expired. Create a new read-only key with Card permission."
    )
    val genericKeyFail: String get() = t(
        "Bybit не принял ключ. Проверь Key, Secret и право Card.",
        "Bybit rejected the key. Check Key, Secret, and Card permission."
    )
    val invalidJson: String get() = t(
        "Bybit вернул непонятный ответ, не JSON. Часто это блок сети, VPN или антивирус.",
        "Bybit returned a response that is not JSON. Often a network block, VPN, or antivirus."
    )
    val http401: String get() = t(
        "Bybit не принял ключ (HTTP 401). Проверь Key и Secret.",
        "Bybit rejected the key (HTTP 401). Check Key and Secret."
    )
    val http403: String get() = t(
        "Bybit отказал в доступе (HTTP 403). Часто это регион, VPN или лимит IP.",
        "Bybit denied access (HTTP 403). Often region, VPN, or an IP limit."
    )
    val http404: String get() = t(
        "Bybit не нашёл метод API (HTTP 404).",
        "Bybit could not find the API method (HTTP 404)."
    )
    val sslFail: String get() = t(
        "Защищённое соединение с Bybit не удалось. Проверь дату и время на телефоне.",
        "Secure connection to Bybit failed. Check the date and time on the phone."
    )
    val unknownHost: String get() = t(
        "Не удаётся найти сервер Bybit. Проверь интернет, DNS и доступ приложения к сети.",
        "Cannot find the Bybit server. Check internet, DNS, and that the app can use the network."
    )
    val timeout: String get() = t(
        "Bybit не ответил вовремя. Повтори через минуту.",
        "Bybit did not respond in time. Try again in a minute."
    )
    val connectFail: String get() = t(
        "Не удалось подключиться к Bybit. Проверь интернет.",
        "Could not connect to Bybit. Check your internet."
    )
    val noNetwork: String get() = t(
        "Нет соединения с Bybit. Проверь интернет и что приложению разрешена сеть.",
        "No connection to Bybit. Check internet and that the app can use the network."
    )
    val syncUnknown: String get() = t(
        "Bybit отклонил запрос. Обнови позже или проверь ключ.",
        "Bybit rejected the request. Refresh later or check the key."
    )

    fun httpStatus(code: Int): String = t("Bybit недоступен (HTTP $code).", "Bybit is unavailable (HTTP $code).")

    fun rejectedCode(code: Int): String = t(
        "Bybit отклонил запрос (код $code).",
        "Bybit rejected the request (code $code)."
    )

    fun monthLabel(month: YearMonth): String {
        return month.format(monthFormatter).replaceFirstChar { it.titlecase(javaLocale) }
    }

    fun shortMonth(month: YearMonth): String {
        return month.format(shortMonthFormatter).trimEnd('.')
    }

    fun categoryLabel(categoryId: String?): String {
        return ExpenseCategories.labelFor(categoryId, locale) ?: uncategorized
    }

    private fun t(ru: String, en: String): String = if (locale == AppLocale.En) en else ru

    companion object {
        val Ru: UiCopy = UiCopy(AppLocale.Ru)
        val En: UiCopy = UiCopy(AppLocale.En)
    }
}
