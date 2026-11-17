package ua.okkt.vstup

import android.app.Application
import androidx.room.Room
import ua.okkt.vstup.data.AppDatabase
import ua.okkt.vstup.data.MIGRATION_2_3
import ua.okkt.vstup.data.Repository
import ua.okkt.vstup.services.createReminderChannel
import ua.okkt.vstup.services.scheduleDailyCheck

class GuideApp : Application() {
    lateinit var repository: Repository

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "guide.db").addMigrations(MIGRATION_2_3).build()
        createReminderChannel(this)
        repository = Repository(db) { assets.open("data.json").bufferedReader().use { it.readText() } }
        scheduleDailyCheck(this)
    }
}
