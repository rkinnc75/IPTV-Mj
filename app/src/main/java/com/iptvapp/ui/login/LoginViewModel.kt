package com.iptvapp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvapp.data.api.XtreamUrlBuilder
import com.iptvapp.data.repository.XtreamRepository
import com.iptvapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: XtreamRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<Resource<Unit>?>(null)
    val loginState: StateFlow<Resource<Unit>?> = _loginState

    fun login(serverUrl: String, username: String, password: String) {
        // Validate at the ViewModel boundary (not only in the Activity): trim,
        // require all fields, and require a real http(s) URL with a host so a
        // value like "http://" or "https:// " can't reach authenticate().
        val url = serverUrl.trim()
        val user = username.trim()
        if (url.isBlank() || user.isBlank() || password.isBlank()) {
            _loginState.value = Resource.Error("Please fill in all fields")
            return
        }
        if (!XtreamUrlBuilder.isValidServerUrl(url)) {
            _loginState.value =
                Resource.Error("Enter a valid server URL, e.g. http://host:port")
            return
        }
        viewModelScope.launch {
            _loginState.value = Resource.Loading
            val result = repository.authenticate(url.trimEnd('/'), user, password)
            _loginState.value = when (result) {
                is Resource.Success -> Resource.Success(Unit)
                is Resource.Error -> Resource.Error(result.message)
                else -> Resource.Error("Unknown error")
            }
        }
    }

    fun resetState() {
        _loginState.value = null
    }
}
