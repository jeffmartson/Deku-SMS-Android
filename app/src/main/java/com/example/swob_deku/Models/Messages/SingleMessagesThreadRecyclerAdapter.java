package com.example.swob_deku.Models.Messages;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.text.format.DateUtils;
import android.text.style.URLSpan;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.compose.animation.core.Animation;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swob_deku.BroadcastReceivers.IncomingTextSMSBroadcastReceiver;
import com.example.swob_deku.Commons.Helpers;
import com.example.swob_deku.ImageViewActivity;
import com.example.swob_deku.Models.Compression;
import com.example.swob_deku.Models.Images.ImageHandler;
import com.example.swob_deku.Models.SIMHandler;
import com.example.swob_deku.Models.SMS.SMS;
import com.example.swob_deku.Models.SMS.SMSHandler;
import com.example.swob_deku.Models.Security.SecurityECDH;
import com.example.swob_deku.Models.Security.SecurityHelpers;
import com.example.swob_deku.R;
import com.example.swob_deku.SMSSendActivity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

//public class SingleMessagesThreadRecyclerAdapter extends PagingDataAdapter<SMS, RecyclerView.ViewHolder> {
public class SingleMessagesThreadRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    Context context;
    Toolbar toolbar;
    String highlightedText;
    View highlightedView;

    private int selectedItemAbsPosition = RecyclerView.NO_POSITION;
    public LiveData<HashMap<String, RecyclerView.ViewHolder>> selectedItem = new MutableLiveData<>();
    MutableLiveData<HashMap<String, RecyclerView.ViewHolder>> mutableSelectedItems = new MutableLiveData<>();
    public MutableLiveData<String[]> retryFailedMessage = new MutableLiveData<>();
    public MutableLiveData<String[]> retryFailedDataMessage = new MutableLiveData<>();

    private final AsyncListDiffer<SMS> mDiffer = new AsyncListDiffer(this, SMS.DIFF_CALLBACK);


    final int MESSAGE_TYPE_ALL = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_ALL;
    final int MESSAGE_TYPE_INBOX = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX;
    final int MESSAGE_TYPE_OUTBOX = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX;

    final int MESSAGE_KEY_INBOX = 400;
    final int MESSAGE_KEY_OUTBOX = 500;

    final int TIMESTAMP_MESSAGE_TYPE_INBOX = 600;
    final int TIMESTAMP_MESSAGE_TYPE_OUTBOX = 700;

    final int TIMESTAMP_KEY_TYPE_INBOX = 800;
    final int TIMESTAMP_KEY_TYPE_OUTBOX = 900;

    final int MESSAGE_TYPE_SENT = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT;
    final int MESSAGE_TYPE_DRAFT = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT;
    final int MESSAGE_TYPE_FAILED = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED;
    final int MESSAGE_TYPE_QUEUED = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_QUEUED;

    SecurityECDH securityECDH;
    byte[] secretKey = null;

    String address;

    private boolean animation = false;
    public SingleMessagesThreadRecyclerAdapter(Context context, String address) throws GeneralSecurityException, IOException {
//        super(SMS.DIFF_CALLBACK);
        this.context = context;
        this.selectedItem = mutableSelectedItems;

        this.securityECDH = new SecurityECDH(context);

        this.address = address;

        if(securityECDH.hasSecretKey(address))
            secretKey = Base64.decode(securityECDH.securelyFetchSecretKey(address), Base64.DEFAULT);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns#MESSAGE_TYPE_OUTBOX
        LayoutInflater inflater = LayoutInflater.from(this.context);

        if( viewType == TIMESTAMP_MESSAGE_TYPE_INBOX ) {
            View view = inflater.inflate(R.layout.messages_thread_received_layout, parent, false);
            return new TimestampMessageReceivedViewHandler(view);
        }
        else if( viewType == MESSAGE_TYPE_INBOX ) {
            View view = inflater.inflate(R.layout.messages_thread_received_layout, parent, false);
            return new MessageReceivedViewHandler(view);
        }
        else if( viewType == TIMESTAMP_MESSAGE_TYPE_OUTBOX ) {
            View view = inflater.inflate(R.layout.messages_thread_sent_layout, parent, false);
            return new TimestampMessageSentViewHandler(view);
        }
        else if( viewType == MESSAGE_KEY_OUTBOX ) {
            View view = inflater.inflate(R.layout.messages_thread_sent_layout, parent, false);
            return new KeySentViewHandler(view);
        }
        else if( viewType == TIMESTAMP_KEY_TYPE_OUTBOX ) {
            View view = inflater.inflate(R.layout.messages_thread_sent_layout, parent, false);
            return new TimestampKeySentViewHandler(view);
        }
        else if( viewType == MESSAGE_KEY_INBOX ) {
            View view = inflater.inflate(R.layout.messages_thread_received_layout, parent, false);
            return new KeyReceivedViewHandler(view);
        }
        else if( viewType == TIMESTAMP_KEY_TYPE_INBOX ) {
            View view = inflater.inflate(R.layout.messages_thread_received_layout, parent, false);
            return new TimestampKeyReceivedViewHandler(view);
        }

        View view = inflater.inflate(R.layout.messages_thread_sent_layout, parent, false);
        return new MessageSentViewHandler(view);
    }

    public void submitList(List<SMS> smsList) {
        animation = true;
        Log.d(getClass().getName(), "Submitting a new list received...");
        mDiffer.submitList(smsList);
    }

    private String decryptContent(String input) {
        if(this.secretKey != null &&
                input.getBytes(StandardCharsets.UTF_8).length > 16
                        + SecurityHelpers.ENCRYPTED_WATERMARK_START.length()
                        + SecurityHelpers.ENCRYPTED_WATERMARK_END.length()
                && SecurityHelpers.containersWaterMark(input)) {
            try {
                byte[] encryptedContent = SecurityECDH.decryptAES(Base64.decode(
                        SecurityHelpers.removeEncryptedMessageWaterMark(input), Base64.DEFAULT),
                        secretKey);
                input = new String(encryptedContent, StandardCharsets.UTF_8);
            } catch(Throwable e ) {
                e.printStackTrace();
            }
        }
        return input;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final SMS sms = mDiffer.getCurrentList().get(position);
        final String smsId = sms.getId();

        if(animation) {
            AnimatorSet animatorSet = new AnimatorSet();
            // Translation animation: Move the item up from the bottom
            ObjectAnimator translationAnim = ObjectAnimator.ofFloat(holder.itemView, "translationY", 200f, 0f);
            translationAnim.setDuration(200);

            // Fade-in animation: Make the item gradually appear
            ObjectAnimator fadeAnim = ObjectAnimator.ofFloat(holder.itemView, "alpha", 0f, 1f);
            fadeAnim.setDuration(200);

            // Play both animations together
            animatorSet.playTogether(translationAnim, fadeAnim);
            animatorSet.start();
            animation = false;
        }

        String date = Helpers.formatDateExtended(context, Long.parseLong(sms.getDate()));

        DateFormat dateFormat = new SimpleDateFormat("h:mm a");
        String timeStamp = dateFormat.format(new Date(Long.parseLong(sms.getDate())));

        String _text = sms.getBody();

        if(holder instanceof MessageReceivedViewHandler) {
            final String text = decryptContent(_text);
            MessageReceivedViewHandler messageReceivedViewHandler = (MessageReceivedViewHandler) holder;
            if(holder instanceof TimestampMessageReceivedViewHandler || holder instanceof TimestampKeyReceivedViewHandler)
                messageReceivedViewHandler.timestamp.setText(date);
            else
                messageReceivedViewHandler.timestamp.setVisibility(View.GONE);

            TextView receivedMessage = messageReceivedViewHandler.receivedMessage;

            TextView dateView = messageReceivedViewHandler.date;
            int dateViewVisibility = position == 0 ? View.VISIBLE : View.INVISIBLE;

            dateView.setVisibility(dateViewVisibility);

            Helpers.highlightLinks(receivedMessage, text, context.getColor(R.color.primary_text_color));

            dateView.setText(timeStamp);

            messageReceivedViewHandler.receivedMessage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(isHighlighted(sms.getId()))
                        resetSelectedItem(sms.id, true);
                    else if(selectedItem.getValue() != null ){
                        longClickHighlight(messageReceivedViewHandler, smsId);
                    } else {
                        dateView.setVisibility(dateView.getVisibility() == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                    }
                }
            });

            messageReceivedViewHandler.receivedMessage.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return longClickHighlight(messageReceivedViewHandler, sms.getId());
                }
            });

        }
        else if(holder instanceof MessageSentViewHandler){
            final String text = decryptContent(_text);
            MessageSentViewHandler messageSentViewHandler = (MessageSentViewHandler) holder;
            if(position != 0) {
                messageSentViewHandler.date.setVisibility(View.INVISIBLE);
                messageSentViewHandler.sentMessageStatus.setVisibility(View.INVISIBLE);
            }
            messageSentViewHandler.date.setText(timeStamp);

            if(holder instanceof TimestampMessageSentViewHandler || holder instanceof  TimestampKeySentViewHandler)
                messageSentViewHandler.timestamp.setText(date);
            else
                messageSentViewHandler.timestamp.setVisibility(View.GONE);

            final int status = sms.getStatusCode();
            String statusMessage = status == Telephony.TextBasedSmsColumns.STATUS_COMPLETE ?
                    context.getString(R.string.sms_status_delivered) : context.getString(R.string.sms_status_sent);

            if(status == Telephony.TextBasedSmsColumns.STATUS_PENDING )
                statusMessage = context.getString(R.string.sms_status_sending);
            if(status == Telephony.TextBasedSmsColumns.STATUS_FAILED ) {
                statusMessage = context.getString(R.string.sms_status_failed);
                messageSentViewHandler.sentMessageStatus.setVisibility(View.VISIBLE);
                messageSentViewHandler.date.setVisibility(View.VISIBLE);
                messageSentViewHandler.sentMessageStatus.setTextColor(
                        context.getResources().getColor(R.color.failed_red, context.getTheme()));
                messageSentViewHandler.date.setTextColor(
                        context.getResources().getColor(R.color.failed_red, context.getTheme()));
            } else {
                statusMessage = "• " + statusMessage;
                messageSentViewHandler.sentMessageStatus.invalidate();
                messageSentViewHandler.date.invalidate();
            }

            messageSentViewHandler.sentMessageStatus.setText(statusMessage);

            Helpers.highlightLinks(messageSentViewHandler.sentMessage, text,
                    context.getColor(R.color.primary_background_color));


            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(isHighlighted(sms.getId()))
                        resetSelectedItem(sms.id, true);
                    else if(selectedItem.getValue() != null) {
                        longClickHighlight(messageSentViewHandler, smsId);
                    }
                    else if(status == Telephony.TextBasedSmsColumns.STATUS_FAILED) {
                        String[] messageValues = new String[2];
                        messageValues[0] = sms.id;

                        String _text = text;
                        if(holder instanceof KeySentViewHandler) {
                            _text = SecurityHelpers.removeKeyWaterMark(text);
                            messageValues[1] = _text;
                            retryFailedDataMessage.setValue(messageValues);
                        }
                        else {
                            messageValues[1] = _text;
                            retryFailedMessage.setValue(messageValues);
                        }
                    }
                    else {
                        int visibility = messageSentViewHandler.date.getVisibility() == View.VISIBLE ?
                                View.INVISIBLE : View.VISIBLE;
                        messageSentViewHandler.date.setVisibility(visibility);
                        messageSentViewHandler.sentMessageStatus.setVisibility(visibility);
                    }
                }
            };

            messageSentViewHandler.imageView.setOnClickListener(onClickListener);
            messageSentViewHandler.sentMessage.setOnClickListener(onClickListener);
            messageSentViewHandler.sentMessage.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return longClickHighlight(messageSentViewHandler, smsId);
                }
            });
        }

        checkForAbsPositioning(smsId, holder);
    }

    private boolean longClickHighlight(RecyclerView.ViewHolder holder, String smsId) {
        if(holder instanceof MessageReceivedViewHandler) {
            MessageReceivedViewHandler messageReceivedViewHandler = (MessageReceivedViewHandler) holder;
            if (selectedItem.getValue() == null || selectedItem.getValue().isEmpty()) {
                List<String> newItems = new ArrayList<>();
                newItems.add(smsId);
                mutableSelectedItems.setValue(new HashMap<String, RecyclerView.ViewHolder>() {{
                    put(smsId, messageReceivedViewHandler);
                }});
                messageReceivedViewHandler.highlight();
                messageReceivedViewHandler.setIsRecyclable(false);
                return true;
            } else if (!selectedItem.getValue().containsKey(smsId)) {
                HashMap<String, RecyclerView.ViewHolder> previousItems = selectedItem.getValue();
                previousItems.put(smsId, messageReceivedViewHandler);
                mutableSelectedItems.setValue(previousItems);
                messageReceivedViewHandler.highlight();
                messageReceivedViewHandler.setIsRecyclable(false);
                return true;
            }
        }
        else {
            MessageSentViewHandler messageSentViewHandler = (MessageSentViewHandler) holder;
            if(selectedItem.getValue() == null || selectedItem.getValue().isEmpty()) {
                List<String> newItems = new ArrayList<>();
                newItems.add(smsId);
                mutableSelectedItems.setValue(new HashMap<String, RecyclerView.ViewHolder>(){{put(smsId, messageSentViewHandler);}});
                messageSentViewHandler.highlight();
                messageSentViewHandler.setIsRecyclable(false);
                return true;
            }
            else if(!selectedItem.getValue().containsKey(smsId)) {
                HashMap<String, RecyclerView.ViewHolder> previousItems = selectedItem.getValue();
                previousItems.put(smsId, messageSentViewHandler);
                mutableSelectedItems.setValue(previousItems);
                messageSentViewHandler.highlight();
                messageSentViewHandler.setIsRecyclable(false);
                return true;
            }
        }
        return false;
    }

    public boolean isHighlighted(String smsId){
        if(selectedItem.getValue() == null)
            return false;
        return selectedItem.getValue().containsKey(smsId);
    }

    public void checkForAbsPositioning(String smsId, RecyclerView.ViewHolder holder) {
        if(selectedItem.getValue() != null && selectedItem.getValue().containsKey(smsId)) {
            Log.d(getClass().getName(), "Content should be highlighted now!");

            if (holder instanceof MessageReceivedViewHandler)
                ((MessageReceivedViewHandler) holder).highlight();

            else if (holder instanceof MessageSentViewHandler)
                ((MessageSentViewHandler) holder).highlight();
            holder.setIsRecyclable(false);
        }
    }

    public void resetSelectedItem(String key, boolean removeList) {
        HashMap<String, RecyclerView.ViewHolder> items = mutableSelectedItems.getValue();
        if(items != null) {
            RecyclerView.ViewHolder view = items.get(key);

            if(view != null) {
                view.setIsRecyclable(true);
                if (view instanceof MessageReceivedViewHandler)
                    ((MessageReceivedViewHandler) view).unHighlight();

                else if (view instanceof MessageSentViewHandler)
                    ((MessageSentViewHandler) view).unHighlight();
            }
            if(removeList) {
                items.remove(key);
                mutableSelectedItems.setValue(items);
            }
        }
    }

    public void resetAllSelectedItems() {
        HashMap<String, RecyclerView.ViewHolder> items = mutableSelectedItems.getValue();

        for(String key: items.keySet())
            resetSelectedItem(key, false);

        mutableSelectedItems.setValue(new HashMap<>());
    }

    public boolean hasSelectedItems() {
        return !(mutableSelectedItems.getValue() == null || mutableSelectedItems.getValue().isEmpty());
    }

    @Override
    public int getItemViewType(int position) {
        List<SMS> snapshotList = mDiffer.getCurrentList();
        SMS sms = snapshotList.get(position);

        boolean isEncryptionKey = SecurityHelpers.isKeyExchange(sms.getBody());

        int viewType = 0;
        if (position == snapshotList.size() - 1 ||
                !SMSHandler.isSameHour(sms, (SMS) snapshotList.get(position + 1))) {
            if(isEncryptionKey) {
                viewType = (sms.getType() == MESSAGE_TYPE_INBOX) ?
                        TIMESTAMP_KEY_TYPE_INBOX : TIMESTAMP_KEY_TYPE_OUTBOX;
            }
            else
                viewType = (sms.getType() == MESSAGE_TYPE_INBOX) ?
                        TIMESTAMP_MESSAGE_TYPE_INBOX : TIMESTAMP_MESSAGE_TYPE_OUTBOX;
        } else {
            if(isEncryptionKey) {
                viewType = (sms.getType() == MESSAGE_TYPE_INBOX) ?
                        MESSAGE_KEY_INBOX : MESSAGE_KEY_OUTBOX;
            }
            else
                viewType = sms.getType();
        }
        return viewType;
    }

    @Override
    public int getItemCount() {
        return mDiffer.getCurrentList().size();
    }

    public void removeAllItems(String[] _keys) {
        List<String> keys = new ArrayList<>(Arrays.asList(_keys));
        List<SMS> sms = new ArrayList<>(mDiffer.getCurrentList());
        List<SMS> smsNew = new ArrayList<>();
        for(SMS sms1 : sms)
            if(!keys.contains(sms1.getId()))
                smsNew.add(sms1);

        mDiffer.submitList(smsNew);
    }

    public void removeItem(String keys) {
        List<SMS> sms = new ArrayList<>(mDiffer.getCurrentList());
        for(int i=0; i< sms.size(); ++i) {
            if(sms.get(i).getId().equals(keys)) {
                sms.remove(i);
                break;
            }
        }
        mDiffer.submitList(sms);
    }

    public static class MessageSentViewHandler extends RecyclerView.ViewHolder {
         TextView sentMessage;
         TextView sentMessageStatus;
         TextView date;
         TextView timestamp;
        ImageView imageView;

         ConstraintLayout constraintLayout, imageConstraintLayout;
        public MessageSentViewHandler(@NonNull View itemView) {
            super(itemView);
            sentMessage = itemView.findViewById(R.id.message_sent_text);
            sentMessageStatus = itemView.findViewById(R.id.message_thread_sent_status_text);
            date = itemView.findViewById(R.id.message_thread_sent_date_text);
            timestamp = itemView.findViewById(R.id.sent_message_date_segment);
            constraintLayout = itemView.findViewById(R.id.message_sent_constraint);
            imageConstraintLayout = itemView.findViewById(R.id.message_sent_image_container);
            imageView = itemView.findViewById(R.id.message_sent_image_view);
        }

        static class ClickableURLSpan extends URLSpan {
            ClickableURLSpan(String url) {
                super(url);
            }

            @Override
            public void onClick(View widget) {
                Log.d(getClass().getName(), "Link clicked!");
                Uri uri = Uri.parse(getURL());
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
                widget.getContext().startActivity(intent);
            }
        }

        public void highlight() {
            constraintLayout.setBackgroundResource(R.drawable.sent_messages_highlighted_drawable);
        }

        public void unHighlight() {
            constraintLayout.setBackgroundResource(R.drawable.sent_messages_drawable);
        }
    }

    public static class TimestampMessageSentViewHandler extends MessageSentViewHandler {
        public TimestampMessageSentViewHandler(@NonNull View itemView) {
            super(itemView);
        }
    }

    public static class KeySentViewHandler extends MessageSentViewHandler {
        public KeySentViewHandler(@NonNull View itemView) {
            super(itemView);
            imageView.setImageDrawable(itemView.getContext().getDrawable(R.drawable.round_key_24));
            imageConstraintLayout.setVisibility(View.VISIBLE);
            constraintLayout.setVisibility(View.GONE);
        }

        public void highlight() {
            constraintLayout.setBackgroundResource(R.drawable.sent_messages_highlighted_drawable);
        }

        public void unHighlight() {
            constraintLayout.setBackgroundResource(R.drawable.sent_messages_drawable);
        }
    }

    public static class TimestampKeySentViewHandler extends KeySentViewHandler {
        public TimestampKeySentViewHandler(@NonNull View itemView) {
            super(itemView);
        }
    }

    public static class MessageReceivedViewHandler extends RecyclerView.ViewHolder {
        TextView receivedMessage;
        TextView date;
        TextView timestamp;
        ImageView imageView;
        ConstraintLayout constraintLayout, imageConstraintLayout;

        public MessageReceivedViewHandler(@NonNull View itemView) {
            super(itemView);
            receivedMessage = itemView.findViewById(R.id.message_received_text);
            date = itemView.findViewById(R.id.message_thread_received_date_text);
            timestamp = itemView.findViewById(R.id.received_message_date_segment);
            constraintLayout = itemView.findViewById(R.id.message_received_constraint);
            imageConstraintLayout = itemView.findViewById(R.id.message_received_image_container);
            imageView = itemView.findViewById(R.id.message_received_image_view);

        }

        public void highlight() {
            constraintLayout.setBackgroundResource(R.drawable.received_messages_highlighted_drawable);
        }

        public void unHighlight() {
            constraintLayout.setBackgroundResource(R.drawable.received_messages_drawable);
        }
    }
    public static class TimestampMessageReceivedViewHandler extends MessageReceivedViewHandler {
        public TimestampMessageReceivedViewHandler(@NonNull View itemView) {
            super(itemView);
        }
    }

    public static class KeyReceivedViewHandler extends MessageReceivedViewHandler {

        public KeyReceivedViewHandler(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.message_received_image_view);

            imageView.setImageDrawable(itemView.getContext().getDrawable(R.drawable.round_key_24));
            imageConstraintLayout.setVisibility(View.VISIBLE);
            constraintLayout.setVisibility(View.GONE);
        }

        public void highlight() {
            constraintLayout.setBackgroundResource(R.drawable.received_messages_highlighted_drawable);
        }

        public void unHighlight() {
            constraintLayout.setBackgroundResource(R.drawable.received_messages_drawable);
        }
    }
    public static class TimestampKeyReceivedViewHandler extends KeyReceivedViewHandler {
        public TimestampKeyReceivedViewHandler(@NonNull View itemView) {
            super(itemView);
        }
    }

}
