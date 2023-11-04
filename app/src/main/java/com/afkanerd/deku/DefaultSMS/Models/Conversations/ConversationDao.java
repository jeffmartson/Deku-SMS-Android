package com.afkanerd.deku.DefaultSMS.Models.Conversations;

import androidx.lifecycle.LiveData;
import androidx.paging.DataSource;
import androidx.paging.PagingLiveData;
import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ConversationDao {

    @Query("SELECT * FROM Conversation WHERE thread_id =:thread_id ORDER BY date DESC")
    PagingSource<Integer, Conversation> get(String thread_id);

    @Query("SELECT * FROM Conversation WHERE thread_id =:thread_id ORDER BY date DESC")
    List<Conversation> getAll(String thread_id);

    @Query("SELECT * FROM Conversation ORDER BY date DESC")
    List<Conversation> getComplete();

//    @Query("SELECT * FROM Conversation WHERE body " +
//            "LIKE '%' || :text || '%' ORDER BY date DESC")
//    PagingSource<Integer, Conversation> find(String text);

    @Query("SELECT * FROM Conversation WHERE message_id =:message_id")
    LiveData<Conversation> get(long message_id);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Conversation conversation);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    List<Long> insertAll(List<Conversation> conversationList);

    @Update
    void update(Conversation conversation);

    @Delete
    void delete(Conversation conversation);
}
