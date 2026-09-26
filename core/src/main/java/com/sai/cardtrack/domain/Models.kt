package com.sai.cardtrack.domain

import com.sai.cardtrack.ui.AppLocale

enum class QueryType { Auth, Refund, Financial }

enum class TransactionKind { Expense, Income }

enum class TransactionSource { Purchase, Refund, TopUp, Cashback, Declined, P2P, P2PRefund, Earn }

enum class TransactionStatus { Pending, Success, Declined }

enum class CategoryOrigin { None, Mcc, User }

data class FeeBreakdown(
    val totalFees: String = "",
    val foreignTransactionFee: String = "",
    val withdrawalFee: String = "",
    val fxPad: String = "",
    val totalTax: String = "",
    val billAmount: String = "",
    val transactionAmount: String = "",
    val transactionCurrency: String = ""
) {
    fun feeSum(): java.math.BigDecimal {
        val parsed = totalFees.toBigDecimalOrNull()
            ?: return java.math.BigDecimal("0.00")
        return parsed.setScale(2, java.math.RoundingMode.HALF_UP)
    }
}

data class CategoryPickerChoice(
    val id: String,
    val label: String
)

data class ExpenseCategory(
    val id: String,
    val label: String,
    val parentId: String? = null,
    val labelEn: String = label
)

object ExpenseCategories {
    const val REFUND_LABEL: String = "Возврат"
    const val TOPUP_LABEL: String = "Пополнение"
    const val CASHBACK_LABEL: String = "Кэшбек"
    const val QR_LABEL: String = "Bybit Pay"
    const val P2P_LABEL: String = "P2P"
    const val EARN_LABEL: String = "Earn"

    val ALL: List<ExpenseCategory> = listOf(
        cat("food", "Еда", "Food"),
        cat("groceries", "Продукты", "Groceries", "food"),
        cat("dining", "Кафе и рестораны", "Cafes and restaurants", "food"),
        cat("coffee", "Кофейни", "Coffee shops", "food"),
        cat("food_delivery", "Доставка", "Delivery", "food"),
        cat("transport", "Транспорт", "Transport"),
        cat("taxi", "Такси", "Taxi", "transport"),
        cat("transit", "Общественный транспорт", "Public transport", "transport"),
        cat("fuel", "Бензин", "Fuel", "transport"),
        cat("parking", "Парковка", "Parking", "transport"),
        cat("shopping", "Покупки", "Shopping"),
        cat("clothes", "Одежда", "Clothes", "shopping"),
        cat("electronics", "Электроника", "Electronics", "shopping"),
        cat("home_goods", "Для дома", "Home", "shopping"),
        cat("marketplaces", "Маркетплейсы", "Marketplaces", "shopping"),
        cat("entertainment", "Развлечения", "Entertainment"),
        cat("events", "Кино и события", "Cinema and events", "entertainment"),
        cat("games", "Игры", "Games", "entertainment"),
        cat("hobbies", "Хобби", "Hobbies", "entertainment"),
        cat("subscriptions", "Подписки", "Subscriptions"),
        cat("streaming", "Стриминг", "Streaming", "subscriptions"),
        cat("software", "Софт и сервисы", "Software and services", "subscriptions"),
        cat("media", "Медиа", "Media", "subscriptions"),
        cat("health", "Здоровье", "Health"),
        cat("pharmacy", "Аптека", "Pharmacy", "health"),
        cat("clinic", "Клиники", "Clinics", "health"),
        cat("fitness", "Спорт", "Fitness", "health"),
        cat("comms", "Связь", "Communications"),
        cat("mobile", "Мобильная связь", "Mobile", "comms"),
        cat("internet", "Интернет", "Internet", "comms"),
        cat("housing", "Жильё", "Housing"),
        cat("rent", "Аренда", "Rent", "housing"),
        cat("utilities", "Коммуналка", "Utilities", "housing"),
        cat("home_repair", "Ремонт", "Repairs", "housing"),
        cat("travel", "Путешествия", "Travel"),
        cat("hotels", "Отели", "Hotels", "travel"),
        cat("tickets", "Билеты", "Tickets", "travel"),
        cat("tours", "Туры", "Tours", "travel"),
        cat("other", "Другое", "Other"),
        cat("gifts", "Подарки", "Gifts", "other"),
        cat("education", "Образование", "Education", "other"),
        cat("fees", "Комиссии", "Fees", "other")
    )

    val PARENTS: List<ExpenseCategory> = ALL.filter { it.parentId == null }

    fun find(id: String?): ExpenseCategory? {
        if (id == null) return null
        return ALL.firstOrNull { it.id == id }
    }

    fun childrenOf(parentId: String): List<ExpenseCategory> {
        return ALL.filter { it.parentId == parentId }
    }

    fun isSubscriptionsFamily(categoryId: String?): Boolean {
        if (categoryId == null) return false
        if (categoryId == "subscriptions") return true
        return find(categoryId)?.parentId == "subscriptions"
    }

