package com.afkanerd.deku.DefaultSMS.BroadcastReceivers;


import static com.afkanerd.deku.DefaultSMS.BroadcastReceivers.IncomingTextSMSBroadcastReceiver.SMS_UPDATED_BROADCAST_INTENT;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.Telephony;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;

import com.afkanerd.deku.DefaultSMS.DAO.ConversationDao;
import com.afkanerd.deku.DefaultSMS.Models.Conversations.Conversation;
import com.afkanerd.deku.DefaultSMS.Models.NativeSMSDB;
import com.afkanerd.deku.DefaultSMS.BuildConfig;
import com.afkanerd.deku.DefaultSMS.Models.NotificationsHandler;
import com.afkanerd.deku.DefaultSMS.Models.SIMHandler;
import com.afkanerd.deku.DefaultSMS.Models.SMSDatabaseWrapper;
import com.afkanerd.deku.DefaultSMS.R;

public class IncomingTextSMSReplyActionBroadcastReceiver extends BroadcastReceiver {
    public static String REPLY_BROADCAST_INTENT = BuildConfig.APPLICATION_ID + ".REPLY_BROADCAST_ACTION";
    public static String MARK_AS_READ_BROADCAST_INTENT = BuildConfig.APPLICATION_ID + ".MARK_AS_READ_BROADCAST_ACTION";

    public static String REPLY_ADDRESS = "REPLY_ADDRESS";
    public static String REPLY_THREAD_ID = "REPLY_THREAD_ID";
    public static String REPLY_SUBSCRIPTION_ID = "REPLY_SUBSCRIPTION_ID";

    // Key for the string that's delivered in the action's intent.
    public static final String KEY_TEXT_REPLY = "KEY_TEXT_REPLY";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals(REPLY_BROADCAST_INTENT)) {
            Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
            if (remoteInput != null) {
                String address = intent.getStringExtra(REPLY_ADDRESS);
                String threadId = intent.getStringExtra(REPLY_THREAD_ID);

                int def_subscriptionId = SIMHandler.getDefaultSimSubscription(context);
                int subscriptionId = intent.getIntExtra(REPLY_SUBSCRIPTION_ID, def_subscriptionId);

                CharSequence reply = remoteInput.getCharSequence(KEY_TEXT_REPLY);
                if (reply == null || reply.toString().isEmpty())
                    return;

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String messageId = String.valueOf(System.currentTimeMillis());
                        Conversation conversation = new Conversation();
                        conversation.setAddress(address);
                        conversation.setSubscription_id(subscriptionId);
                        conversation.setThread_id(threadId);
                        conversation.setText(reply.toString());
                        conversation.setMessage_id(messageId);
                        conversation.setDate(String.valueOf(System.currentTimeMillis()));
                        conversation.setType(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX);
                        conversation.setStatus(Telephony.TextBasedSmsColumns.STATUS_PENDING);
                        ConversationDao conversationDao = Conversation.getDao(context);
                        conversationDao.insert(conversation);
                        try {
                            SMSDatabaseWrapper.send_text(context, conversation, null);

                            Intent broadcastIntent = new Intent(SMS_UPDATED_BROADCAST_INTENT);
                            broadcastIntent.putExtra(Conversation.ID, conversation.getMessage_id());
                            broadcastIntent.putExtra(Conversation.THREAD_ID, conversation.getThread_id());
                            if(intent.getExtras() != null)
                                broadcastIntent.putExtras(intent.getExtras());

                            context.sendBroadcast(broadcastIntent);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();

                Cursor cursor = NativeSMSDB.fetchByThreadId(context, threadId);
                if(cursor.moveToFirst()) {
                    Conversation conversation = Conversation.build(cursor);
                    cursor.close();


                    NotificationCompat.MessagingStyle messagingStyle =
                            NotificationsHandler.getMessagingStyle(context, conversation, reply.toString());

                    Intent replyIntent = NotificationsHandler.getReplyIntent(context, conversation);
                    PendingIntent pendingIntent = NotificationsHandler.getPendingIntent(context, conversation);

                    NotificationCompat.Builder builder =
                            NotificationsHandler.getNotificationBuilder(context, replyIntent,
                                    conversation, pendingIntent);

                    builder.setStyle(messagingStyle);

                    NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
                    notificationManagerCompat.notify(Integer.parseInt(threadId), builder.build());
                }
            }
        }
        else if(intent.getAction() != null && intent.getAction().equals(MARK_AS_READ_BROADCAST_INTENT)) {
            String threadId = intent.getStringExtra(Conversation.THREAD_ID);
            String messageId = intent.getStringExtra(Conversation.ID);
            Log.d(getClass().getName(), "Got this from mark: " + messageId);
            Log.d(getClass().getName(), "Got this from thread mark: " + threadId);
            try {
                NativeSMSDB.Incoming.update_read(context, 1, threadId, messageId);
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                notificationManager.cancel(Integer.parseInt(threadId));
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
}
