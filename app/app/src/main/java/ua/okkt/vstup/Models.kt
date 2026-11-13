package ua.okkt.vstup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ua.okkt.vstup.data.OfferEntity
import ua.okkt.vstup.data.ProgramEntity
import ua.okkt.vstup.data.SpecialtyEntity

@Serializable
data class DataFile(
    val version: Int,
    @SerialName("updated_at") val updatedAt: String,
    val specialties: List<SpecialtyJson>,
    val events: List<EventJson>,
    val links: List<LinkJson> = emptyList(),
    val college: CollegeJson = CollegeJson(),
)

@Serializable
data class CollegeJson(
    val intro: String = "",
    val facts: List<FactJson> = emptyList(),
    val advantages: List<AdvantageJson> = emptyList(),
)

@Serializable
data class FactJson(val value: String, val label: String)

@Serializable
data class AdvantageJson(val title: String, val text: String)

@Serializable
data class LinkJson(
    val id: String,
    val group: String,
    val title: String,
    val url: String,
    val note: String = "",
)

@Serializable
data class SpecialtyJson(
    val id: String,
    val code: String,
    val name: String,
    val description: String = "",
    @SerialName("site_url") val siteUrl: String = "",
    val programs: List<ProgramJson>,
)

@Serializable
data class ProgramJson(
    val id: String,
    val name: String,
    val qualification: String,
    val credits: Int? = null,
    val features: String = "",
    val practice: String = "",
    @SerialName("further_study") val furtherStudy: String = "",
    @SerialName("doc_url") val docUrl: String = "",
    val offers: List<OfferJson>,
    val components: List<String> = emptyList(),
    val positions: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
)

@Serializable
data class OfferJson(
    val base: Int,
    val form: String,
    @SerialName("duration_months") val durationMonths: Int? = null,
    @SerialName("price_per_year") val pricePerYear: Int? = null,
    @SerialName("budget_places_last_year") val budgetPlacesLastYear: Int? = null,
    @SerialName("applications_last_year") val applicationsLastYear: Int? = null,
)

@Serializable
data class EventJson(
    val id: String,
    val title: String,
    val kind: String,
    @SerialName("starts_on") val startsOn: String,
    @SerialName("ends_on") val endsOn: String? = null,
    val base: Int? = null,
    val note: String = "",
)

data class ProgramCard(
    val specialty: SpecialtyEntity,
    val program: ProgramEntity,
    val offers: List<OfferEntity>,
    val components: List<String>,
    val positions: List<String>,
    val skills: List<String>,
    val tools: List<String>,
)

const val CUSTOM_KIND = "custom"
const val CUSTOM_ID_PREFIX = "custom-"

val eventKindTitles = mapOf(
    "registration" to "Реєстрація",
    "documents" to "Подання документів",
    "exam" to "Вступні випробування",
    "rating" to "Рейтинговий список",
    "enrollment" to "Зарахування",
    "open_day" to "День відкритих дверей",
    "other" to "Інше",
    CUSTOM_KIND to "Моя подія",
)

fun durationText(months: Int?): String {
    if (months == null) return "уточнюється"
    val years = months / 12
    val rest = months % 12
    return when {
        years == 0 -> "$rest міс."
        rest == 0 -> "$years р."
        else -> "$years р. $rest міс."
    }
}

fun baseText(base: Int) = "після $base класу"

fun priceText(uah: Int?): String =
    if (uah == null) "немає даних" else "%,d грн/рік".format(uah).replace(',', ' ')
