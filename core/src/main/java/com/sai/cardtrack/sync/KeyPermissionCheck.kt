package com.sai.cardtrack.sync

import com.sai.cardtrack.bybit.ApiKeyInfo
import com.sai.cardtrack.ui.AppLocale
import com.sai.cardtrack.ui.UiCopy

object KeyPermissionCheck {
    const val NOT_READONLY =
        "Ключ не read-only. Создай новый ключ: только чтение, без торговли и вывода."
    const val HAS_WITHDRAW =
        "У ключа есть право вывода. Сними Withdraw и оставь только чтение."
    const val SUBACCOUNT =
        "Ключ с субаккаунта. Создай read-only ключ на основном аккаунте."

    fun banner(info: ApiKeyInfo, locale: AppLocale = AppLocale.Ru): String? {
        val copy = UiCopy(locale)
        if (!info.isMaster) return copy.subaccount
        if (info.readOnly != 1) return copy.notReadOnly
        if (info.wallet.any { it.equals("Withdraw", ignoreCase = true) }) return copy.hasWithdraw
        if (info.bitCard.none { it.equals("BitCard", ignoreCase = true) }) {
            return bybitAccessBanner(10005, "permission denied", locale)
        }
        val days = info.deadlineDay
        if (info.ips.isEmpty() && days != null && days <= 0) {
            return bybitAccessBanner(33004, "", locale)
        }
        return null
    }
}
