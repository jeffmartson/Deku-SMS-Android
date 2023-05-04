package com.example.swob_deku;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Base64;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.room.Room;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.swob_deku.Models.Contacts.Contacts;
import com.example.swob_deku.Models.Datastore;
import com.example.swob_deku.Models.GatewayServer.GatewayServer;
import com.example.swob_deku.Models.GatewayServer.GatewayServerDAO;
import com.example.swob_deku.Models.Images.ImageHandler;
import com.example.swob_deku.Models.Router.Router;
import com.example.swob_deku.Models.SMS.SMS;
import com.example.swob_deku.Models.SMS.SMSHandler;
import com.example.swob_deku.Models.Security.SecurityHelpers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BroadcastSMSTextActivity extends BroadcastReceiver {
    Context context;

    public static final String TAG_NAME = "RECEIVED_SMS_ROUTING";
    public static final String TAG_ROUTING_URL = "swob.work.route.url,";
    public static final String TAG_WORKER_ID = "swob.work.id.";


    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;

        if (intent.getAction().equals(Telephony.Sms.Intents.SMS_DELIVER_ACTION)) {
            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    StringBuffer messageBuffer = new StringBuffer();
                    String address = new String();

                    for (SmsMessage currentSMS : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                        // TODO: Fetch address name from contact list if present
                        address = currentSMS.getDisplayOriginatingAddress();
                        String displayMessage = currentSMS.getDisplayMessageBody();
                        displayMessage = displayMessage == null ?
                                new String(currentSMS.getUserData(), StandardCharsets.UTF_8) :
                                displayMessage;
                        messageBuffer.append(displayMessage);
                    }

                    String message = messageBuffer.toString();
                    final String finalAddress = address;

                    long messageId = -1;
                    try {
//                        SecurityDH securityDH = new SecurityDH(context);
//                        if(securityDH.hasSecretKey(finalAddress)){
//                            try {
//                                byte[] messageData = Base64.decode(message, Base64.DEFAULT);
//                                messageData = SMSSendActivity.decompress(Base64.decode(messageData, Base64.DEFAULT));
//                                message = Base64.encodeToString(messageData, Base64.DEFAULT);
////                                message = new String(messageData, StandardCharsets.UTF_8);
//                            } catch(Exception e) {
//                                e.printStackTrace();
//                            }
//                        }
                        messageId = SMSHandler.registerIncomingMessage(context, finalAddress, message);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    long finalMessageId = messageId;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            sendNotification(context, null, finalAddress, finalMessageId);
                        }
                    }).start();
                    final String messageFinal = message;

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
//                                CharsetDecoder charsetDecoder = StandardCharsets.UTF_8.newDecoder();
//                                charsetDecoder.decode(ByteBuffer.wrap(Base64.decode(message, Base64.DEFAULT)));
                                Base64.decode(messageFinal, Base64.DEFAULT);
                                createWorkForMessage(finalAddress, messageFinal, finalMessageId);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                    break;
            }
        }
    }

    private void createWorkForMessage(String address, String message, long messageId) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Datastore databaseConnector = Room.databaseBuilder(this.context, Datastore.class,
                Datastore.databaseName).build();

        new Thread(new Runnable() {
            @Override
            public void run() {
                GatewayServerDAO gatewayServerDAO = databaseConnector.gatewayServerDAO();
                List<GatewayServer> gatewayServerList = gatewayServerDAO.getAllList();

                for (GatewayServer gatewayServer : gatewayServerList) {
                    try {
                        OneTimeWorkRequest routeMessageWorkRequest = new OneTimeWorkRequest.Builder(Router.class)
                                .setConstraints(constraints)
                                .setBackoffCriteria(
                                        BackoffPolicy.LINEAR,
                                        OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                                        TimeUnit.MILLISECONDS
                                )
                                .addTag(TAG_NAME)
                                .addTag(TAG_WORKER_ID + messageId)
                                .addTag(TAG_ROUTING_URL + gatewayServer.getURL())
                                .setInputData(
                                        new Data.Builder()
                                                .putString("address", address)
                                                .putString("text", message)
                                                .putString("gatewayServerUrl", gatewayServer.getURL())
                                                .build()
                                )
                                .build();

                        // String uniqueWorkName = address + message;
                        String uniqueWorkName = messageId + ":" + gatewayServer.getURL();
                        WorkManager workManager = WorkManager.getInstance(context);
                        workManager.enqueueUniqueWork(
                                uniqueWorkName,
                                ExistingWorkPolicy.KEEP,
                                routeMessageWorkRequest);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public static void sendNotification(Context context, String text, String address, long messageId) {
        Intent receivedSmsIntent = new Intent(context, SMSSendActivity.class);

        receivedSmsIntent.putExtra(SMSSendActivity.ADDRESS, address);

        Cursor cursor = SMSHandler.fetchSMSInboxById(context, String.valueOf(messageId));

        if(cursor.moveToFirst()) {
            SMS sms = new SMS(cursor);
            String contactName = Contacts.retrieveContactName(context, address);
            contactName = (contactName.equals("null") || contactName.isEmpty()) ?
                    address : contactName;
            List<NotificationCompat.MessagingStyle.Message> unreadMessages = new ArrayList<>();
            Cursor cursor1 = SMSHandler.fetchUnreadSMSMessagesForThreadId(context, sms.getThreadId());
            if(cursor1.moveToFirst()) {
                do {
                    SMS unreadSMS = new SMS(cursor1);

                    SpannableStringBuilder spannable = new SpannableStringBuilder(contactName);

                    StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
                    StyleSpan ItalicSpan = new StyleSpan(Typeface.ITALIC);

                    spannable.setSpan(boldSpan, 0, contactName.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);

                    if(unreadSMS.getBody().contains(ImageHandler.IMAGE_HEADER)) {
                        String message = context.getString(R.string.notification_title_new_photo);
                        SpannableStringBuilder spannableMessage = new SpannableStringBuilder(message);
                        spannableMessage.setSpan(ItalicSpan, 0, message.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                        unreadMessages.add(new NotificationCompat.MessagingStyle.Message(
                                spannableMessage,
                                Long.parseLong(unreadSMS.getDate()),
                                spannable));
                    } else if(SecurityHelpers.isKeyExchange(unreadSMS.getBody())){
                        String message = context.getString(R.string.notification_title_new_key);
                        SpannableStringBuilder spannableMessage = new SpannableStringBuilder(message);
                        spannableMessage.setSpan(ItalicSpan, 0, message.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                        unreadMessages.add(new NotificationCompat.MessagingStyle.Message(
                                spannableMessage,
                                Long.parseLong(unreadSMS.getDate()),
                                spannable));
                    } else {
                        unreadMessages.add(new NotificationCompat.MessagingStyle.Message(
                                unreadSMS.getBody() + "\n",
                                Long.parseLong(unreadSMS.getDate()), spannable));
                    }
                } while(cursor1.moveToNext());
            }
            cursor1.close();

            receivedSmsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            // TODO: check request code and make some changes
            PendingIntent pendingReceivedSmsIntent = PendingIntent.getActivity( context,
                    Integer.parseInt(sms.getThreadId()),
                    receivedSmsIntent, PendingIntent.FLAG_IMMUTABLE);
//                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    context, context.getString(R.string.CHANNEL_ID))
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentIntent(pendingReceivedSmsIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE);

            NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle("Me");
            messagingStyle.setConversationTitle(context.getString(R.string.notification_title));
//            messagingStyle.setConversationTitle(contactName);
            for(NotificationCompat.MessagingStyle.Message message : unreadMessages) {
                messagingStyle.addMessage(message);
            }
            builder.setStyle(messagingStyle);

            /**
             * TODO: Using the same ID leaves notifications updated (not appended).
             * TODO: Recommendation: use groups for notifications to allow for appending them.
             */
            notificationManager.notify(Integer.parseInt(sms.getThreadId()), builder.build());
//            notificationManager.notify(Integer.parseInt(sms.id), builder.build());
        }
        cursor.close();
    }
}