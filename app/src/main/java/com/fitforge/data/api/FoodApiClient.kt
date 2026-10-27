package com.fitforge.data.api

import com.fitforge.data.Food
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class FoodSearchResult(
    val usdaFoods: List<Food> = emptyList(),
    val openFoodFactsFoods: List<Food> = emptyList(),
    val usdaError: String? = null,
    val openFoodFactsError: String? = null
) {
    val allOnlineFoods: List<Food> get() = usdaFoods + openFoodFactsFoods
}

object FoodApiClient {
    private const val DEFAULT_USDA_KEY = "j7rbFLBtmchnQnmjGCk8zHfwM6nrLJhPTqFN6qCC"
    private const val USER_AGENT = "FitForge - Android - Version 1.1 - contact@fitforge.app"
    private const val TIMEOUT_MS = 9000

    /**
     * Searches both USDA FoodData Central and OpenFoodFacts concurrently.
     */
    suspend fun searchBoth(query: String, usdaApiKey: String = DEFAULT_USDA_KEY): FoodSearchResult =
        coroutineScope {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@coroutineScope FoodSearchResult()

            val isBarcode = trimmed.matches(Regex("^[0-9]{8,14}$"))

            val usdaDeferred = async(Dispatchers.IO) {
                if (isBarcode) {
                    // For barcodes, search by UPC in USDA
                    searchUsda(trimmed, usdaApiKey)
                } else {
                    searchUsda(trimmed, usdaApiKey)
                }
            }

            val offDeferred = async(Dispatchers.IO) {
                if (isBarcode) {
                    lookupOpenFoodFactsBarcode(trimmed)
                } else {
                    searchOpenFoodFacts(trimmed)
                }
            }

            val usdaResult = usdaDeferred.await()
            val offResult = offDeferred.await()

            FoodSearchResult(
                usdaFoods = usdaResult.getOrDefault(emptyList()),
                openFoodFactsFoods = offResult.getOrDefault(emptyList()),
                usdaError = usdaResult.exceptionOrNull()?.localizedMessage,
                openFoodFactsError = offResult.exceptionOrNull()?.localizedMessage
            )
        }

    /**
     * Search USDA FoodData Central
     * https://api.nal.usda.gov/fdc/v1/foods/search
     */
    suspend fun searchUsda(
        query: String,
        apiKey: String = DEFAULT_USDA_KEY,
        pageSize: Int = 25
    ): Result<List<Food>> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val effectiveKey = apiKey.ifBlank { DEFAULT_USDA_KEY }
            val urlString = "https://api.nal.usda.gov/fdc/v1/foods/search?query=$encodedQuery&pageSize=$pageSize&api_key=$effectiveKey"

