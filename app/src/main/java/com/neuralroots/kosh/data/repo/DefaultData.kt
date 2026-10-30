package com.neuralroots.kosh.data.repo

import com.neuralroots.kosh.data.db.Category
import com.neuralroots.kosh.data.db.CategoryKind

object DefaultData {
    private fun c(i: Int, name: String, emoji: String, color: Long, kind: CategoryKind = CategoryKind.EXPENSE) =
        Category(name = name, emoji = emoji, color = color.toInt(), kind = kind, sortOrder = i)

    fun categories(): List<Category> = listOf(
        c(0, "Food & Dining", "🍔", 0xFFF97316),
        c(1, "Groceries", "🛒", 0xFF22C55E),
        c(2, "Transport", "🚕", 0xFF3B82F6),
        c(3, "Fuel", "⛽", 0xFFEAB308),
        c(4, "Shopping", "🛍️", 0xFFEC4899),
        c(5, "Bills & Utilities", "💡", 0xFF06B6D4),
        c(6, "Rent", "🏠", 0xFF8B5CF6),
        c(7, "EMI", "🏦", 0xFF6366F1),
        c(8, "Health", "💊", 0xFFEF4444),
        c(9, "Entertainment", "🎬", 0xFFA855F7),
        c(10, "Subscriptions", "📺", 0xFF0EA5E9),
        c(11, "Education", "📚", 0xFF14B8A6),
        c(12, "Travel", "✈️", 0xFF0284C7),
        c(13, "Personal Care", "💇", 0xFFF472B6),
        c(14, "Gifts & Donations", "🎁", 0xFFF43F5E),
        c(15, "Mobile & Internet", "📱", 0xFF64748B),
        c(16, "Insurance", "🛡️", 0xFF0D9488),
        c(17, "Investments", "📈", 0xFF16A34A),
        c(18, "Other", "🧾", 0xFF78716C),
        c(0, "Salary", "💼", 0xFF12B76A, CategoryKind.INCOME),
        c(1, "Business", "🏢", 0xFF0EA5E9, CategoryKind.INCOME),
        c(2, "Freelance", "💻", 0xFF8B5CF6, CategoryKind.INCOME),
        c(3, "Interest & Dividends", "💰", 0xFFEAB308, CategoryKind.INCOME),
        c(4, "Refund / Cashback", "↩️", 0xFF06B6D4, CategoryKind.INCOME),
        c(5, "Gift Received", "🎉", 0xFFEC4899, CategoryKind.INCOME),
        c(6, "Other Income", "➕", 0xFF78716C, CategoryKind.INCOME),
    )

    val palette: List<Int> = listOf(
        0xFF5B5BD6, 0xFF12B76A, 0xFFF97316, 0xFFEC4899, 0xFF3B82F6, 0xFFEAB308,
        0xFF8B5CF6, 0xFF06B6D4, 0xFFEF4444, 0xFF14B8A6, 0xFFA855F7, 0xFF64748B,
    ).map { it.toInt() }

    val emojiChoices: List<String> = listOf(
        "🍔", "☕", "🍕", "🛒", "🚕", "🚌", "⛽", "🚗", "🛍️", "👕", "💡", "💧", "🔥", "🏠", "🏦",
        "💊", "🏥", "🎬", "🎮", "📺", "🎵", "📚", "🎓", "✈️", "🏨", "💇", "🎁", "📱", "🌐", "🛡️",
        "📈", "💼", "💻", "💰", "🎉", "🐶", "👶", "🏋️", "🍺", "🧾", "🔧", "🧹", "💳", "🏷️", "➕",
    )
}
