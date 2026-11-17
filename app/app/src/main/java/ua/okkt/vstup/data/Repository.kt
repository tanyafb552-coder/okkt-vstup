package ua.okkt.vstup.data

import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.okkt.vstup.CUSTOM_ID_PREFIX
import ua.okkt.vstup.CUSTOM_KIND
import ua.okkt.vstup.DataFile
import ua.okkt.vstup.ProgramCard
import ua.okkt.vstup.services.CalendarService
import ua.okkt.vstup.services.DataSyncService
import ua.okkt.vstup.services.SyncResult
import ua.okkt.vstup.services.DueReminder

const val KEY_DATA_VERSION = "data_version"
const val KEY_DATA_UPDATED_AT = "data_updated_at"
const val KEY_BASE = "base"
const val KEY_DEFAULT_DAYS = "default_days"
const val DEFAULT_REMINDER_DAYS = 3

class Repository(private val db: AppDatabase, private val readAsset: () -> String) {
    val dao = db.dao()
    private val syncService = DataSyncService()

    val baseFlow: Flow<Int?> = dao.settingFlow(KEY_BASE).map { it?.toIntOrNull() }

    suspend fun base(): Int? = dao.setting(KEY_BASE)?.toIntOrNull()

    suspend fun saveSetting(key: String, value: String) = dao.saveSetting(SettingEntity(key, value))

    suspend fun programCard(programId: String): ProgramCard? {
        val program = dao.program(programId) ?: return null
        val specialty = dao.specialty(program.specialtyId) ?: return null
        return ProgramCard(
            specialty,
            program,
            dao.offers(programId),
            dao.itemNames(programId, ITEM_COMPONENT),
            dao.itemNames(programId, ITEM_POSITION),
            dao.itemNames(programId, ITEM_SKILL),
            dao.itemNames(programId, ITEM_TOOL),
        )
    }

    suspend fun programCards(ids: Collection<String>): List<ProgramCard> = ids.mapNotNull { programCard(it) }

    suspend fun specialtyPrograms(specialtyId: String): List<ProgramCard> =
        dao.programsOf(specialtyId).mapNotNull { programCard(it.id) }

    suspend fun setFavorite(programId: String, favorite: Boolean) {
        if (favorite) dao.addFavorite(FavoriteEntity(programId, System.currentTimeMillis()))
        else dao.removeFavorite(programId)
    }

    val defaultDaysFlow: Flow<Int> = dao.settingFlow(KEY_DEFAULT_DAYS).map { it?.toIntOrNull() ?: DEFAULT_REMINDER_DAYS }

    suspend fun defaultDays(): Int = dao.setting(KEY_DEFAULT_DAYS)?.toIntOrNull() ?: DEFAULT_REMINDER_DAYS

    suspend fun setReminder(eventId: String, enabled: Boolean, daysBefore: Int) =
        dao.saveReminder(ReminderEntity(eventId, daysBefore, enabled, notifiedOn = null))

    suspend fun dueReminders(today: LocalDate): List<DueReminder> =
        CalendarService().dueReminders(dao.allReminders(), dao.allEvents(), today)

    suspend fun saveCustomEvent(id: String?, title: String, date: LocalDate, note: String) = db.withTransaction {
        val eventId = id ?: (CUSTOM_ID_PREFIX + System.currentTimeMillis())
        dao.saveEvent(EventEntity(eventId, title.trim(), CUSTOM_KIND, date.toString(), null, null, note.trim(), isCustom = true))
        val reminder = dao.reminderOnce(eventId)
        if (reminder != null) dao.saveReminder(reminder.copy(notifiedOn = null))
    }

    suspend fun deleteCustomEvent(id: String) = db.withTransaction {
        dao.deleteCustomEvent(id)
        dao.deleteReminder(id)
    }

    suspend fun seedIfNeeded() {
        val bundled = syncService.parse(readAsset())
        if (syncService.isNewer(bundled, dao.setting(KEY_DATA_VERSION)?.toIntOrNull())) applyNewData(bundled)
    }

