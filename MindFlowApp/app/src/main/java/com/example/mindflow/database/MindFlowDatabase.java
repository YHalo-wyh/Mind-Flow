package com.example.mindflow.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.mindflow.database.dao.*;
import com.example.mindflow.model.*;

/**
 * MindFlow Room数据库
 * 包含所有表的访问接口
 */
@Database(entities = {
        FocusSession.class,
        AppEvent.class,
        SensorData.class,
        FeatureWindow.class,
        LabelWindow.class,
        Intervention.class,
        FocusTask.class,
        FocusRecord.class
}, version = 2, exportSchema = false)
public abstract class MindFlowDatabase extends RoomDatabase {

    // 1. 唯一的单例变量
    private static volatile MindFlowDatabase INSTANCE;

    // 2. DAO访问接口
    public abstract FocusSessionDao focusSessionDao();

    public abstract AppEventDao appEventDao();

    public abstract SensorDataDao sensorDataDao();

    public abstract FeatureWindowDao featureWindowDao();

    public abstract LabelWindowDao labelWindowDao();

    public abstract InterventionDao interventionDao();

    // 新增DAO - 番茄todo风格
    public abstract FocusTaskDao focusTaskDao();
    public abstract FocusRecordDao focusRecordDao();

    /**
     * 获取数据库单例 (统一入口)
     */
    public static MindFlowDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (MindFlowDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            MindFlowDatabase.class,
                            "mindflow_database")
                            .fallbackToDestructiveMigration() // 允许升级时清空旧数据
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    // 为了兼容你可能写过的旧代码，保留 getInstance，直接调用上面的 getDatabase
    public static MindFlowDatabase getInstance(Context context) {
        return getDatabase(context);
    }
}