            val jsonString = httpGet(urlString, mapOf("Accept" to "application/json"))
            parseUsdaResponse(jsonString)
        }
    }

    /**
     * Search OpenFoodFacts using Search-a-licious
     * https://search.openfoodfacts.net/search
     */
    suspend fun searchOpenFoodFacts(
        query: String,
        pageSize: Int = 25
    ): Result<List<Food>> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val urlString = "https://search.openfoodfacts.net/search?q=$encodedQuery&page_size=$pageSize&fields=code,product_name,brands,nutriments,categories"

            val jsonString = httpGet(
                urlString,
                mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to "application/json"
                )
            )
            parseOpenFoodFactsHits(jsonString)
        }
    }

    /**
     * Barcode lookup on OpenFoodFacts using official API v3 (latest recommended version)
     * https://world.openfoodfacts.org/api/v3/product/{barcode}.json
     */
    suspend fun lookupOpenFoodFactsBarcode(barcode: String): Result<List<Food>> = withContext(Dispatchers.IO) {
        runCatching {
            val urlString = "https://world.openfoodfacts.org/api/v3/product/$barcode.json?fields=code,product_name,brands,nutriments,categories"
            val jsonString = httpGet(
                urlString,
                mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to "application/json"
                )
            )
            val root = JSONObject(jsonString)
            val status = root.optString("status", "")
            if (status == "failure" || !root.has("product")) {
                return@runCatching emptyList<Food>()
            }
            val productObj = root.optJSONObject("product") ?: return@runCatching emptyList<Food>()
            val food = parseOffProductObject(productObj, root.optString("code", barcode))
            if (food != null) listOf(food) else emptyList()
        }
    }


    private fun parseUsdaResponse(jsonString: String): List<Food> {
        val root = JSONObject(jsonString)
        val foodsArray = root.optJSONArray("foods") ?: return emptyList()
        val result = mutableListOf<Food>()

        for (i in 0 until foodsArray.length()) {
            val item = foodsArray.optJSONObject(i) ?: continue
            val fdcId = item.optLong("fdcId", 0L)
            if (fdcId == 0L) continue

            val rawDesc = item.optString("description", "Unknown Food").trim()
            val brand = item.optString("brandName", item.optString("brandOwner", "")).trim()
            val category = item.optString("foodCategory", "General Food").trim()
            val dataType = item.optString("dataType", "USDA")

            val displayName = formatFoodTitle(rawDesc)
            val finalName = if (brand.isNotBlank() && !displayName.contains(brand, ignoreCase = true)) {
                "$displayName (${formatFoodTitle(brand)})"
            } else {
                displayName
            }

            var kcalFound: Double? = null
            var proteinFound: Double? = null
            var carbsFound: Double? = null
            var fatFound: Double? = null
            var fiberFound: Double? = null

            val nutrientsArray = item.optJSONArray("foodNutrients") ?: JSONArray()
            for (j in 0 until nutrientsArray.length()) {
                val n = nutrientsArray.optJSONObject(j) ?: continue
                val nName = n.optString("nutrientName", "").lowercase(Locale.ROOT)
                val nNum = n.optString("nutrientNumber", "")
                val unit = n.optString("unitName", "").uppercase(Locale.ROOT)
                val value = if (n.has("value") && !n.isNull("value")) n.optDouble("value") else null

                if (value != null && !value.isNaN()) {
                    when {
                        nNum == "208" || (nName.contains("energy") && unit == "KCAL") -> {
                            if (kcalFound == null) kcalFound = value
                        }
                        kcalFound == null && unit == "KJ" && (nName.contains("energy") || nNum == "268") -> {
                            kcalFound = value / 4.184
                        }
                        nNum == "203" || nName == "protein" -> {
                            proteinFound = value
                        }
                        nNum == "205" || nName.contains("carbohydrate") -> {
                            carbsFound = value
                        }
                        nNum == "204" || nName.contains("total lipid") -> {
                            fatFound = value
                        }
                        nNum == "291" || nName.contains("fiber") -> {
                            fiberFound = value
                        }
                    }
                }
            }

            // Distinguish missing vs zero:
            // If energy was omitted completely, estimate using Atwater factors only if macros exist.
            // If energy was explicitly reported as 0.0 (e.g. water, black coffee, diet drinks), preserve 0.0.
            val finalKcal = when {
                kcalFound != null -> kcalFound
                proteinFound != null || carbsFound != null || fatFound != null -> {
                    ((proteinFound ?: 0.0) * 4.0) + ((carbsFound ?: 0.0) * 4.0) + ((fatFound ?: 0.0) * 9.0)
                }
                else -> 0.0
            }

            val food = Food(
                id = "usda_$fdcId",
                name = finalName,
                category = if (category.isNotBlank()) category else "Food",
                kcalPer100g = round1(finalKcal),
                proteinPer100g = round1(proteinFound ?: 0.0),
                carbsPer100g = round1(carbsFound ?: 0.0),
                fatPer100g = round1(fatFound ?: 0.0),
                fiberPer100g = round1(fiberFound ?: 0.0),
                source = "USDA FoodData Central ($dataType)",
                state = "per 100g"
            )
            result.add(food)
        }
        return result
    }

    private fun parseOpenFoodFactsHits(jsonString: String): List<Food> {
        val root = JSONObject(jsonString)
        val hitsArray = root.optJSONArray("hits") ?: return emptyList()
        val result = mutableListOf<Food>()

        for (i in 0 until hitsArray.length()) {
            val hit = hitsArray.optJSONObject(i) ?: continue
            val food = parseOffProductObject(hit, hit.optString("code", ""))
            if (food != null) {
                result.add(food)
            }
        }
        return result
    }

    private fun parseOffProductObject(obj: JSONObject, defaultCode: String): Food? {
        val code = obj.optString("code", defaultCode).trim()
        val rawName = obj.optString("product_name", "").trim()
        if (rawName.isBlank() && code.isBlank()) return null

        // Extract brands
        val brandStr = when {
            obj.has("brands") && obj.get("brands") is JSONArray -> {
                val arr = obj.getJSONArray("brands")
                val list = mutableListOf<String>()
                for (k in 0 until arr.length()) {
                    val b = arr.optString(k).replace("xx:", "").replace("en:", "").trim()
                    if (b.isNotBlank()) list.add(b)
                }
                list.joinToString(", ")
            }
            obj.has("brands") -> {
                obj.optString("brands", "").replace("xx:", "").replace("en:", "").trim()
            }
            else -> ""
        }

        val baseName = if (rawName.isNotBlank()) rawName else "Product #$code"
        val displayName = if (brandStr.isNotBlank() && !baseName.contains(brandStr, ignoreCase = true)) {
            "$baseName ($brandStr)"
        } else {
            baseName
        }

        val categoryRaw = obj.optString("categories", "Packaged Food")
        val category = categoryRaw.split(",").firstOrNull()?.trim()?.ifBlank { "Packaged Food" } ?: "Packaged Food"

        val nutriments = obj.optJSONObject("nutriments") ?: JSONObject()

        val kcalReported = getNutrientValueOrNull(nutriments, "energy-kcal_100g", "energy-kcal")
        val kjReported = getNutrientValueOrNull(nutriments, "energy-kj_100g", "energy_100g", "energy")
        val proteinReported = getNutrientValueOrNull(nutriments, "proteins_100g", "proteins")
        val carbsReported = getNutrientValueOrNull(nutriments, "carbohydrates_100g", "carbohydrates")
        val fatReported = getNutrientValueOrNull(nutriments, "fat_100g", "fat")
        val fiberReported = getNutrientValueOrNull(nutriments, "fiber_100g", "fiber")

        // Distinguish missing vs zero:
        val kcal = when {
            kcalReported != null -> kcalReported
            kjReported != null && kjReported > 0.0 -> kjReported / 4.184
            proteinReported != null || carbsReported != null || fatReported != null -> {
                ((proteinReported ?: 0.0) * 4.0) + ((carbsReported ?: 0.0) * 4.0) + ((fatReported ?: 0.0) * 9.0)
            }
            else -> 0.0
        }

        return Food(
            id = "off_${if (code.isNotBlank()) code else baseName.hashCode().toString()}",
            name = displayName,
            category = category,
            kcalPer100g = round1(kcal),
            proteinPer100g = round1(proteinReported ?: 0.0),
            carbsPer100g = round1(carbsReported ?: 0.0),
            fatPer100g = round1(fatReported ?: 0.0),
            fiberPer100g = round1(fiberReported ?: 0.0),
            source = "OpenFoodFacts",
            state = "per 100g"
        )
    }

    private fun getNutrientValueOrNull(obj: JSONObject, vararg keys: String): Double? {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val d = obj.optDouble(k, Double.NaN)
                if (!d.isNaN()) return d
            }
        }
        return null
    }

    private fun formatFoodTitle(str: String): String {
        if (str.isBlank()) return str
        // If text is ALL CAPS, convert to Title Case
        val isAllUpper = str.any { it.isLetter() } && str.filter { it.isLetter() }.all { it.isUpperCase() }
        if (isAllUpper) {
            return str.lowercase(Locale.ROOT).split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }
        return str
    }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0

    private fun httpGet(urlString: String, headers: Map<String, String> = emptyMap()): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            ?: throw Exception("HTTP $code without error body")

        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val sb = StringBuilder()
        reader.use { r ->
            var line: String?
            while (r.readLine().also { line = it } != null) {
                sb.append(line).append('\n')
            }
        }
        conn.disconnect()

        if (code !in 200..299) {
            val errSnippet = sb.toString().take(200)
            throw Exception("HTTP $code: $errSnippet")
        }
        return sb.toString()
    }
}
