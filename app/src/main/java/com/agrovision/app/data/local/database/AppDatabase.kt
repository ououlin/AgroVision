package com.agrovision.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.agrovision.app.data.local.dao.FeedbackDao
import com.agrovision.app.data.local.dao.HistoryDao
import com.agrovision.app.data.local.dao.PlotDao
import com.agrovision.app.data.local.dao.TreatmentDao
import com.agrovision.app.data.local.entity.DetectionFeedback
import com.agrovision.app.data.local.entity.HistoryEntity
import com.agrovision.app.data.local.entity.PlotEntity
import com.agrovision.app.data.local.entity.TreatmentEntity

@Database(
    entities = [
        HistoryEntity::class,
        DetectionFeedback::class,
        PlotEntity::class,
        TreatmentEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun plotDao(): PlotDao
    abstract fun treatmentDao(): TreatmentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加新版字段（兼容旧数据库）
                database.execSQL(
                    "ALTER TABLE detection_history ADD COLUMN disease_name_en TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE detection_history ADD COLUMN disease_name_cn TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE detection_history ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE detection_history ADD COLUMN note TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS detection_feedback (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        history_id INTEGER NOT NULL,
                        is_correct INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY(history_id) REFERENCES detection_history(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_detection_feedback_history_id ON detection_feedback(history_id)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 新增 all_results_json 字段存储 Top-3 完整结果
                database.execSQL(
                    "ALTER TABLE detection_history ADD COLUMN all_results_json TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * V2.0 迁移：4 → 5
         *
         * 1. detection_history 新增 plot_id（可空，老数据默认 NULL）与 sync_status（默认 0）
         * 2. 新建 plots 表（地块）
         * 3. 新建 treatments 表（用药记录），外键 plot_id CASCADE
         *
         * 老用户从 V1 升级后，detection_history.plot_id 全部为 NULL，UI 引导补绑。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. history 表新增 V2.0 字段
                database.execSQL(
                    "ALTER TABLE detection_history ADD COLUMN plot_id INTEGER DEFAULT NULL"
                )
                database.execSQL(
                    "ALTER TABLE detection_history ADD COLUMN sync_status INTEGER NOT NULL DEFAULT 0"
                )

                // 2. plots 地块表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS plots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        remote_id TEXT NOT NULL DEFAULT '',
                        user_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        crop_type TEXT NOT NULL,
                        area REAL,
                        longitude REAL,
                        latitude REAL,
                        phenology_stage TEXT NOT NULL DEFAULT '',
                        note TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        sync_status INTEGER NOT NULL DEFAULT 0,
                        is_deleted INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_plots_user_id ON plots(user_id)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_plots_remote_id ON plots(remote_id)"
                )

                // 3. treatments 用药记录表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS treatments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        remote_id TEXT NOT NULL DEFAULT '',
                        plot_id INTEGER NOT NULL,
                        history_id INTEGER,
                        chemical_name TEXT NOT NULL,
                        dosage TEXT NOT NULL,
                        applied_at INTEGER NOT NULL,
                        safety_interval_days INTEGER NOT NULL DEFAULT 0,
                        expected_harvest_at INTEGER,
                        status TEXT NOT NULL DEFAULT 'applied',
                        note TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL,
                        sync_status INTEGER NOT NULL DEFAULT 0,
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(plot_id) REFERENCES plots(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_treatments_plot_id ON treatments(plot_id)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_treatments_history_id ON treatments(history_id)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_treatments_remote_id ON treatments(remote_id)"
                )
            }
        }

        /** 所有迁移列表（DI 注入 Room.build 时引用） */
        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5
        )
    }
}
