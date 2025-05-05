package com.example.caesartv.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MediaEntity> mediaEntities);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUrls(List<MediaUrlEntity> urlEntities);

    @Transaction
    @Query("SELECT * FROM media WHERE isActive = 1 ORDER BY displayOrder")
    List<MediaWithUrls> getAllMedia();

    @Query("DELETE FROM media")
    void deleteAll();

    @Query("DELETE FROM media_url")
    void deleteAllUrls();

    @Query("SELECT COUNT(*) FROM media WHERE isActive = 1")
    int countActiveMedia();
}