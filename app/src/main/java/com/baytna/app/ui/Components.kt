package com.baytna.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baytna.app.R
import com.baytna.app.data.db.TransactionEntity
import com.baytna.app.data.repo.HouseholdSnapshot
import com.baytna.app.data.repo.ValidationException
import com.baytna.app.data.repo.VersionConflictException
import com.baytna.app.ui.theme.LocalFinanceColors
import com.baytna.domain.finance.ValidationError
import com.baytna.domain.model.BudgetWarning
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.Money
import java.time.YearMonth

@Composable
fun MonthSelector(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
        }
        Text(Fmt.monthTitle(month, currentLocale()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { onChange(month.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
        }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun warningColor(w: BudgetWarning): Color = when (w) {
    BudgetWarning.NONE -> MaterialTheme.colorScheme.primary
    BudgetWarning.AT_75 -> LocalFinanceColors.current.warning
    BudgetWarning.AT_90 -> LocalFinanceColors.current.warning
    BudgetWarning.OVER_100 -> LocalFinanceColors.current.expense
}

@Composable
fun warningLabel(w: BudgetWarning): String? = when (w) {
    BudgetWarning.NONE -> null
    BudgetWarning.AT_75 -> stringResource(R.string.budget_warn_75)
    BudgetWarning.AT_90 -> stringResource(R.string.budget_warn_90)
    BudgetWarning.OVER_100 -> stringResource(R.string.budget_warn_100)
}

@Composable
fun BudgetProgress(percent: Long, warning: BudgetWarning, modifier: Modifier = Modifier) {
    val label = warningLabel(warning)
    LinearProgressIndicator(
        progress = { (percent.coerceIn(0, 100)) / 100f },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .semantics { contentDescription = (label ?: "") + " " + percent + "%" },
        color = warningColor(warning),
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        drawStopIndicator = {},
    )
}

@Composable
fun amountColor(type: TransactionType): Color = when (type) {
    TransactionType.INCOME, TransactionType.REFUND -> LocalFinanceColors.current.income
    TransactionType.EXPENSE -> LocalFinanceColors.current.expense
    TransactionType.TRANSFER -> LocalFinanceColors.current.transfer
}

/** Red below zero, green above, plain text at zero (a zero is neither a gain nor a loss). */
@Composable
fun netColor(net: Money): Color = when {
    net.isNegative -> LocalFinanceColors.current.expense
    net.isPositive -> LocalFinanceColors.current.income
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
fun typeLabel(type: TransactionType): String = stringResource(
    when (type) {
        TransactionType.INCOME -> R.string.type_income
        TransactionType.EXPENSE -> R.string.type_expense
        TransactionType.TRANSFER -> R.string.type_transfer
        TransactionType.REFUND -> R.string.type_refund
    },
)

@Composable
fun TransactionRow(tx: TransactionEntity, snapshot: HouseholdSnapshot, onClick: () -> Unit) {
    val category = tx.categoryId?.let { snapshot.categoriesById[it] }
    val account = snapshot.accountsById[tx.accountId]
    val destination = tx.destinationAccountId?.let { snapshot.accountsById[it] }
    val payer = snapshot.membersById[tx.paidByUserId]?.displayName ?: ""
    val ownerName = tx.personalOwnerUserId?.let { snapshot.membersById[it]?.displayName }
    val deleted = tx.deletedAt != null
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = category?.colorValue() ?: LocalFinanceColors.current.transfer
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(
                if (tx.type == TransactionType.TRANSFER) Icons.Filled.SwapHoriz else CategoryIcons.of(category?.icon ?: "other"),
                contentDescription = null, tint = tint, modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = when {
                tx.type == TransactionType.TRANSFER -> stringResource(R.string.transfer_from_to, account?.name ?: "", destination?.name ?: "")
                category != null -> category.displayName()
                else -> typeLabel(tx.type)
            }
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val ownership = if (tx.type == TransactionType.TRANSFER) null else if (tx.ownership == Ownership.SHARED) {
                stringResource(R.string.shared)
            } else stringResource(R.string.personal_for, ownerName ?: "")
            val parts = listOfNotNull(
                stringResource(R.string.paid_by, payer),
                ownership,
                if (tx.type != TransactionType.TRANSFER) account?.name else null,
                tx.note.takeIf { it.isNotBlank() },
            )
            Text(
                parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (deleted) Text(stringResource(R.string.deleted_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.width(8.dp))
        val sign = when (tx.type) {
            TransactionType.INCOME, TransactionType.REFUND -> Money(tx.amountMinor)
            TransactionType.EXPENSE -> Money(-tx.amountMinor)
            TransactionType.TRANSFER -> Money(tx.amountMinor)
        }
        Text(
            money(sign, signed = tx.type != TransactionType.TRANSFER),
            color = if (deleted) MaterialTheme.colorScheme.onSurfaceVariant else amountColor(tx.type),
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
        )
    }
}

fun errorMessage(context: Context, t: Throwable): String = when (t) {
    is VersionConflictException -> context.getString(R.string.error_conflict)
    is ValidationException -> t.errors.joinToString("\n") { context.getString(validationRes(it)) }
    else -> context.getString(R.string.error_generic, t.message ?: t.javaClass.simpleName)
}

fun validationRes(e: ValidationError): Int = when (e) {
    ValidationError.AMOUNT_NOT_POSITIVE -> R.string.err_amount
    ValidationError.ACCOUNT_REQUIRED -> R.string.err_account
    ValidationError.DESTINATION_REQUIRED -> R.string.err_destination
    ValidationError.SAME_SOURCE_AND_DESTINATION -> R.string.err_same_account
    ValidationError.DESTINATION_NOT_ALLOWED -> R.string.err_destination
    ValidationError.CATEGORY_REQUIRED -> R.string.err_category
    ValidationError.CATEGORY_TYPE_MISMATCH -> R.string.err_category
    ValidationError.CATEGORY_NOT_ALLOWED_FOR_TRANSFER -> R.string.err_category
    ValidationError.PERSONAL_OWNER_REQUIRED -> R.string.err_owner
    ValidationError.ORIGINAL_REQUIRED_FOR_REFUND -> R.string.err_refund_original
    ValidationError.ORIGINAL_NOT_EXPENSE -> R.string.err_refund_original
    ValidationError.REFUND_EXCEEDS_ORIGINAL -> R.string.err_refund_cap
    ValidationError.HAS_ACTIVE_REFUNDS -> R.string.err_has_refunds
    ValidationError.INVALID_REFERENCE -> R.string.err_reference
    ValidationError.NAME_REQUIRED -> R.string.err_name
    ValidationError.WITHDRAWAL_EXCEEDS_SAVED -> R.string.err_withdrawal
}
