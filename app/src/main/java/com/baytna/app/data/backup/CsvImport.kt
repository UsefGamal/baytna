package com.baytna.app.data.backup

import com.baytna.app.data.db.AccountEntity
import com.baytna.app.data.db.CategoryEntity
import com.baytna.app.data.db.MemberEntity
import com.baytna.app.data.repo.TransactionInput
import com.baytna.app.data.repo.noonCairo
import com.baytna.domain.model.CategoryType
import com.baytna.domain.model.Ownership
import com.baytna.domain.model.TransactionType
import com.baytna.domain.money.MoneyParser
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Why a CSV row was left out. */
enum class SkipReason {
    /** Date, type or amount is missing or cannot be read. */
    UNREADABLE,
    /** No active account with that name. */
    UNKNOWN_ACCOUNT,
    /** A different currency from the household's. */
    OTHER_CURRENCY,
    /** Refunds need the original expense, which a CSV cannot point at. */
    REFUND,
    /** Marked deleted in the file. */
    DELETED,
    /** Already on this phone (same id, or the same row imported before). */
    DUPLICATE,
}

/** Rows ready to save, plus counts of rows left out and why. */
data class ImportPlan(
    val rows: List<TransactionInput>,
    val skipped: Map<SkipReason, Int>,
    /** Rows whose category was not found and that go into "Other" instead. */
    val uncategorized: Int,
) {
    val skippedTotal get() = skipped.values.sum()
}

class CsvFormatException : Exception("Not a transactions CSV")

/**
 * Reads a transactions CSV (the app's own export, or any file with date, type, amount and account columns) and maps
 * each row onto this household by name. Nothing is written here; the caller saves [ImportPlan.rows] through the
 * repository so every import passes the same validation as a hand-entered transaction.
 */
