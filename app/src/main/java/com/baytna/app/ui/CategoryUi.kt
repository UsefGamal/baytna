package com.baytna.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.baytna.app.R
import com.baytna.app.data.db.CategoryEntity

object CategoryIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "home" to Icons.Filled.Home,
        "cart" to Icons.Filled.ShoppingCart,
        "bolt" to Icons.Filled.Bolt,
        "car" to Icons.Filled.DirectionsCar,
        "food" to Icons.Filled.Restaurant,
        "health" to Icons.Filled.LocalHospital,
        "bag" to Icons.Filled.ShoppingBag,
        "movie" to Icons.Filled.Movie,
        "family" to Icons.Filled.FamilyRestroom,
        "subscriptions" to Icons.Filled.Subscriptions,
        "person" to Icons.Filled.Person,
        "savings" to Icons.Filled.Savings,
        "salary" to Icons.Filled.Payments,
        "money" to Icons.Filled.AttachMoney,
        "other" to Icons.Filled.Category,
    )
    private val byKey = all.toMap()
    fun of(key: String): ImageVector = byKey[key] ?: Icons.Filled.Category

    val palette: List<Long> = listOf(
        0xFF16A36A, 0xFF5B8DEF, 0xFFE6A23C, 0xFF4FB3BF, 0xFFEF8A5B, 0xFFE36D6D,
        0xFFB57EDC, 0xFFD96BA0, 0xFF8C9EFF, 0xFF26A69A, 0xFFA1887F, 0xFF90A4AE,
    )
}

fun CategoryEntity.colorValue() = Color(color)

private fun systemNameRes(key: String): Int? = when (key) {
    "housing" -> R.string.cat_housing
    "groceries" -> R.string.cat_groceries
    "utilities" -> R.string.cat_utilities
    "transport" -> R.string.cat_transport
    "food_delivery" -> R.string.cat_food_delivery
    "health" -> R.string.cat_health
    "shopping" -> R.string.cat_shopping
    "entertainment" -> R.string.cat_entertainment
    "family" -> R.string.cat_family
    "subscriptions" -> R.string.cat_subscriptions
    "personal" -> R.string.cat_personal
    "savings_planning" -> R.string.cat_savings_planning
    "other" -> R.string.cat_other
    "salary" -> R.string.cat_salary
    "other_income" -> R.string.cat_other_income
    else -> null
}

@Composable
fun CategoryEntity.displayName(): String = systemKey?.let { systemNameRes(it) }?.let { stringResource(it) } ?: name

fun CategoryEntity.displayName(context: android.content.Context): String =
    systemKey?.let { systemNameRes(it) }?.let { context.getString(it) } ?: name
