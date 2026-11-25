package com.baytna.app

import android.content.Context
import com.baytna.app.data.db.BaytnaDatabase
import com.baytna.app.data.prefs.SettingsStore
import com.baytna.app.data.repo.BaytnaRepository
import com.baytna.app.data.sync.FirebaseCloud
import com.baytna.app.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import com.baytna.app.notify.Reminders
import java.time.YearMonth
import com.baytna.domain.time.CAIRO

/** Manual dependency container. Small enough that a DI framework is not needed yet. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = BaytnaDatabase.build(context)
    val settings = SettingsStore(context)
    val repository = BaytnaRepository(database, actor = { settings.settings.first().currentMemberId })
    val reminders = Reminders(context, repository, settings)

    private val firebase = FirebaseCloud.createOrNull(context)
    val sync = SyncManager(context, database, settings, firebase, firebase, appScope)

    /** Month selected on Home/Transactions/Budgets/Insights, shared across tabs. */
    val selectedMonth = MutableStateFlow(YearMonth.now(CAIRO))
}
