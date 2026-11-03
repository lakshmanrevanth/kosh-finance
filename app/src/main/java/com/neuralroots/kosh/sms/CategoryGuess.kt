package com.neuralroots.kosh.sms

import com.neuralroots.kosh.data.db.CategoryKind

/**
 * Keyword-based category guess for a merchant / SMS, used when there is no learned
 * category for that merchant yet. Returns a default category name (see DefaultData).
 */
object CategoryGuess {
    private val expenseRules: List<Pair<String, List<String>>> = listOf(
        "Food & Dining" to listOf("swiggy", "zomato", "dominos", "domino", "pizza", "mcdonald", "kfc", "burger", "cafe", "coffee", "starbucks", "restaurant", "hotel", "dhaba", "food", "chai", "bakery", "biryani", "barbeque", "haldiram"),
        "Groceries" to listOf("bigbasket", "blinkit", "zepto", "instamart", "dmart", "d mart", "grofers", "jiomart", "reliance fresh", "more retail", "spencer", "kirana", "grocery", "supermarket", "milk", "dairy", "vegetable", "fruits", "nature basket", "ratnadeep"),
        "Transport" to listOf("uber", "ola", "rapido", "metro", "irctc", "redbus", "railway", "cab", "taxi", "fastag", "parking", "toll", "namma yatri"),
        "Fuel" to listOf("petrol", "fuel", "hpcl", "bpcl", "iocl", "indian oil", "bharat petroleum", "hindustan petroleum", "shell", "nayara", "filling station"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "snapdeal", "decathlon", "lifestyle", "pantaloons", "westside", "zara", "h&m", "croma", "reliance digital", "ikea", "mall"),
        "Bills & Utilities" to listOf("electricity", "bescom", "tneb", "msedcl", "tata power", "adani", "water", "gas", "indane", "bharat gas", "hp gas", "bill", "billdesk", "bbps", "utility", "municipal", "property tax"),
        "Mobile & Internet" to listOf("airtel", "jio", "vodafone", "vi ", "bsnl", "act fibernet", "hathway", "broadband", "recharge", "prepaid", "postpaid", "dth", "tata play", "tatasky"),
        "Rent" to listOf("rent", "nobroker", "landlord", "house owner", "pg ", "hostel"),
        "EMI" to listOf("emi", "nach", "ecs", "loan", "bajaj finance", "home credit", "mandate"),
        "Health" to listOf("pharmacy", "medical", "medplus", "apollo", "1mg", "netmeds", "pharmeasy", "hospital", "clinic", "diagnostic", "doctor", "practo", "chemist"),
        "Entertainment" to listOf("bookmyshow", "pvr", "inox", "cinema", "movie", "steam", "playstation"),
        "Subscriptions" to listOf("netflix", "hotstar", "prime video", "spotify", "youtube", "apple.com", "google play", "zee5", "sonyliv", "jiocinema", "subscription", "openai", "chatgpt", "microsoft"),
        "Education" to listOf("school", "college", "university", "tuition", "course", "udemy", "coursera", "byju", "unacademy", "exam", "fees"),
        "Travel" to listOf("makemytrip", "goibibo", "cleartrip", "yatra", "indigo", "air india", "vistara", "akasa", "spicejet", "oyo", "airbnb", "booking.com", "agoda", "flight", "airline"),
        "Personal Care" to listOf("salon", "spa", "parlour", "parlor", "urban company", "urbanclap", "barber", "grooming"),
        "Insurance" to listOf("insurance", "lic ", "premium", "policybazaar", "hdfc life", "icici pru", "star health", "acko"),
        "Investments" to listOf("zerodha", "groww", "upstox", "angel one", "kuvera", "mutual fund", "sip", "nps", "ppf", "smallcase", "indmoney", "paytm money"),
        "Gifts & Donations" to listOf("donation", "temple", "charity", "gift", "ketto", "giveindia"),
    )

    private val incomeRules: List<Pair<String, List<String>>> = listOf(
        "Salary" to listOf("salary", "sal ", "payroll", "wages", "stipend"),
        "Interest & Dividends" to listOf("interest", "int.pd", "int pd", "dividend", "div "),
        "Refund / Cashback" to listOf("refund", "reversal", "reversed", "cashback", "cash back"),
        "Freelance" to listOf("upwork", "fiverr", "freelance", "invoice"),
    )

    /** Best-guess default category name for this merchant/SMS, or null. */
    fun guess(merchant: String, body: String, kind: CategoryKind): String? {
        val hay = " ${merchant.lowercase()} ${body.lowercase()} "
        val rules = if (kind == CategoryKind.EXPENSE) expenseRules else incomeRules
        // Merchant name first (more specific), then the whole SMS text.
        val m = " ${merchant.lowercase()} "
        rules.firstOrNull { (_, kws) -> kws.any { m.contains(it) } }?.let { return it.first }
        return rules.firstOrNull { (_, kws) -> kws.any { hay.contains(it) } }?.first
    }

    fun fallback(kind: CategoryKind): String = if (kind == CategoryKind.EXPENSE) "Other" else "Other Income"
}
