package com.baytna.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baytna.app.AppContainer
import com.baytna.app.data.db.ActivityEntity
import com.baytna.app.data.db.BudgetAllocationEntity
import com.baytna.app.data.db.GoalContributionEntity
import com.baytna.app.data.db.RecurringRuleEntity
import com.baytna.app.data.db.SavingsGoalEntity
import com.baytna.app.data.repo.toMovement
import com.baytna.domain.money.Money
import com.baytna.domain.planning.GoalProgress
import com.baytna.domain.planning.SavingsMath
import java.time.LocalDate
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

data class PlanningState(
    val goals: List<SavingsGoalEntity> = emptyList(),
    val contributions: List<GoalContributionEntity> = emptyList(),
    val rules: List<RecurringRuleEntity> = emptyList(),
    val activity: List<ActivityEntity> = emptyList(),
) {
    val contributionsByGoal: Map<String, List<GoalContributionEntity>> by lazy { contributions.groupBy { it.goalId } }

    fun progress(goal: SavingsGoalEntity, today: LocalDate): GoalProgress = SavingsMath.progress(
        Money(goal.targetMinor),
        contributionsByGoal[goal.id].orEmpty().map { it.toMovement() },
        today,
        goal.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    )

    /** Active rules with an open occurrence on or before [until]. */
    fun dueBy(until: LocalDate): List<RecurringRuleEntity> =
        rules.filter { it.isActive && !LocalDate.parse(it.nextDueDate).isAfter(until) }
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

    val planning: StateFlow<PlanningState> = repo.observeHousehold()
        .flatMapLatest { h ->
            if (h == null) flowOf(PlanningState())
            else combine(
                repo.observeGoals(h.id),
                repo.observeContributions(h.id),
                repo.observeRecurring(h.id),
                repo.observeActivity(h.id),
            ) { g, c, r, a -> PlanningState(g, c, r, a) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlanningState())

    /** Turns daily reminders on or off; when turning on, shows anything already due right away. */
    fun setReminders(enabled: Boolean) = viewModelScope.launch {
        container.settings.setRemindersEnabled(enabled)
        container.reminders.schedule(enabled)
        if (enabled) runCatching { container.reminders.run() }
    }

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