object CsvImport {
    private val REQUIRED = listOf("date", "type", "amount", "account")
    private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    private val DATE_FORMATS = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("d/M/uuuu"),
        DateTimeFormatter.ofPattern("d-M-uuuu"),
        DateTimeFormatter.ofPattern("d.M.uuuu"),
    )

    fun plan(
        text: String,
        currency: String,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        /** Every name a category is known by (each language's display name, the stored name). */
        categoryNames: (CategoryEntity) -> Set<String>,
        members: List<MemberEntity>,
        me: String,
        existingIds: Set<String>,
    ): ImportPlan {
        val table = parse(text)
        if (table.isEmpty()) throw CsvFormatException()
        val header = table.first().map { it.trim().lowercase().replace(' ', '_') }
        if (!header.containsAll(REQUIRED)) throw CsvFormatException()
        fun List<String>.cell(name: String): String {
            val i = header.indexOf(name)
            return if (i < 0 || i >= size) "" else unescape(this[i].trim())
        }

        fun key(s: String) = s.trim().lowercase()
        val accountByName = accounts.filter { it.isActive }.associateBy { key(it.name) }
        val activeCategories = categories.filter { it.isActive }
        val categoryByName = buildMap {
            for (c in activeCategories) for (n in categoryNames(c) + c.name) if (n.isNotBlank()) putIfAbsent(key(n) to c.type, c)
        }
        val fallbackExpense = activeCategories.firstOrNull { it.systemKey == "other" }
            ?: activeCategories.firstOrNull { it.type == CategoryType.EXPENSE }
        val fallbackIncome = activeCategories.firstOrNull { it.systemKey == "other_income" }
            ?: activeCategories.firstOrNull { it.type == CategoryType.INCOME }
        val memberByName = members.associateBy { key(it.displayName) }

        val rows = mutableListOf<TransactionInput>()
        val skipped = mutableMapOf<SkipReason, Int>()
        fun skip(r: SkipReason) { skipped[r] = (skipped[r] ?: 0) + 1 }
        var uncategorized = 0
        val seen = mutableMapOf<String, Int>()
        val taken = existingIds.toMutableSet()

        for (line in table.drop(1)) {
            if (line.all { it.isBlank() }) continue
            if (line.cell("deleted").equals("true", ignoreCase = true)) { skip(SkipReason.DELETED); continue }
            val type = runCatching { TransactionType.valueOf(line.cell("type").uppercase()) }.getOrNull()
            val date = parseDate(line.cell("date"))
            val amount = MoneyParser.parse(line.cell("amount"))
            if (type == null || date == null || amount == null || amount.minor <= 0) { skip(SkipReason.UNREADABLE); continue }
            if (type == TransactionType.REFUND) { skip(SkipReason.REFUND); continue }
            val cur = line.cell("currency")
            if (cur.isNotEmpty() && !cur.equals(currency, ignoreCase = true)) { skip(SkipReason.OTHER_CURRENCY); continue }
            val account = accountByName[key(line.cell("account"))]
            if (account == null) { skip(SkipReason.UNKNOWN_ACCOUNT); continue }

            val isTransfer = type == TransactionType.TRANSFER
            val destination = if (isTransfer) accountByName[key(line.cell("destination_account"))] else null
            if (isTransfer && (destination == null || destination.id == account.id)) { skip(SkipReason.UNKNOWN_ACCOUNT); continue }

            var categoryId: String? = null
            if (!isTransfer) {
                val wanted = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
                val found = categoryByName[key(line.cell("category")) to wanted]
                val category = found ?: (if (wanted == CategoryType.INCOME) fallbackIncome else fallbackExpense)
                if (category == null) { skip(SkipReason.UNREADABLE); continue }
                if (found == null) uncategorized++
                categoryId = category.id
            }

            val payer = memberByName[key(line.cell("paid_by"))]?.id ?: me
            val personal = !isTransfer && line.cell("ownership").equals("PERSONAL", ignoreCase = true)
            val owner = if (personal) memberByName[key(line.cell("personal_owner"))]?.id ?: payer else null
            val note = line.cell("note")

            // The file's own id when it has one, otherwise one derived from the row, so importing a file twice adds nothing.
            val given = line.cell("id").takeIf { ID_PATTERN.matches(it) }
            val id = given ?: run {
                val content = listOf(date, type, amount.minor, account.id, destination?.id, categoryId, note).joinToString("|")
                val n = seen.merge(content, 1, Int::plus)!!
                "csv_" + sha256("$content|$n").take(24)
            }
            if (!taken.add(id)) { skip(SkipReason.DUPLICATE); continue }

            rows += TransactionInput(
                type = type,
                amount = amount,
                accountId = account.id,
                destinationAccountId = destination?.id,
                categoryId = categoryId,
                paidByUserId = payer,
                ownership = if (personal) Ownership.PERSONAL else Ownership.SHARED,
                personalOwnerUserId = owner,
                occurredAt = noonCairo(date),
                note = note,
                originalTransactionId = null,
                actorUserId = me,
                fixedId = id,
            )
        }
        return ImportPlan(rows, skipped, uncategorized)
    }

    private fun parseDate(s: String): LocalDate? {
        val text = s.take(10).trim()
        for (f in DATE_FORMATS) runCatching { return LocalDate.parse(text, f) }
        return null
    }

    /** Undoes the export's guard against spreadsheet formulas ("'=x" back to "=x"). */
    private fun unescape(s: String) = if (s.length > 1 && s[0] == '\'' && s[1] in "=+-@") s.substring(1) else s

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** RFC 4180 with some slack: a leading BOM, CRLF or LF line ends, quoted cells with "" escapes and line breaks. */
    fun parse(text: String): List<List<String>> {
        val src = text.removePrefix("﻿")
        val out = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < src.length) {
            val c = src[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < src.length && src[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row += cell.toString(); cell.clear() }
                '\r' -> Unit
                '\n' -> { row += cell.toString(); cell.clear(); out += row; row = mutableListOf() }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); out += row }
        return out
    }
}
