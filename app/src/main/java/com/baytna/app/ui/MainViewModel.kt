package com.baytna.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baytna.app.AppContainer
import com.baytna.app.data.db.BudgetAllocationEntity
import com.baytna.app.data.db.BudgetEntity
import com.baytna.app.data.db.MemberEntity
import com.baytna.app.data.prefs.Settings
import com.baytna.app.data.repo.BaytnaRepository
import com.baytna.app.data.repo.HouseholdSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

sealed interface AppState {
    data object Loading : AppState
    data object NeedsOnboarding : AppState
    data class Ready(
        val snapshot: HouseholdSnapshot,
        val settings: Settings,
        val me: MemberEntity,
        val pendingSync: Int,
    ) : AppState
}

data class BudgetState(val budget: BudgetEntity, val allocations: List<BudgetAllocationEntity>)

/** Activity-scoped view model: every screen reads the same Room-backed snapshot. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(val container: AppContainer) : ViewModel() {
    val repo: BaytnaRepository = container.repository
    val month: StateFlow<YearMonth> = container.selectedMonth

    private val errorChannel = Channel<Throwable>(Channel.BUFFERED)
    val errors = errorChannel.receiveAsFlow()

    val state: StateFlow<AppState> = combine(
        repo.observeSnapshot(),
        container.settings.settings,
        repo.observePendingSyncCount(),
    ) { snapshot, settings, pending ->
        if (snapshot == null) {
            AppState.NeedsOnboarding
        } else {
            val me = snapshot.members.firstOrNull { it.id == settings.currentMemberId } ?: snapshot.members.firstOrNull()
            if (me == null) AppState.NeedsOnboarding else AppState.Ready(snapshot, settings, me, pending)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)

    val budget: StateFlow<BudgetState?> = combine(repo.observeHousehold(), month) { h, m -> h to m }
        .flatMapLatest { (h, m) ->
            if (h == null) flowOf(null)
            else repo.observeMonthlyBudget(h.id, m).flatMapLatest { b ->
                if (b == null) flowOf(null) else repo.observeAllocations(b.id).map { BudgetState(b, it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setMonth(m: YearMonth) {
        container.selectedMonth.value = m
    }

    /** Runs a repository command; failures are surfaced as a snackbar, success calls [onSuccess] on the main thread. */
    fun launch(onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}, block: suspend BaytnaRepository.() -> Unit) {
        viewModelScope.launch {
            try {
                repo.block()
                onSuccess()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                onFailure()
                errorChannel.send(t)
            }
        }
    }

    fun setCurrentMember(id: String) = viewModelScope.launch { container.settings.setCurrentMember(id) }

    fun setTheme(mode: com.baytna.app.data.prefs.ThemeMode) = viewModelScope.launch { container.settings.setThemeMode(mode) }
}
