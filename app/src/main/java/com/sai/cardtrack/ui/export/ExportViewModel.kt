package com.sai.cardtrack.ui.export

import androidx.lifecycle.ViewModel
import com.sai.cardtrack.data.CredentialsStore
import com.sai.cardtrack.data.TransactionRepository
import com.sai.cardtrack.domain.CsvExport
import com.sai.cardtrack.ui.UiCopy
import kotlinx.coroutines.flow.first

class ExportViewModel(
    private val repository: TransactionRepository,
    private val copy: UiCopy = UiCopy.Ru,
    private val credentialsStore: CredentialsStore? = null
) : ViewModel() {
    suspend fun exportCsv(): String {
        val includeP2p = credentialsStore?.p2pAccountingEnabled() ?: false
        return CsvExport.table(repository.observeAll().first(), copy.locale, includeP2p)
    }

    suspend fun exportKoinly(): String {
        val includeP2p = credentialsStore?.p2pAccountingEnabled() ?: false
        return CsvExport.koinly(repository.observeAll().first(), includeP2p)
    }
}
