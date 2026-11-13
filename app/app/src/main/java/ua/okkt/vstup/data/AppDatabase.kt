package ua.okkt.vstup.data

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

const val ITEM_COMPONENT = "component"
const val ITEM_POSITION = "position"
const val ITEM_SKILL = "skill"
const val ITEM_TOOL = "tool"
const val INFO_INTRO = "intro"
const val INFO_FACT = "fact"
const val INFO_ADVANTAGE = "advantage"

@Entity(tableName = "specialty")
data class SpecialtyEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val description: String,
    @ColumnInfo(name = "site_url") val siteUrl: String,
)

@Entity(
    tableName = "program",
    foreignKeys = [ForeignKey(SpecialtyEntity::class, ["id"], ["specialty_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("specialty_id")],
)
data class ProgramEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "specialty_id") val specialtyId: String,
    val name: String,
    val qualification: String,
    val credits: Int?,
    val features: String,
    val practice: String,
    @ColumnInfo(name = "further_study") val furtherStudy: String,
    @ColumnInfo(name = "doc_url") val docUrl: String,
)

@Entity(
    tableName = "offer",
    foreignKeys = [ForeignKey(ProgramEntity::class, ["id"], ["program_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("program_id")],
)
data class OfferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "program_id") val programId: String,
    val base: Int,
    val form: String,
    @ColumnInfo(name = "duration_months") val durationMonths: Int?,
    @ColumnInfo(name = "price_per_year") val pricePerYear: Int?,
    @ColumnInfo(name = "budget_places_last_year") val budgetPlacesLastYear: Int?,
    @ColumnInfo(name = "applications_last_year") val applicationsLastYear: Int?,
)

@Entity(
    tableName = "program_item",
    foreignKeys = [ForeignKey(ProgramEntity::class, ["id"], ["program_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("program_id")],
)
data class ProgramItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "program_id") val programId: String,
    val kind: String,
    val name: String,
)

data class SpecialtyOffer(
    val specialtyId: String,
    val base: Int,
    val form: String,
    val durationMonths: Int?,
)

data class FavoriteProgram(
    val programId: String,
    val specialtyId: String,
    val programName: String,
    val code: String,
    val specialtyName: String,
)

@Entity(tableName = "event")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    @ColumnInfo(name = "starts_on") val startsOn: String,
    @ColumnInfo(name = "ends_on") val endsOn: String?,
    val base: Int?,
    val note: String,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean,
)

@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "program_id") val programId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "days_before") val daysBefore: Int,
    val enabled: Boolean,
    @ColumnInfo(name = "notified_on") val notifiedOn: String?,
)

@Entity(tableName = "link")
data class LinkEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_name") val groupName: String,
    val title: String,
    val url: String,
    val note: String,
    val position: Int,
)

@Entity(tableName = "college_info")
data class CollegeInfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val text: String,
    val position: Int,
)

