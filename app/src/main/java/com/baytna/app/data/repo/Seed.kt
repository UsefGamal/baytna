package com.baytna.app.data.repo

import com.baytna.domain.model.CategoryType

/** Default categories from the product spec. Names are localized in the UI by [key]. */
data class SeedCategory(val key: String, val icon: String, val color: Long, val type: CategoryType)

object Seed {
    val categories = listOf(
        SeedCategory("housing", "home", 0xFF5B8DEF, CategoryType.EXPENSE),
        SeedCategory("groceries", "cart", 0xFF16A36A, CategoryType.EXPENSE),
        SeedCategory("utilities", "bolt", 0xFFE6A23C, CategoryType.EXPENSE),
        SeedCategory("transport", "car", 0xFF4FB3BF, CategoryType.EXPENSE),
        SeedCategory("food_delivery", "food", 0xFFEF8A5B, CategoryType.EXPENSE),
        SeedCategory("health", "health", 0xFFE36D6D, CategoryType.EXPENSE),
        SeedCategory("shopping", "bag", 0xFFB57EDC, CategoryType.EXPENSE),
        SeedCategory("entertainment", "movie", 0xFFD96BA0, CategoryType.EXPENSE),
        SeedCategory("family", "family", 0xFF8C9EFF, CategoryType.EXPENSE),
        SeedCategory("subscriptions", "subscriptions", 0xFF7986CB, CategoryType.EXPENSE),
        SeedCategory("personal", "person", 0xFFA1887F, CategoryType.EXPENSE),
        SeedCategory("savings_planning", "savings", 0xFF26A69A, CategoryType.EXPENSE),
        SeedCategory("other", "other", 0xFF90A4AE, CategoryType.EXPENSE),
        SeedCategory("salary", "salary", 0xFF16A36A, CategoryType.INCOME),
        SeedCategory("other_income", "money", 0xFF43A047, CategoryType.INCOME),
    )
}