    fun sheetChoices(browseParentId: String?): List<ExpenseCategory> {
        if (browseParentId == null) return PARENTS
        if (find(browseParentId) == null) return PARENTS
        return childrenOf(browseParentId)
    }

    fun pickerChoices(
        browseParentId: String?,
        locale: AppLocale,
        parentChoiceLabel: String
    ): List<CategoryPickerChoice> {
        val parent = find(browseParentId)
        if (parent == null) {
            return PARENTS.map { CategoryPickerChoice(it.id, it.labelFor(locale)) }
        }
        val children = childrenOf(parent.id).map { child ->
            CategoryPickerChoice(child.id, child.labelFor(locale))
        }
        return listOf(CategoryPickerChoice(parent.id, parentChoiceLabel)) + children
    }

    fun labelFor(categoryId: String?, locale: AppLocale = AppLocale.Ru): String? {
        val category = find(categoryId) ?: return null
        val own = category.labelFor(locale)
        val parent = find(category.parentId) ?: return own
        return parent.labelFor(locale) + " · " + own
    }

    private fun ExpenseCategory.labelFor(locale: AppLocale): String {
        return if (locale == AppLocale.En) labelEn else label
    }

    private fun cat(id: String, label: String, labelEn: String, parentId: String? = null): ExpenseCategory {
        return ExpenseCategory(id, label, parentId, labelEn)
    }
}

data class BybitAssetRecord(
    val txnId: String?,
    val orderNo: String?,
    val side: String,
    val paidAmount: String,
    val paidCurrency: String,
    val merchName: String,
    val txnCreate: Long,
    val status: String,
    val tradeStatus: String,
    val basicAmount: String = "",
    val basicCurrency: String = "",
    val billAmount: String = "",
    val paidFiat: String = "",
    val transactionAmount: String = "",
    val transactionCurrency: String = "",
    val totalFees: String = "",
    val foreignTransactionFee: String = "",
    val withdrawalFee: String = "",
    val fxPad: String = "",
    val totalTax: String = "",
    val declinedReason: String = "",
    val mccCode: String = "",
    val merchCategoryDesc: String = ""
)

data class BybitPointRecord(
    val bizId: String?,
    val transactionId: String?,
    val point: Int,
    val side: String,
    val type: String,
    val createTime: Long,
    val outOrderId: String? = null,
    val bizTxnId: String? = null
)

data class BybitTransfer(
    val transferId: String,
    val coin: String,
    val amount: String,
    val fromAccountType: String,
    val toAccountType: String,
    val timestamp: Long,
    val status: String
)

data class BybitFundingRecord(
    val id: String,
    val currency: String,
    val ioDirection: String,
    val txnAmt: String,
    val createTime: Long,
    val showBusiType: String,
    val showBusiTypeEn: String,
    val description: String,
    val descriptionEn: String
)

data class BybitEarnYieldRecord(
    val id: String,
    val coin: String,
    val amount: String,
    val status: String,
    val createdAt: Long
)

data class CardMoneyAmount(
    val amount: String,
    val currency: String
)

data class TransactionDraft(
    val txnId: String,
    val orderNo: String?,
    val kind: TransactionKind,
    val paidAmount: String,
    val paidCurrency: String,
    val merchantName: String,
    val txnCreate: Long,
    val status: TransactionStatus,
    val bybitSide: String,
    val source: TransactionSource = TransactionSource.Purchase,
    val declinedReason: String = "",
    val mccCode: String = "",
    val merchCategoryDesc: String = "",
    val categoryId: String? = null,
    val categoryOrigin: CategoryOrigin = CategoryOrigin.None,
    val fees: FeeBreakdown = FeeBreakdown()
)

data class Transaction(
    val txnId: String,
    val orderNo: String?,
    val kind: TransactionKind,
    val paidAmount: String,
    val paidCurrency: String,
    val merchantName: String,
    val txnCreate: Long,
    val status: TransactionStatus,
    val bybitSide: String,
    val categoryId: String?,
    val syncedAt: Long,
    val source: TransactionSource = TransactionSource.Purchase,
    val categoryOrigin: CategoryOrigin = CategoryOrigin.None,
    val declinedReason: String = "",
    val mccCode: String = "",
    val merchCategoryDesc: String = "",
    val fees: FeeBreakdown = FeeBreakdown()
)

data class RewardSnapshot(
    val usedLimit: String,
    val limit: String,
    val unit: String,
    val tier: String,
    val autoCashback: Boolean,
    val availablePoint: String,
    val pendingPoint: String
)

data class FundBalanceSnapshot(
    val amount: String,
    val currency: String = "USD",
    val updatedAt: Long
)

sealed class ClassifyResult {
    data class Keep(val draft: TransactionDraft) : ClassifyResult()
    data object Skip : ClassifyResult()
}
