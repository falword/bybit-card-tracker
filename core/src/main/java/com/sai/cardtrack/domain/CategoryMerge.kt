package com.sai.cardtrack.domain

object CategoryMerge {
    fun resolve(existing: Transaction?, draft: TransactionDraft): Pair<String?, CategoryOrigin> {
        if (existing == null) {
            return draft.categoryId to draft.categoryOrigin
        }
        if (existing.categoryOrigin == CategoryOrigin.User) {
            return existing.categoryId to CategoryOrigin.User
        }
        if (draft.categoryOrigin == CategoryOrigin.Mcc && draft.categoryId != null) {
            return draft.categoryId to CategoryOrigin.Mcc
        }
        return existing.categoryId to existing.categoryOrigin
    }
}
