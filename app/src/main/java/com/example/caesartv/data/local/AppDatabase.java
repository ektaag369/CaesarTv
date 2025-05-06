package com.example.caesartv.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {MediaEntity.class, MediaUrlEntity.class}, version = 6, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract MediaDao mediaDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "caesartv_database")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE media ADD COLUMN localFilePath TEXT");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE media_url (" +
                    "dbId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "mediaId TEXT NOT NULL, " +
                    "urlType TEXT, " +
                    "url TEXT, " +
                    "id TEXT, " +
                    "FOREIGN KEY(mediaId) REFERENCES media(id) ON DELETE CASCADE)");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE media_temp (" +
                    "id TEXT NOT NULL, " +
                    "title TEXT, " +
                    "description TEXT, " +
                    "mediaType TEXT, " +
                    "url TEXT, " +
                    "localFilePath TEXT, " +
                    "thumbnailUrl TEXT, " +
                    "duration INTEGER NOT NULL, " +
                    "displayOrder INTEGER NOT NULL, " +
                    "isActive INTEGER NOT NULL, " +
                    "createdAt TEXT, " +
                    "updatedAt TEXT, " +
                    "PRIMARY KEY(id))");
            database.execSQL("INSERT INTO media_temp SELECT id, title, description, mediaType, url, localFilePath, thumbnailUrl, duration, displayOrder, isActive, createdAt, updatedAt FROM media");
            database.execSQL("DROP TABLE media");
            database.execSQL("ALTER TABLE media_temp RENAME TO media");
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS media_url");
            database.execSQL("CREATE TABLE media_url (" +
                    "dbId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "mediaId TEXT NOT NULL, " +
                    "urlType TEXT, " +
                    "url TEXT, " +
                    "id TEXT, " +
                    "FOREIGN KEY(mediaId) REFERENCES media(id) ON DELETE CASCADE)");
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE media_url ADD COLUMN localFilePath TEXT");
        }
    };
}