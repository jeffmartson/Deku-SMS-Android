package com.afkanerd.deku.DefaultSMS.Models.Conversations;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.Room;

import com.afkanerd.deku.DefaultSMS.Commons.Helpers;
import com.afkanerd.deku.DefaultSMS.DAO.ThreadedConversationsDao;
import com.afkanerd.deku.DefaultSMS.Models.Contacts;
import com.afkanerd.deku.DefaultSMS.Models.Database.Datastore;
import com.afkanerd.deku.DefaultSMS.Models.Database.Migrations;
import com.afkanerd.deku.DefaultSMS.R;

import java.util.ArrayList;
import java.util.List;


@Entity
public class ThreadedConversations {
    @NonNull
    @PrimaryKey
     private String thread_id;
    private String address;

    @Ignore
    private int avatar_color;

    @Ignore
    private String avatar_initials;

    @Ignore
    private String avatar_image;
     private int msg_count;

     private int type;

     private String date;

     private boolean is_archived;
     private boolean is_blocked;

     private boolean is_shortcode;

     private boolean is_read;

     private String snippet;

     private String contact_name;

     private String formatted_datetime;

    public static ThreadedConversationsDao getDao(Context context) {
        Datastore databaseConnector = Room.databaseBuilder(context, Datastore.class,
                        Datastore.databaseName)
                .addMigrations(new Migrations.Migration8To9())
                .build();
        ThreadedConversationsDao threadedConversationsDao =  databaseConnector.threadedConversationsDao();
        databaseConnector.close();
        return threadedConversationsDao;
    }

    public static ThreadedConversations build(Context context, Conversation conversation) {
        ThreadedConversations threadedConversations = new ThreadedConversations();
        threadedConversations.setAddress(conversation.getAddress());
        if(conversation.isIs_key()) {
            threadedConversations.setSnippet(context.getString(R.string.conversation_threads_secured_content));
        }
        else threadedConversations.setSnippet(conversation.getText());
        threadedConversations.setThread_id(conversation.getThread_id());
        threadedConversations.setDate(conversation.getDate());
        threadedConversations.setType(conversation.getType());
        threadedConversations.setIs_read(conversation.isRead());

        return threadedConversations;
    }

    public static List<ThreadedConversations> buildRaw(Cursor cursor) {
        List<String> seenThreads = new ArrayList<>();
        List<ThreadedConversations> threadedConversations = new ArrayList<>();
        if(cursor.moveToFirst()) {
            do {
                ThreadedConversations threadedConversation = build(cursor);
                if(!seenThreads.contains(threadedConversation.getThread_id())) {
                    seenThreads.add(threadedConversation.getThread_id());
                    threadedConversations.add(threadedConversation);
                }
            } while(cursor.moveToNext());
        }
        return threadedConversations;
    }

    public static List<ThreadedConversations> buildRaw(Context context, List<Conversation> conversations) {
        List<ThreadedConversations> threadedConversations = new ArrayList<>();
        for(Conversation conversation : conversations) {
            ThreadedConversations threadedConversation = build(context, conversation);
            String contactName = Contacts.retrieveContactName(context,
                    threadedConversation.getAddress());
            threadedConversation.setContact_name(contactName);
            threadedConversations.add(threadedConversation);
        }
        return threadedConversations;
    }

    public boolean isIs_shortcode() {
        return is_shortcode;
    }