    suspend fun syncFromNetwork(): SyncResult = withContext(Dispatchers.IO) {
        val shownSince = dao.setting(KEY_DATA_UPDATED_AT)?.let { CalendarService().dateText(it) } ?: "—"
        val text = try {
            syncService.download()
        } catch (e: Exception) {
            return@withContext SyncResult(false, "Не вдалося оновити: немає з'єднання з джерелом даних. Показано дані від $shownSince.")
        }
        val file = try {
            syncService.parse(text)
        } catch (e: IllegalArgumentException) {
            return@withContext SyncResult(false, "Не вдалося оновити: файл даних пошкоджений (${e.message}). Показано дані від $shownSince.")
        }
        val localVersion = dao.setting(KEY_DATA_VERSION)?.toIntOrNull()
        if (!syncService.isNewer(file, localVersion)) {
            return@withContext SyncResult(false, "Дані актуальні: версія $localVersion від $shownSince.")
        }
        applyNewData(file)
        SyncResult(true, "Дані оновлено до версії ${file.version} від ${CalendarService().dateText(file.updatedAt)}.")
    }

    private suspend fun applyNewData(file: DataFile) = db.withTransaction {
        val staleFavorites = syncService.staleFavorites(file, dao.favoriteIds())
        val staleReminders = syncService.staleReminders(file, dao.allReminders().map { it.eventId })
        replaceData(file)
        staleFavorites.forEach { dao.removeFavorite(it) }
        staleReminders.forEach { dao.deleteReminder(it) }
    }

    suspend fun replaceData(file: DataFile) = db.withTransaction {
        dao.deleteSpecialties()
        dao.deleteCampaignEvents()
        dao.insertSpecialties(file.specialties.map { SpecialtyEntity(it.id, it.code, it.name, it.description, it.siteUrl) })
        val programs = file.specialties.flatMap { s -> s.programs.map { s.id to it } }
        dao.insertPrograms(programs.map { (specialtyId, p) ->
            ProgramEntity(p.id, specialtyId, p.name, p.qualification, p.credits, p.features, p.practice, p.furtherStudy, p.docUrl)
        })
        dao.insertOffers(programs.flatMap { (_, p) ->
            p.offers.map {
                OfferEntity(
                    programId = p.id, base = it.base, form = it.form, durationMonths = it.durationMonths,
                    pricePerYear = it.pricePerYear, budgetPlacesLastYear = it.budgetPlacesLastYear,
                    applicationsLastYear = it.applicationsLastYear,
                )
            }
        })
        dao.insertItems(programs.flatMap { (_, p) ->
            p.components.map { ProgramItemEntity(programId = p.id, kind = ITEM_COMPONENT, name = it) } +
                p.positions.map { ProgramItemEntity(programId = p.id, kind = ITEM_POSITION, name = it) } +
                p.skills.map { ProgramItemEntity(programId = p.id, kind = ITEM_SKILL, name = it) } +
                p.tools.map { ProgramItemEntity(programId = p.id, kind = ITEM_TOOL, name = it) }
        })
        dao.insertEvents(file.events.map {
            EventEntity(it.id, it.title, it.kind, it.startsOn, it.endsOn, it.base, it.note, isCustom = false)
        })
        dao.deleteCollegeInfo()
        val college = file.college
        dao.insertCollegeInfo(
            listOf(CollegeInfoEntity(kind = INFO_INTRO, title = "", text = college.intro, position = 0)) +
                college.facts.mapIndexed { index, fact -> CollegeInfoEntity(kind = INFO_FACT, title = fact.value, text = fact.label, position = index + 1) } +
                college.advantages.mapIndexed { index, advantage ->
                    CollegeInfoEntity(kind = INFO_ADVANTAGE, title = advantage.title, text = advantage.text, position = 100 + index)
                }
        )
        dao.deleteLinks()
        dao.insertLinks(file.links.mapIndexed { index, link ->
            LinkEntity(link.id, link.group, link.title, link.url, link.note, index)
        })
        dao.saveSetting(SettingEntity(KEY_DATA_VERSION, file.version.toString()))
        dao.saveSetting(SettingEntity(KEY_DATA_UPDATED_AT, file.updatedAt))
    }
}
