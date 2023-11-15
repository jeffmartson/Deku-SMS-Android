package com.afkanerd.deku.DefaultSMS.BroadcastReceivers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.provider.Telephony;
import android.util.Log;

import com.afkanerd.deku.DefaultSMS.BuildConfig;
import com.afkanerd.deku.DefaultSMS.DAO.ConversationDao;
import com.afkanerd.deku.DefaultSMS.Models.Conversations.Conversation;
import com.afkanerd.deku.DefaultSMS.Models.NativeSMSDB;
import com.afkanerd.deku.DefaultSMS.Models.NotificationsHandler;
import com.afkanerd.deku.E2EE.E2EEHandler;
import com.afkanerd.deku.Router.Router.RouterItem;
import com.afkanerd.deku.Router.Router.RouterHandler;

import java.io.IOException;

public class IncomingTextSMSBroadcastReceiver extends BroadcastReceiver {
    Context context;

    public static final String TAG_NAME = "RECEIVED_SMS_ROUTING";
    public static final String TAG_ROUTING_URL = "swob.work.route.url,";


    public static String SMS_DELIVER_ACTION =
            BuildConfig.APPLICATION_ID + ".SMS_DELIVER_ACTION";
    public static String SMS_SENT_BROADCAST_INTENT =
            BuildConfig.APPLICATION_ID + ".SMS_SENT_BROADCAST_INTENT";

    public static String SMS_DELIVERED_BROADCAST_INTENT =
            BuildConfig.APPLICATION_ID + ".SMS_DELIVERED_BROADCAST_INTENT";

    public static String DATA_SENT_BROADCAST_INTENT =
            BuildConfig.APPLICATION_ID + ".DATA_SENT_BROADCAST_INTENT";

    public static String DATA_DELIVERED_BROADCAST_INTENT =
            BuildConfig.APPLICATION_ID + ".DATA_DELIVERED_BROADCAST_INTENT";

    /*
    - address received might be different from how address is saved.
    - how it received is the trusted one, but won't match that which has been saved.
    - when message gets stored it's associated to the thread - so matching is done by android
    - without country code, can't know where message is coming from. Therefore best assumption is
    - service providers do send in country code.
    - How is matched to users stored without country code?
     */


    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;

        if (intent.getAction().equals(Telephony.Sms.Intents.SMS_DELIVER_ACTION)) {
            if (getResultCode() == Activity.RESULT_OK) {
                try {
                    final String[] regIncomingOutput = NativeSMSDB.Incoming.register_incoming_text(context, intent);
                    if(regIncomingOutput != null) {
                        final String messageId = regIncomingOutput[NativeSMSDB.MESSAGE_ID];
                        final String text = regIncomingOutput[NativeSMSDB.BODY];
                        final String threadId = regIncomingOutput[NativeSMSDB.THREAD_ID];
                        final String address = regIncomingOutput[NativeSMSDB.ADDRESS];
                        final String dateSent = regIncomingOutput[NativeSMSDB.DATE_SENT];
                        final int subscriptionId = Integer.parseInt(regIncomingOutput[NativeSMSDB.SUBSCRIPTION_ID]);

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Conversation conversation = new Conversation();
                                conversation.setMessage_id(messageId);
                                conversation.setText(text);
                                conversation.setThread_id(threadId);
                                conversation.setType(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX);
                                conversation.setAddress(address);
                                conversation.setSubscription_id(subscriptionId);
                                conversation.setDate(dateSent);
                                ConversationDao conversationDao = Conversation.getDao(context);
                                conversationDao.insert(conversation);

                                Intent broadcastIntent = new Intent(SMS_DELIVER_ACTION);
                                broadcastIntent.putExtra(Conversation.ID, messageId);
                                context.sendBroadcast(broadcastIntent);

                                NotificationsHandler.sendIncomingTextMessageNotification(context,
                                        conversation);
                            }
                        }).start();

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
//                                handleEncryption(text);
                                router_activities(messageId);
                            }
                        }).start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        else if(intent.getAction().equals(SMS_SENT_BROADCAST_INTENT)) {
            String id = intent.getStringExtra(NativeSMSDB.ID);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ConversationDao conversationDao = Conversation.getDao(context);
                    Conversation conversation = conversationDao.getMessage(id);
                    if (getResultCode() == Activity.RESULT_OK) {
                        NativeSMSDB.Outgoing.register_sent(context, id);
                        conversation.setStatus(Telephony.TextBasedSmsColumns.STATUS_NONE);
                    } else {
                        try {
                            NativeSMSDB.Outgoing.register_failed(context, id, getResultCode());
                            conversation.setStatus(Telephony.TextBasedSmsColumns.STATUS_FAILED);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    conversationDao.update(conversation);
                }
            }).start();
        }
        else if(intent.getAction().equals(SMS_DELIVERED_BROADCAST_INTENT)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String id = intent.getStringExtra(NativeSMSDB.ID);
                    ConversationDao conversationDao = Conversation.getDao(context);
                    Conversation conversation = conversationDao.getMessage(id);
                    if (getResultCode() == Activity.RESULT_OK) {
                        NativeSMSDB.Outgoing.register_delivered(context, id);
                        conversation.setStatus(Telephony.TextBasedSmsColumns.STATUS_COMPLETE);
                    } else {
                        if (BuildConfig.DEBUG)
                            Log.d(getClass().getName(), "Broadcast received Failed to deliver: "
                                    + getResultCode());
                    }
                    conversationDao.update(conversation);
                }
            }).start();
        }
        else if(intent.getAction().equals(DATA_SENT_BROADCAST_INTENT)) {
            String id = intent.getStringExtra(NativeSMSDB.ID);
            if (getResultCode() == Activity.RESULT_OK) {
                ConversationDao conversationDao = Conversation.getDao(context);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Conversation conversation = conversationDao.getMessage(id);
                        conversation.setStatus(Telephony.TextBasedSmsColumns.STATUS_NONE);
                        conversationDao.update(conversation);
                    }
                }).start();
            } else {
                if (BuildConfig.DEBUG)
                    Log.d(getClass().getName(), "Broadcast received Failed to deliver: "
                            + getResultCode());
            }
        }
        else if(intent.getAction().equals(DATA_DELIVERED_BROADCAST_INTENT)) {
            String id = intent.getStringExtra(NativeSMSDB.ID);
            if (getResultCode() == Activity.RESULT_OK) {
                ConversationDao conversationDao = Conversation.getDao(context);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Conversation conversation = conversationDao.getMessage(id);
                        conversation.setStatus(Telephony.TextBasedSmsColumns.STATUS_COMPLETE);
                        conversationDao.update(conversation);
                    }
                }).start();
            } else {
                if (BuildConfig.DEBUG)
                    Log.d(getClass().getName(), "Broadcast received Failed to deliver: "
                            + getResultCode());
            }
        }
    }

//    private void handleEncryption(String text) {
//        if(E2EEHandler.isValidDekuPublicKey(text)) {
//
//        }
//    }

    public void router_activities(String messageId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Cursor cursor = NativeSMSDB.fetchByMessageId(context, messageId);
                    if(cursor.moveToFirst()) {
                        RouterItem routerItem = new RouterItem(cursor);
                        cursor.close();
                        RouterHandler.route(context, routerItem);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}