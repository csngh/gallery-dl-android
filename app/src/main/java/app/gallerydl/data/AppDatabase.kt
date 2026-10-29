package app.gallerydl.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.gallerydl.download.linkFolderName

@Database(
    entities = [DownloadTask::class, Folder::class, LibraryItem::class],
    version = 9,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gallerydl.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
    }
}

/** Tags are gone, and every library item now belongs to the folder of the link it came from. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS item_tags")
        db.execSQL("DROP TABLE IF EXISTS tags")

        val urls = buildList {
            db.query("SELECT DISTINCT sourceUrl FROM library_items WHERE folderId IS NULL").use {
                while (it.moveToNext()) add(it.getString(0))
            }
        }
        for (url in urls) {
            val name = linkFolderName(url)
            db.execSQL("INSERT OR IGNORE INTO folders (name) VALUES (?)", arrayOf(name))
            db.execSQL(
                "UPDATE library_items SET folderId = (SELECT id FROM folders WHERE name = ?) " +
                    "WHERE folderId IS NULL AND sourceUrl = ?",
                arrayOf(name, url),
            )
        }
    }
}

/** Per-link count of files that could not be downloaded. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN failedFiles INTEGER NOT NULL DEFAULT 0")
    }
}

/** Live download progress shown in the queue. */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN fileIndex INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN totalFiles INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN fileProgress INTEGER NOT NULL DEFAULT -1")
    }
}

/** The website each link belongs to, so links can be grouped by site. */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN site TEXT")
    }
}

/** A tag the user can give a folder. */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folders ADD COLUMN tag TEXT")
    }
}

/** "Clear finished" on the Queue hides links there instead of deleting them. */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN hiddenFromQueue INTEGER NOT NULL DEFAULT 0")
    }
}

/** Each file keeps the direct address it was downloaded from. */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE library_items ADD COLUMN fileUrl TEXT")
    }
}

/** A link can carry the folder name the user chose for it. */
private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_tasks ADD COLUMN folderName TEXT")
    }
}
