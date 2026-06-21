package top.trumeet.mipush.provider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseOpenHelper;

import java.io.File;

import top.trumeet.mipush.provider.gen.db.DaoMaster;
import top.trumeet.mipush.provider.gen.db.DaoSession;
import top.trumeet.mipush.provider.gen.db.EventDao;
import top.trumeet.mipush.provider.gen.db.RegisteredApplicationDao;

/**
 * Created by Trumeet on 2017/12/23.
 */

public class DatabaseUtils {
    private static final String TAG = "DatabaseUtils";
    public static DaoSession daoSession;
    public static synchronized void init(Context context) {
        if (daoSession != null) {
            return;
        }
        ensureDatabaseDirectory(context);
        MyMigrationHelper.DEBUG = true;
        MySQLiteOpenHelper helper = new MySQLiteOpenHelper(context
                , "db",
                null);
        daoSession = new DaoMaster(helper.getWritableDatabase())
                .newSession();
    }

    public static DaoSession requireDaoSession(Context context) {
        init(context);
        return daoSession;
    }

    private static void ensureDatabaseDirectory(Context context) {
        final File databaseFile = context.getDatabasePath("db");
        if (databaseFile == null) {
            return;
        }
        final File parent = databaseFile.getParentFile();
        if (parent == null || parent.exists()) {
            return;
        }
        if (!parent.mkdirs() && !parent.exists()) {
            Log.w(TAG, "Failed to create database directory: " + parent.getAbsolutePath());
        }
    }


    private static class MySQLiteOpenHelper extends DatabaseOpenHelper {
        public MySQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
            super(context, name, factory, DaoMaster.SCHEMA_VERSION);
        }

        @Override
        public void onCreate(Database db) {
            Log.i("greenDAO", "Creating tables for schema version " + DaoMaster.SCHEMA_VERSION);
            DaoMaster.createAllTables(db, false);
        }

        @Override
        public void onUpgrade(Database db, int oldVersion, int newVersion) {
            MyMigrationHelper.migrate(db, new MyMigrationHelper.ReCreateAllTableListener() {

                @Override
                public void onCreateAllTables(Database db, boolean ifNotExists) {
                    DaoMaster.createAllTables(db, ifNotExists);
                }

                @Override
                public void onDropAllTables(Database db, boolean ifExists) {
                    DaoMaster.dropAllTables(db, ifExists);
                }
            }, EventDao.class, RegisteredApplicationDao.class);
        }
        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }
    }

}
