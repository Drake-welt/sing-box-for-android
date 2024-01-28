package io.drake.sfa.database.preference

import androidx.preference.PreferenceDataStore

interface OnPreferenceDataStoreChangeListener {
    fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String)
}
