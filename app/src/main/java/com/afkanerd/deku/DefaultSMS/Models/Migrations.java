package com.afkanerd.deku.DefaultSMS.Models;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class Migrations {
    // Define the migration class
    public static class Migration4To5 extends Migration {
        public Migration4To5() {
            super(4, 5);
        }

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Step 1: Create the new table
//            database.execSQL("CREATE TABLE IF NOT EXISTS new_table (id INTEGER PRIMARY KEY, name TEXT)");
        }
    }

    public static class Migration5To6 extends Migration {
        public Migration5To6() {
            super(5, 6);
        }

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Step 1: Create the new table
            database.execSQL("ALTER TABLE GatewayClient ADD COLUMN projectBinding2 TEXT");
        }
    }

    public static class Migration6To7 extends Migration {
        public Migration6To7() {
            super(6, 7);
        }

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS GatewayServer");

            database.execSQL("CREATE TABLE IF NOT EXISTS GatewayServer (" +
                    "id INTEGER NOT NULL PRIMARY KEY, " +
                    "format TEXT, date INTEGER, " +
                    "protocol TEXT, URL TEXT)");

        }
    }

    public static class Migration7To8 extends Migration {
        public Migration7To8() {
            super(7, 8);
        }

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE GatewayServer ADD COLUMN tag TEXT");
        }
    }

    public static class Migration8To9 extends Migration {
        public Migration8To9() {
            super(8, 9);
        }

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS ThreadedConversations " +
                    "(thread_id TEXT PRIMARY KEY NOT NULL, " +
                    "msg_count INTEGER NOT NULL, " +
                    "avatar_color INTEGER NOT NULL, " +
                    "type INTEGER NOT NULL, " +
                    "date TEXT, " +
                    "is_archived INTEGER NOT NULL, " +
                    "is_blocked INTEGER NOT NULL, " +
                    "is_read INTEGER NOT NULL, " +
                    "snippet TEXT, " +
                    "contact_name TEXT, " +
                    "avatar_initials TEXT, " +
                    "avatar_image TEXT, " +
                    "formatted_datetime TEXT)");

            database.execSQL("CREATE TABLE IF NOT EXISTS Conversation " +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "message_id TEXT, " +
                    "thread_id TEXT, " +
                    "date TEXT, " +
                    "date_sent TEXT, " +
                    "type INTEGER NOT NULL, " +
                    "num_segments INTEGER NOT NULL, " +
                    "subscription_id INTEGER NOT NULL, " +
                    "status INTEGER NOT NULL, " +
                    "read INTEGER NOT NULL, " +
                    "is_encrypted INTEGER NOT NULL, " +
                    "is_key INTEGER NOT NULL, " +
                    "is_image INTEGER NOT NULL, " +
                    "formatted_date TEXT, " +
                    "address TEXT, " +
                    "body TEXT)");

            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Conversation_message_id ON Conversation (message_id)");
        }
    }

}
