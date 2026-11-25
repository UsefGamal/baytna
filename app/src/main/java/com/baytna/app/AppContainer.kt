package com.baytna.app

import android.content.Context
import com.baytna.app.data.db.BaytnaDatabase
import com.baytna.app.data.prefs.SettingsStore
import com.baytna.app.data.repo.BaytnaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.YearMonth
import com.baytna.domain.time.CAIRO

/** Manual dependency container. Small enough that a DI framework is not needed yet. */
class AppContainer(context: Context) {
    val database = BaytnaDatabase.build(context)
    val repository = BaytnaRepository(database)
    val settings = SettingsStore(context)

    /** Month selected on Home/Transactions/Budgets/Insights, shared across tabs. */
    val selectedMonth = MutableStateFlow(YearMonth.now(CAIRO))
}
