package com.baytna.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baytna.app.BaytnaApp
import com.baytna.app.MainActivity
import com.baytna.app.R
import com.baytna.app.data.prefs.SettingsStore
import com.baytna.app.data.repo.BaytnaRepository
import com.baytna.app.data.repo.toAllocation
import com.baytna.app.data.repo.toLedger
import com.baytna.app.ui.Fmt
import com.baytna.app.ui.displayName
import com.baytna.domain.finance.BudgetMath
import com.baytna.domain.finance.CashFlowCalculator
import com.baytna.domain.model.BudgetWarning
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money
import com.baytna.domain.time.CAIRO
import com.baytna.domain.time.Period
import com.baytna.domain.time.budgetMonthOf
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** One reminder to show; [key] makes sure the same reminder is shown only once. */
data class Reminder(val key: String, val title: String, val text: String)

/**
 * Daily local reminders: recurring items due today or tomorrow (or overdue), and budget categories that reached
 * 90% or went over. Everything is computed from Room on the phone; nothing is sent to a server.
 */
class Reminders(
    private val context: Context,
    private val repo: BaytnaRepository,
    private val settings: SettingsStore,
) {
    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Turns the daily check on or off. Safe to call repeatedly. */
    fun schedule(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val now = ZonedDateTime.now(CAIRO)
        var next = now.toLocalDate().atTime(LocalTime.of(9, 0)).atZone(CAIRO)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Works out today's reminders. Pure apart from reading Room, so it is unit tested. */
    suspend fun compute(today: LocalDate = LocalDate.now(CAIRO)): List<Reminder> {
        val h = repo.currentHousehold() ?: return emptyList()
        val out = mutableListOf<Reminder>()
        val locale = context.resources.configuration.locales[0]

        for (r in repo.dueRecurring(h.id, today.plusDays(1))) {
            val due = LocalDate.parse(r.nextDueDate)
            val amount = Fmt.money(context, Money(r.amountMinor), locale)
            val title = when {
                due.isBefore(today) -> context.getString(R.string.reminder_overdue, r.name)
                due == today -> context.getString(R.string.reminder_due_today, r.name)
                else -> context.getString(R.string.reminder_due_tomorrow, r.name)
            }
            val text = context.getString(
                if (r.type == TransactionType.INCOME) R.string.reminder_expected_income else R.string.reminder_expected_payment, amount,
            )
            out += Reminder("rec:${r.id}:${r.nextDueDate}", title, text)
        }

        val startDay = settings.settings.first().monthStartDay
        val month = budgetMonthOf(today, startDay)
        repo.budgetWithAllocations(h.id, month)?.let { (budget, allocations) ->
            val period = Period.month(month, startDay)
            val spending = CashFlowCalculator.spendingByCategory(
                repo.allTransactions(h.id).filter { it.deletedAt == null && period.contains(it.occurredAt) }.map { it.toLedger() },
            )
            val report = BudgetMath.report(Money(budget.planningIncomeMinor), allocations.map { it.toAllocation() }, spending)
            val categories = repo.categoriesById(h.id)
            for (line in report.lines) {
                val level = when (line.warning) {
                    BudgetWarning.OVER_100 -> 100
                    BudgetWarning.AT_90 -> 90
                    else -> continue
                }
                val name = line.allocation.categoryId?.let { categories[it] }?.displayName(context) ?: continue
                val title = if (level == 100) context.getString(R.string.reminder_budget_over, name)
                else context.getString(R.string.reminder_budget_90, name, line.percentUsed.toString())
                val text = context.getString(R.string.reminder_budget_left, Fmt.money(context, line.remaining, locale))
                out += Reminder("bud:$month:${line.allocation.id}:$level", title, text)
            }
        }
        return out
    }

    /** Shows reminders not shown before. Returns how many were posted. */
    @SuppressLint("MissingPermission") // checked by hasPermission() above the notify calls
    suspend fun run(today: LocalDate = LocalDate.now(CAIRO)): Int {
        if (!settings.settings.first().remindersEnabled || !hasPermission()) return 0
        val all = compute(today)
        val sent = settings.sentReminders.first()
        val fresh = all.filter { it.key !in sent }
        if (fresh.isEmpty()) return 0
        ensureChannel()
        val nm = NotificationManagerCompat.from(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        for (r in fresh) {
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(r.title)
                .setContentText(r.text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            try {
                nm.notify(r.key.hashCode(), n)
            } catch (e: SecurityException) {
                return 0
            }
        }
        // Keep only keys that can still come back, so the set stays small.
        settings.setSentReminders((sent + fresh.map { it.key }).filter { k -> all.any { it.key == k } }.toSet())
        return fresh.size
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    companion object {
        const val CHANNEL = "reminders"
        const val WORK = "baytna-reminders"
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as BaytnaApp).container.reminders.run()
        return Result.success()
    }
}