    public void setIs_shortcode(boolean is_shortcode) {
        this.is_shortcode = is_shortcode;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public static ThreadedConversations build(Cursor cursor) {
        int snippetIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.BODY);
        int threadIdIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.THREAD_ID);
        int addressIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.ADDRESS);
        int typeIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.TYPE);
        int readIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.READ);
        int dateIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.DATE);

        ThreadedConversations threadedConversations = new ThreadedConversations();
        threadedConversations.setSnippet(cursor.getString(snippetIndex));
        threadedConversations.setThread_id(cursor.getString(threadIdIndex));
        threadedConversations.setAddress(cursor.getString(addressIndex));
        threadedConversations.setType(cursor.getInt(typeIndex));
        threadedConversations.setIs_read(cursor.getInt(readIndex) == 1);
        threadedConversations.setDate(cursor.getString(dateIndex));

        return threadedConversations;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getThread_id() {
        return thread_id;
    }

    public void setThread_id(String thread_id) {
        this.thread_id = thread_id;
    }

    public int getMsg_count() {
        return msg_count;
    }

    public void setMsg_count(int msg_count) {
        this.msg_count = msg_count;
    }

    public int getAvatar_color() {
        return avatar_color;
    }

    protected void setAvatar_color(int avatar_color) {
        this.avatar_color = avatar_color;
    }

    public boolean isIs_archived() {
        return is_archived;
    }

    public void setIs_archived(boolean is_archived) {
        this.is_archived = is_archived;
    }

    public boolean isIs_blocked() {
        return is_blocked;
    }

    public void setIs_blocked(boolean is_blocked) {
        this.is_blocked = is_blocked;
    }

    public boolean isIs_read() {
        return is_read;
    }

    public void setIs_read(boolean is_read) {
        this.is_read = is_read;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getContact_name() {
        return contact_name;
    }

    public void setContact_name(String contact_name) {
        this.contact_name = contact_name;
        if(this.contact_name != null && !this.contact_name.isEmpty()) {
            this.setAvatar_initials(this.contact_name.substring(0, 1));
            this.setAvatar_color(Helpers.generateColor(this.contact_name));
        } else {
            this.setAvatar_initials(null);
            this.setAvatar_color(Helpers.generateColor(this.getAddress()));
        }
    }

    public String getAvatar_initials() {
        return avatar_initials;
    }

    protected void setAvatar_initials(String avatar_initials) {
        this.avatar_initials = avatar_initials;
    }

    public String getAvatar_image() {
        return avatar_image;
    }

    public void setAvatar_image(String avatar_image) {
        this.avatar_image = avatar_image;
    }

    public String getFormatted_datetime() {
        return formatted_datetime;
    }

    public void setFormatted_datetime(String formatted_datetime) {
        this.formatted_datetime = formatted_datetime;
    }

    public static final DiffUtil.ItemCallback<ThreadedConversations> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ThreadedConversations>() {
        @Override
        public boolean areItemsTheSame(@NonNull ThreadedConversations oldItem, @NonNull ThreadedConversations newItem) {
            return oldItem.thread_id.equals(newItem.thread_id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull ThreadedConversations oldItem, @NonNull ThreadedConversations newItem) {
            return oldItem.equals(newItem);
        }
    };

    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj instanceof ThreadedConversations) {
            ThreadedConversations threadedConversations = (ThreadedConversations) obj;

            if(snippet == null) {
                //secure content
                return threadedConversations.thread_id.equals(this.thread_id) &&
                        threadedConversations.is_archived == this.is_archived &&
                        threadedConversations.is_blocked == this.is_blocked &&
                        threadedConversations.is_read == this.is_read &&
                        threadedConversations.type == this.type &&
                        threadedConversations.avatar_color == this.avatar_color &&
                        threadedConversations.msg_count == this.msg_count &&
                        threadedConversations.address.equals(this.address) &&
                        threadedConversations.date.equals(this.date);
            }
            try {
                return threadedConversations.thread_id.equals(this.thread_id) &&
                        threadedConversations.is_archived == this.is_archived &&
                        threadedConversations.is_blocked == this.is_blocked &&
                        threadedConversations.is_read == this.is_read &&
                        threadedConversations.type == this.type &&
                        threadedConversations.avatar_color == this.avatar_color &&
                        threadedConversations.msg_count == this.msg_count &&
                        threadedConversations.address.equals(this.address) &&
                        threadedConversations.date.equals(this.date) &&
                        threadedConversations.snippet.equals(this.snippet);
            } catch(Exception e) {
                e.printStackTrace();
            }

        }
        return false;
    }

    public boolean diffReplace(ThreadedConversations threadedConversations) {
        if(!threadedConversations.equals(this))
            return false;

        boolean diff = false;
        if(this.contact_name == null || !this.contact_name.equals(threadedConversations.getContact_name())) {
            this.contact_name = threadedConversations.getContact_name();
            diff = true;
        }
        if(this.avatar_initials == null || !this.avatar_initials.equals(threadedConversations.getAvatar_initials())) {
            this.avatar_initials = threadedConversations.getAvatar_initials();
            diff = true;
        }
        if(this.avatar_color != threadedConversations.getAvatar_color()) {
            this.avatar_color = threadedConversations.getAvatar_color();
            diff = true;
        }
        if(this.snippet == null || !this.snippet.equals(threadedConversations.getSnippet())) {
            this.snippet = threadedConversations.getSnippet();
            diff = true;
        }
        if(this.date == null || !this.date.equals(threadedConversations.getDate())) {
            this.date = threadedConversations.getDate();
            diff = true;
        }
        if(this.type != threadedConversations.getType()) {
            this.type = threadedConversations.getType();
            diff = true;
        }
//        if(this.is_read != threadedConversations.isIs_read()) {
//            this.is_read = threadedConversations.isIs_read();
//            diff = true;
//        }
        this.msg_count = threadedConversations.getMsg_count();
        return diff;
    }

}