@Entity(tableName = "app_setting")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface GuideDao {
    @Insert
    suspend fun insertSpecialties(specialties: List<SpecialtyEntity>)

    @Insert
    suspend fun insertPrograms(programs: List<ProgramEntity>)

    @Insert
    suspend fun insertOffers(offers: List<OfferEntity>)

    @Insert
    suspend fun insertItems(items: List<ProgramItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    @Query("DELETE FROM specialty")
    suspend fun deleteSpecialties()

    @Query("DELETE FROM event WHERE is_custom = 0")
    suspend fun deleteCampaignEvents()

    @Query(
        """
        SELECT DISTINCT s.* FROM specialty s
        JOIN program p ON p.specialty_id = s.id
        JOIN offer o ON o.program_id = p.id
        WHERE (:base IS NULL OR o.base = :base) AND (:form IS NULL OR o.form = :form)
        ORDER BY s.code
        """
    )
    fun filteredSpecialties(base: Int?, form: String?): Flow<List<SpecialtyEntity>>

    @Query("SELECT DISTINCT form FROM offer ORDER BY form")
    fun forms(): Flow<List<String>>

    @Query(
        """
        SELECT DISTINCT p.specialty_id AS specialtyId, o.base, o.form, o.duration_months AS durationMonths
        FROM offer o JOIN program p ON p.id = o.program_id
        ORDER BY o.base, o.form
        """
    )
    fun specialtyOffers(): Flow<List<SpecialtyOffer>>

    @Query("SELECT * FROM program ORDER BY id")
    fun allPrograms(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM specialty WHERE id = :id")
    suspend fun specialty(id: String): SpecialtyEntity?

    @Query("SELECT * FROM program WHERE specialty_id = :specialtyId ORDER BY id")
    suspend fun programsOf(specialtyId: String): List<ProgramEntity>

    @Query("SELECT * FROM program WHERE id = :id")
    suspend fun program(id: String): ProgramEntity?

    @Query("SELECT * FROM offer WHERE program_id = :programId ORDER BY base, form")
    suspend fun offers(programId: String): List<OfferEntity>

    @Query("SELECT name FROM program_item WHERE program_id = :programId AND kind = :kind ORDER BY id")
    suspend fun itemNames(programId: String, kind: String): List<String>

    @Query(
        """
        SELECT f.program_id AS programId, p.specialty_id AS specialtyId, p.name AS programName, s.code AS code, s.name AS specialtyName
        FROM favorite f JOIN program p ON p.id = f.program_id JOIN specialty s ON s.id = p.specialty_id
        ORDER BY f.added_at
        """
    )
    fun favoritePrograms(): Flow<List<FavoriteProgram>>

    @Query("SELECT program_id FROM favorite")
    fun favoriteIdsFlow(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE program_id = :programId")
    suspend fun removeFavorite(programId: String)

    @Query("SELECT program_id FROM favorite")
    suspend fun favoriteIds(): List<String>

    @Query("SELECT * FROM event ORDER BY starts_on")
    fun events(): Flow<List<EventEntity>>

    @Query("SELECT * FROM event WHERE id = :id")
    fun event(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM event")
    suspend fun allEvents(): List<EventEntity>

    @Query("SELECT * FROM reminder WHERE event_id = :eventId")
    fun reminder(eventId: String): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminder WHERE enabled = 1")
    fun enabledReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder")
    suspend fun allReminders(): List<ReminderEntity>

    @Upsert
    suspend fun saveReminder(reminder: ReminderEntity)

    @Query("UPDATE reminder SET notified_on = :date WHERE event_id = :eventId")
    suspend fun markNotified(eventId: String, date: String)

    @Query("DELETE FROM reminder WHERE event_id = :eventId")
    suspend fun deleteReminder(eventId: String)

    @Upsert
    suspend fun saveEvent(event: EventEntity)

    @Query("DELETE FROM event WHERE id = :id AND is_custom = 1")
    suspend fun deleteCustomEvent(id: String)

    @Query("SELECT * FROM reminder WHERE event_id = :eventId")
    suspend fun reminderOnce(eventId: String): ReminderEntity?

    @Query("SELECT * FROM link ORDER BY position")
    fun links(): Flow<List<LinkEntity>>

    @Insert
    suspend fun insertLinks(links: List<LinkEntity>)

    @Query("DELETE FROM link")
    suspend fun deleteLinks()

    @Query("SELECT * FROM college_info ORDER BY position")
    fun collegeInfo(): Flow<List<CollegeInfoEntity>>

    @Insert
    suspend fun insertCollegeInfo(rows: List<CollegeInfoEntity>)

    @Query("DELETE FROM college_info")
    suspend fun deleteCollegeInfo()

    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    suspend fun setting(key: String): String?

    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    fun settingFlow(key: String): Flow<String?>

    @Upsert
    suspend fun saveSetting(setting: SettingEntity)
}

@Database(
    entities = [
        SpecialtyEntity::class, ProgramEntity::class, OfferEntity::class, ProgramItemEntity::class,
        EventEntity::class, FavoriteEntity::class, ReminderEntity::class, SettingEntity::class, LinkEntity::class, CollegeInfoEntity::class,
    ],
    version = 4,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 3, to = 4)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): GuideDao
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE offer")
        db.execSQL("DROP TABLE component")
        db.execSQL("DROP TABLE position")
        db.execSQL("DROP TABLE specialty")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `specialty` (`id` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `site_url` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `program` (`id` TEXT NOT NULL, `specialty_id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`qualification` TEXT NOT NULL, `credits` INTEGER, `features` TEXT NOT NULL, `practice` TEXT NOT NULL, " +
                "`further_study` TEXT NOT NULL, `doc_url` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`specialty_id`) " +
                "REFERENCES `specialty`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_program_specialty_id` ON `program` (`specialty_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `offer` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `program_id` TEXT NOT NULL, " +
                "`base` INTEGER NOT NULL, `form` TEXT NOT NULL, `duration_months` INTEGER, `price_per_year` INTEGER, " +
                "`budget_places_last_year` INTEGER, `applications_last_year` INTEGER, FOREIGN KEY(`program_id`) " +
                "REFERENCES `program`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_offer_program_id` ON `offer` (`program_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `program_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`program_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, FOREIGN KEY(`program_id`) " +
                "REFERENCES `program`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_program_item_program_id` ON `program_item` (`program_id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `favorite_new` (`program_id` TEXT NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`program_id`))")
        db.execSQL("INSERT INTO favorite_new (program_id, added_at) SELECT specialty_id, added_at FROM favorite")
        db.execSQL("DROP TABLE favorite")
        db.execSQL("ALTER TABLE favorite_new RENAME TO favorite")
        db.execSQL("DELETE FROM app_setting WHERE `key` = 'data_version'")
    }
}
