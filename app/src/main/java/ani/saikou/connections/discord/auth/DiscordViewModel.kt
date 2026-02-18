package ani.saikou.connections.discord.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import ani.saikou.connections.discord.rpc.RpcRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class DiscordViewModel(
    private val repository: DiscordRepository,
    private val rpcRepository: RpcRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscordUiState())
    val uiState: StateFlow<DiscordUiState> = _uiState.asStateFlow()



    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        loadDiscordUser()
        loadRpcPreference()
    }
    fun loadDiscordUser() {
        viewModelScope.launch {

            updateState { it.copy(isLoading = true) }

            val token = repository.getSavedToken()
            if (token == null) {
                updateState { it.copy(isLoggedIn = false, isLoading = false) }
                return@launch
            }


            val user = repository.fetchUserData(token)
            if (user?.id != null )  {
                updateState {
                    it.copy(
                        isLoggedIn = true,
                        username = user.globalName ?: user.username,
                        avatarUrl = user.getAvatarUrl(),
                        isLoading = false
                    )
                }
            } else {
                // Token might be expired or network failed
                updateState { it.copy(isLoading = false) }
            }
        }
    }



    private fun loadRpcPreference() {
        val rpcEnabled = rpcRepository.loadRpcPreference()
        updateState { it.copy(isRpcEnabled = rpcEnabled) }


    }

    fun setRpcEnabled(enabled: Boolean) {
        rpcRepository.saveRpcPreference(enabled)
        updateState { it.copy(isRpcEnabled = enabled) }

    }



    fun logout() {
        repository.removeSavedToken()
        updateState { it.copy(isLoggedIn = false, username = null, avatarUrl = null) }
    }

    private fun updateState(block: (DiscordUiState) -> DiscordUiState) {
        _uiState.update(block)
    }

    override fun onCleared() {
        super.onCleared()

    }
}