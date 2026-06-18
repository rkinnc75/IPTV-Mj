package com.iptvapp.ui

import androidx.lifecycle.ViewModel
import com.iptvapp.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    prefs: PreferencesManager
) : ViewModel() {
    val isLoggedIn = prefs.credentials.map { it.isLoggedIn }
}
