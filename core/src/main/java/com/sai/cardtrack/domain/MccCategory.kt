package com.sai.cardtrack.domain

object MccCategory {
    private val categories: Map<String, String> = mapOf(
        "5411" to "groceries",
        "5412" to "groceries",
        "5499" to "groceries",
        "5812" to "dining",
        "5813" to "dining",
        "5814" to "dining",
        "4121" to "taxi",
        "4111" to "transit",
        "4112" to "transit",
        "4131" to "transit",
        "5541" to "fuel",
        "5542" to "fuel",
        "7523" to "parking",
        "5912" to "pharmacy",
        "8011" to "clinic",
        "8021" to "clinic",
        "8031" to "clinic",
        "8041" to "clinic",
        "8042" to "clinic",
        "8043" to "clinic",
        "8050" to "clinic",
        "8062" to "clinic",
        "8099" to "clinic",
        "7997" to "fitness",
        "7941" to "fitness",
        "5816" to "games",
        "5815" to "software",
        "5817" to "software",
        "5818" to "software",
        "4899" to "streaming",
        "4814" to "mobile",
        "4816" to "internet",
        "4900" to "utilities",
        "6513" to "rent",
        "7011" to "hotels",
        "4511" to "tickets",
        "4722" to "tours",
        "5942" to "education",
        "8299" to "education",
        "5732" to "electronics",
        "5651" to "clothes",
        "5661" to "clothes",
        "5311" to "shopping",
        "5331" to "shopping",
        "5399" to "shopping",
        "6010" to "fees",
        "6011" to "fees",
        "6012" to "fees",
        "5999" to "other"
    )

    fun suggest(mccCode: String): String? {
        return categories[mccCode]
    }
}
