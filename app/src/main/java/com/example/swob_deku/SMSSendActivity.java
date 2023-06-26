package com.example.swob_deku;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.security.crypto.EncryptedSharedPreferences;

import com.example.swob_deku.Models.Archive.ArchiveHandler;
import com.example.swob_deku.Models.CustomAppCompactActivity;
import com.example.swob_deku.Models.Messages.SingleMessageViewModel;
import com.example.swob_deku.Models.Messages.SingleMessagesThreadRecyclerAdapter;
import com.example.swob_deku.Models.SIMHandler;
import com.example.swob_deku.Models.SMS.SMS;
import com.example.swob_deku.Models.SMS.SMSHandler;
import com.example.swob_deku.Models.Security.SecurityECDH;
import com.example.swob_deku.Models.Security.SecurityHelpers;
import com.example.swob_deku.Settings.SettingsHandler;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMSSendActivity extends CustomAppCompactActivity {
    public static final String COMPRESSED_IMAGE_BYTES = "COMPRESSED_IMAGE_BYTES";
    public static final String IMAGE_URI = "IMAGE_URI";
    public static final String SEARCH_STRING = "search_string";
    public static final String SEARCH_OFFSET = "search_offset";
    public static final String SEARCH_POSITION = "search_position";
    public static final String SMS_SENT_INTENT = "SMS_SENT";
    public static final String SMS_DELIVERED_INTENT = "SMS_DELIVERED";
    public static final int SEND_SMS_PERMISSION_REQUEST_CODE = 1;
    private final int RESULT_GALLERY = 100;
    SingleMessagesThreadRecyclerAdapter singleMessagesThreadRecyclerAdapter;
    SingleMessageViewModel singleMessageViewModel;
    TextInputEditText smsTextView;
    ConstraintLayout multiSimcardConstraint;
    MutableLiveData<String> mutableLiveDataComposeMessage = new MutableLiveData<>();

    Toolbar toolbar;
    ActionBar ab;

    LinearLayoutManager linearLayoutManager;
    RecyclerView singleMessagesThreadRecyclerView;

    SMS.SMSMetaEntity smsMetaEntity;

    SharedPreferences sharedPreferences;
    SharedPreferences.OnSharedPreferenceChangeListener onSharedPreferenceChangeListener;
    int defaultSubscriptionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_smsactivity);

        try {
            _setupActivityDependencies();
            _instantiateGlobals();
            _configureToolbars();
            _configureRecyclerView();
            _configureMessagesTextBox();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        configureBroadcastListeners(new Runnable() {
            @Override
            public void run() {
                if(getIntent().hasExtra(SMS.SMSMetaEntity.THREAD_ID)) {
                    if(singleMessageViewModel.threadId == null)
                        singleMessageViewModel.informNewItemChanges(getApplicationContext(),
                                smsMetaEntity.getThreadId());
                    else
                        singleMessageViewModel.informNewItemChanges(getApplicationContext());
                    cancelNotifications(smsMetaEntity.getThreadId());
                    try {
                        _checkEncryptionStatus();
                    } catch (GeneralSecurityException | IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        _configureLayoutForMessageType();
        _configureEncryptionListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        new Thread(new Runnable() {
            @Override
            public void run() {
                if(getIntent().hasExtra(SMS.SMSMetaEntity.THREAD_ID))
                    _updateThreadToRead();
            }
        }).start();

        if(!smsMetaEntity.isShortCode() &&
                !SettingsHandler.alertNotEncryptedCommunicationDisabled(getApplicationContext())) {
            try {
                _checkEncryptionStatus();
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!smsMetaEntity.isShortCode())
            getMenuInflater().inflate(R.menu.single_messages_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home
                && singleMessagesThreadRecyclerAdapter.hasSelectedItems()) {
            singleMessagesThreadRecyclerAdapter.resetAllSelectedItems();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void _setupActivityDependencies() throws Exception {
        /**
         * Address = This could come from Shared Intent, Contacts etc
         * ThreadID = This comes from Thread screen and notifications
         * ThreadID is the intended way of populating the messages
         * ==> If not ThreadId do not populate, everything else should take the pleasure of finding
         * and sending a threadID to this intent
         */

        smsMetaEntity = new SMS.SMSMetaEntity();
        if(getIntent().getAction() != null && getIntent().getAction().equals(Intent.ACTION_SENDTO)) {
            String sendToString = getIntent().getDataString();
            if (sendToString.contains("smsto:") || sendToString.contains("sms:")) {
                getIntent().putExtra(SMS.SMSMetaEntity.ADDRESS,
                        sendToString.substring(sendToString.indexOf(':') + 1));
                Log.d(getLocalClassName(), "Send to data received");
            }
        }

        if(!getIntent().hasExtra(SMS.SMSMetaEntity.THREAD_ID) &&
                !getIntent().hasExtra(SMS.SMSMetaEntity.ADDRESS)) {
            throw new Exception("No threadId nor Address supplied for activity");
        }

        if(getIntent().hasExtra(SMS.SMSMetaEntity.THREAD_ID))
            smsMetaEntity.setThreadId(getApplicationContext(),
                    getIntent().getStringExtra(SMS.SMSMetaEntity.THREAD_ID));

        if(getIntent().hasExtra(SMS.SMSMetaEntity.ADDRESS)) {
            smsMetaEntity.setAddress(getApplicationContext(),
                    getIntent().getStringExtra(SMS.SMSMetaEntity.ADDRESS));
        }
    }

    private void _instantiateGlobals() throws GeneralSecurityException, IOException {
        toolbar = (Toolbar) findViewById(R.id.send_smsactivity_toolbar);
        setSupportActionBar(toolbar);
        ab = getSupportActionBar();

        smsTextView = findViewById(R.id.sms_text);
        multiSimcardConstraint = findViewById(R.id.simcard_select_constraint);
        singleMessagesThreadRecyclerView = findViewById(R.id.single_messages_thread_recycler_view);

        singleMessagesThreadRecyclerAdapter = new SingleMessagesThreadRecyclerAdapter(getApplicationContext(),
                smsMetaEntity.getAddress());

        singleMessageViewModel = new ViewModelProvider(this)
                .get(SingleMessageViewModel.class);

        linearLayoutManager = new LinearLayoutManager(this);

        defaultSubscriptionId = SIMHandler.getDefaultSimSubscription(getApplicationContext());

        sharedPreferences = getSharedPreferences(smsMetaEntity.getAddress(), Context.MODE_PRIVATE);
        onSharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                // Keys are encrypted so can't check for specifc entries
                try {
                    _checkEncryptionStatus();
                    if(sharedPreferences.contains(key))
                        _configureMessagesTextBox();
                } catch (GeneralSecurityException | IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private void _configureRecyclerView() {
        linearLayoutManager.setStackFromEnd(false);
        linearLayoutManager.setReverseLayout(true);

        singleMessagesThreadRecyclerView.setLayoutManager(linearLayoutManager);
        singleMessagesThreadRecyclerView.setAdapter(singleMessagesThreadRecyclerAdapter);


        int offset = getIntent().getIntExtra(SEARCH_OFFSET, 0);

        singleMessageViewModel.getMessages(
                getApplicationContext(), smsMetaEntity.getThreadId(), offset).observe(this, new Observer<List<SMS>>() {
            @Override
            public void onChanged(List<SMS> smsList) {
                Log.d(getLocalClassName(), "Paging data changed!");
                singleMessagesThreadRecyclerAdapter.mDiffer.submitList(smsList);
                if (getIntent().hasExtra(SEARCH_POSITION))
                    singleMessagesThreadRecyclerView.scrollToPosition(
                            getIntent().getIntExtra(SEARCH_POSITION, -1));
            }
        });

        singleMessagesThreadRecyclerAdapter.retryFailedMessage.observe(this, new Observer<String[]>() {
            @Override
            public void onChanged(String[] strings) {
                // TODO: fix this
//                try {
//                    SMSHandler.deleteMessage(getApplicationContext(), strings[0]);
//                    sendSMSMessage(null, strings[1], null);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
            }
        });

        singleMessagesThreadRecyclerAdapter.retryFailedDataMessage.observe(this, new Observer<String[]>() {
            @Override
            public void onChanged(String[] strings) {
                try {
                    // TODO: fix this
//                    SMSHandler.deleteMessage(getApplicationContext(), strings[0]);
//
//                    long messageId = Helpers.generateRandomNumber();
//                    int subscriptionId = SIMHandler.getDefaultSimSubscription(getApplicationContext());
//
//                    String text = SecurityHelpers.FIRST_HEADER
//                            + strings[1]
//                            + SecurityHelpers.END_HEADER;
//
//                    SMSHandler.registerPendingMessage(getApplicationContext(),
//                            smsMetaEntity.getAddress(),
//                            text,
//                            messageId,
//                            subscriptionId);
//
//                    // TODO: rewrite rxKeys to a more standardized form
//                    rxKeys(Base64.decode(strings[1], Base64.DEFAULT), messageId, subscriptionId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        singleMessagesThreadRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                final int lastTopVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager())
                        .findLastVisibleItemPosition();

                final int firstVisibleItemPosition = ((LinearLayoutManager) recyclerView.getLayoutManager())
                        .findFirstVisibleItemPosition();

                final int maximumScrollPosition = singleMessagesThreadRecyclerAdapter.getItemCount() - 1;

                if (!singleMessageViewModel.offsetStartedFromZero && firstVisibleItemPosition == 0) {
                    int newSize = singleMessageViewModel.refreshDown(getApplicationContext());

                    if (newSize > 0)
                        recyclerView.scrollToPosition(lastTopVisiblePosition + 1 + newSize);
                }
                else if (singleMessageViewModel.offsetStartedFromZero &&
                        lastTopVisiblePosition >= maximumScrollPosition) {
                    singleMessageViewModel.refresh(getApplicationContext());
                    int itemCount = recyclerView.getAdapter().getItemCount();
                    if (itemCount > maximumScrollPosition + 1)
                        recyclerView.scrollToPosition(lastTopVisiblePosition);
                }
            }
        });

        try {
            singleMessagesThreadRecyclerAdapter.selectedItem.observe(this, new Observer<HashMap<String, RecyclerView.ViewHolder>>() {
                @Override
                public void onChanged(HashMap<String, RecyclerView.ViewHolder> selectedItems) {
                    _changeToolbarsItemSelected(selectedItems);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void _configureToolbars() {
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                if (R.id.copy == id) {
                    _copyItems();
                    return true;
                } else if (R.id.delete == id || R.id.delete_multiple == id) {
                    _deleteItems();
                    return true;
                } else if (R.id.make_call == id) {
                    smsMetaEntity.call(getApplicationContext());
                    return true;
                }
                return false;
            }
        });

        ab.setDisplayHomeAsUpEnabled(true);
        ab.setTitle(smsMetaEntity.getContactName(getApplicationContext()));
    }

    private void _configureMessagesTextBox() throws GeneralSecurityException, IOException {
        if (mutableLiveDataComposeMessage.getValue() == null ||
                mutableLiveDataComposeMessage.getValue().isEmpty())
            findViewById(R.id.sms_send_button).setVisibility(View.INVISIBLE);

        mutableLiveDataComposeMessage.observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                findViewById(R.id.sms_send_button).setVisibility(s.isEmpty() ? View.INVISIBLE : View.VISIBLE);
            }
        });
        findViewById(R.id.sms_send_button).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                _onLongClickSendButton(v);
                return true;
            }
        });

        multiSimcardConstraint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.getVisibility() == View.VISIBLE)
                    v.setVisibility(View.INVISIBLE);
            }
        });

        smsTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                view.getParent().requestDisallowInterceptTouchEvent(true);
                if ((motionEvent.getAction() & MotionEvent.ACTION_UP) != 0 &&
                        (motionEvent.getActionMasked() & MotionEvent.ACTION_UP) != 0) {
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            }
        });

        TextView encryptedMessageTextView = findViewById(R.id.send_sms_encrypted_version);
        encryptedMessageTextView.setMovementMethod(new ScrollingMovementMethod());

        final boolean hasSecretKey = smsMetaEntity.getEncryptionState(getApplicationContext())
                == SMS.SMSMetaEntity.ENCRYPTION_STATE.ENCRYPTED;
        final byte[] secretKey = smsMetaEntity.getSecretKey(getApplicationContext());

        findViewById(R.id.sms_send_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    sendTextMessage(v);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        smsTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mutableLiveDataComposeMessage.setValue(s.toString());

                try {
                    if (!s.toString().isEmpty() && hasSecretKey) {
                        String encryptedString = Base64.encodeToString(
                                SecurityECDH.encryptAES(s.toString().getBytes(StandardCharsets.UTF_8),
                                        secretKey),
                                Base64.DEFAULT);

                        encryptedString = SecurityHelpers.putEncryptedMessageWaterMark(encryptedString);
                        String stats = SMSHandler.calculateSMS(encryptedString);
                        String displayedString = encryptedString + "\n\n" + stats;

                        encryptedMessageTextView.setVisibility(View.VISIBLE);
                        encryptedMessageTextView.setText(displayedString);
                        if (encryptedMessageTextView.getLayout() != null)
                            encryptedMessageTextView.scrollTo(0,
                                    encryptedMessageTextView.getLayout().getLineTop(
                                            encryptedMessageTextView.getLineCount()) - encryptedMessageTextView.getHeight());
                    } else {
                        encryptedMessageTextView.setVisibility(View.GONE);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });

        // Message has been shared from another app to send by SMS
        if (getIntent().hasExtra(SMS.SMSMetaEntity.SHARED_SMS_BODY)) {
            smsTextView.setText(getIntent().getStringExtra(SMS.SMSMetaEntity.SHARED_SMS_BODY));

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mutableLiveDataComposeMessage
                            .setValue(getIntent().getStringExtra(SMS.SMSMetaEntity.SHARED_SMS_BODY));
                }
            });
        }
    }

    private void _configureLayoutForMessageType() {
        if(smsMetaEntity.isShortCode()) {
            // Cannot reply to message
            ConstraintLayout smsLayout = findViewById(R.id.send_message_content_layouts);
            smsLayout.setVisibility(View.GONE);
        }
    }

    private void _updateThreadToRead() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int updatedCount = SMSHandler.updateMarkThreadMessagesAsRead(getApplicationContext(),
                        smsMetaEntity.getThreadId());
            }
        }).start();
    }

    private void _changeToolbarsItemSelected(HashMap<String, RecyclerView.ViewHolder> selectedItems) {
        if (selectedItems != null) {
            if (selectedItems.isEmpty()) {
                showDefaultToolbar(toolbar.getMenu());
            } else {
                hideDefaultToolbar(toolbar.getMenu(), selectedItems.size());
            }
        }
    }

    private void _configureEncryptionListeners() {
        sharedPreferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }
    private void _checkEncryptionStatus() throws GeneralSecurityException, IOException {
        Log.d(getLocalClassName(), "Encryption status: " + smsMetaEntity.getEncryptionState(getApplicationContext()));
        if(smsMetaEntity.getEncryptionState(getApplicationContext()) ==
                SMS.SMSMetaEntity.ENCRYPTION_STATE.NOT_ENCRYPTED) {
            ab.setSubtitle(R.string.send_sms_activity_user_not_encrypted);

            int textColor = Color.WHITE;
            Integer bgColor = getResources().getColor(R.color.failed_red, getTheme());
            String conversationNotSecuredText = getString(R.string.send_sms_activity_user_not_secure);
            String actionText = getString(R.string.send_sms_activity_user_not_secure_yes);

            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        byte[] agreementKey = smsMetaEntity.generateAgreements(getApplicationContext());

                        // TODO: refactor the entire send sms thing to inform when dual-sim
                        // TODO: support for multi-sim
                        int subscriptionId = SIMHandler.getDefaultSimSubscription(getApplicationContext());
                        String threadId = SMSHandler.registerPendingKeyMessage(getApplicationContext(),
                                smsMetaEntity.getAddress(),
                                agreementKey,
                                subscriptionId);

                        if(smsMetaEntity.getThreadId() == null && threadId != null) {
                            getIntent().putExtra(SMS.SMSMetaEntity.THREAD_ID, threadId);
                            _setupActivityDependencies();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

            lunchSnackBar(conversationNotSecuredText, actionText, onClickListener, bgColor, textColor);
        }
        else if(smsMetaEntity.getEncryptionState(getApplicationContext()) ==
                SMS.SMSMetaEntity.ENCRYPTION_STATE.SENT_PENDING_AGREEMENT) {
            ab.setSubtitle(R.string.send_sms_activity_user_not_encrypted);

            int bgColor = getResources().getColor(R.color.purple_200, getTheme());
            String conversationNotSecuredText = getString(R.string.send_sms_activity_user_not_secure_pending);
            String actionText = getString(R.string.send_sms_activity_user_not_secure_pending_yes);

            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        byte[] agreementKey = smsMetaEntity.generateAgreements(getApplicationContext());

                        // TODO: refactor the entire send sms thing to inform when dual-sim
                        // TODO: support for multi-sim
                        int subscriptionId = SIMHandler.getDefaultSimSubscription(getApplicationContext());
                        SMSHandler.registerPendingKeyMessage(getApplicationContext(),
                                smsMetaEntity.getAddress(),
                                agreementKey,
                                subscriptionId);
                    } catch (GeneralSecurityException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };

            lunchSnackBar(conversationNotSecuredText, actionText, onClickListener, bgColor, Color.BLACK);
        }
        else if(smsMetaEntity.getEncryptionState(getApplicationContext()) ==
                SMS.SMSMetaEntity.ENCRYPTION_STATE.RECEIVED_PENDING_AGREEMENT) {
            String text = getString(R.string.send_sms_activity_user_not_secure_agree);
            String actionText = getString(R.string.send_sms_activity_user_not_secure_yes_agree);
            Integer bgColor = getResources().getColor(R.color.highlight_yellow, getTheme());
            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        smsMetaEntity.agreePeerRequest(getApplicationContext());
                    } catch (GeneralSecurityException | IOException e) {
                        e.printStackTrace();
                    }
                }
            };

            lunchSnackBar(text, actionText, onClickListener, bgColor, Color.BLACK);
        }
        else if(smsMetaEntity.getEncryptionState(getApplicationContext()) ==
                SMS.SMSMetaEntity.ENCRYPTION_STATE.RECEIVED_AGREEMENT_REQUEST) {
            String text = getString(R.string.send_sms_activity_user_not_secure_no_agreed);
            String actionText = getString(R.string.send_sms_activity_user_not_secure_yes_agree);
            int bgColor = getResources().getColor(R.color.purple_200, getTheme());

            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        byte[] agreementKey = smsMetaEntity.agreePeerRequest(getApplicationContext());

                        // TODO: refactor the entire send sms thing to inform when dual-sim
                        // TODO: support for multi-sim
                        int subscriptionId = SIMHandler.getDefaultSimSubscription(getApplicationContext());
                        String threadId = SMSHandler.registerPendingKeyMessage(getApplicationContext(),
                                smsMetaEntity.getAddress(),
                                agreementKey,
                                subscriptionId);

                        if(smsMetaEntity.getThreadId() == null && threadId != null) {
                            getIntent().putExtra(SMS.SMSMetaEntity.THREAD_ID, threadId);
                            _setupActivityDependencies();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            lunchSnackBar(text, actionText, onClickListener, bgColor, Color.BLACK);
        }
        else if(smsMetaEntity.getEncryptionState(getApplicationContext()) ==
                SMS.SMSMetaEntity.ENCRYPTION_STATE.ENCRYPTED) {
            ab.setSubtitle(getString(R.string.send_sms_activity_user_encrypted));
        }
    }

    public void sendTextMessage(View view) throws Exception {
        if(smsTextView.getText() != null) {
            String text = smsTextView.getText().toString();
            String threadId = _sendSMSMessage(defaultSubscriptionId, text);
            smsTextView.getText().clear();
            if(smsMetaEntity.getThreadId() == null && threadId != null) {
                getIntent().putExtra(SMS.SMSMetaEntity.THREAD_ID, threadId);
                _setupActivityDependencies();
            }

            // Remove messages from archive if pending send
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        removeFromArchive();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    private void removeFromArchive() throws InterruptedException {
        ArchiveHandler archiveHandler = new ArchiveHandler(getApplicationContext());
        archiveHandler.removeFromArchive(getApplicationContext(),
                Long.parseLong(smsMetaEntity.getThreadId()));
    }

    private String _sendSMSMessage(int subscriptionId, String text) {
        String threadId = new String();
        try {
            threadId = SMSHandler.registerPendingMessage(getApplicationContext(),
                    smsMetaEntity.getAddress(), text, subscriptionId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return threadId;
    }

    private void lunchSnackBar(String text, String actionText, View.OnClickListener onClickListener,
                               Integer bgColor, Integer textColor) {
        String insertDetails = smsMetaEntity.getContactName(getApplicationContext());
        insertDetails = insertDetails.replaceAll("\\+", "");
        String insertText = text.replaceAll("\\[insert name\\]", insertDetails);

        SpannableStringBuilder spannable = new SpannableStringBuilder(insertText);

        Pattern pattern = Pattern.compile(insertDetails); // Regex pattern to match "[phonenumber]"
        Matcher matcher = pattern.matcher(spannable);

        while (matcher.find()) {
            StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
            spannable.setSpan(boldSpan, matcher.start(), matcher.end(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }

        Snackbar snackbar = Snackbar.make(findViewById(R.id.coordinator),
                spannable, BaseTransientBottomBar.LENGTH_INDEFINITE);

        View snackbarView = snackbar.getView();

        snackbar.setTextColor(textColor);

        if (bgColor == null)
            bgColor = getResources().getColor(R.color.primary_warning_background_color,
                    getTheme());

        snackbar.setBackgroundTint(bgColor);
        snackbar.setTextMaxLines(10);
        snackbar.setActionTextColor(textColor);
        snackbar.setAction(actionText, onClickListener);
        snackbar.getView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snackbar.dismiss();
            }
        });

        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams)
                snackbarView.getLayoutParams();
        params.gravity = Gravity.TOP;
        snackbarView.setLayoutParams(params);

        snackbar.show();
    }

    public void uploadImage(View view) {
//        Intent galleryIntent = new Intent(
//                Intent.ACTION_PICK,
//                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
//
//        startActivityForResult(galleryIntent, RESULT_GALLERY);
    }


    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
//
//        if (requestCode == RESULT_GALLERY) {
//            if (null != data) {
//                Uri imageUri = data.getData();
//
//                Intent intent = new Intent(getApplicationContext(), ImageViewActivity.class);
//                intent.putExtra(IMAGE_URI, imageUri.toString());
//                intent.putExtra(ADDRESS, address);
//                intent.putExtra(THREAD_ID, threadId);
//                startActivity(intent);
//                finish();
//            }
//        }
    }

    public void _onLongClickSendButton(View view) {
        List<SubscriptionInfo> simcards = SIMHandler.getSimCardInformation(getApplicationContext());

        TextView simcard1 = findViewById(R.id.simcard_select_operator_1_name);
        TextView simcard2 = findViewById(R.id.simcard_select_operator_2_name);

        ImageButton simcard1Img = findViewById(R.id.simcard_select_operator_1);
        ImageButton simcard2Img = findViewById(R.id.simcard_select_operator_2);

        ArrayList<TextView> views = new ArrayList();
        views.add(simcard1);
        views.add(simcard2);

        ArrayList<ImageButton> buttons = new ArrayList();
        buttons.add(simcard1Img);
        buttons.add(simcard2Img);

        for (int i = 0; i < simcards.size(); ++i) {
            CharSequence carrierName = simcards.get(i).getCarrierName();
            views.get(i).setText(carrierName);
            buttons.get(i).setImageBitmap(simcards.get(i).createIconBitmap(getApplicationContext()));

            final int subscriptionId = simcards.get(i).getSubscriptionId();
            buttons.get(i).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    defaultSubscriptionId = subscriptionId;
                    findViewById(R.id.simcard_select_constraint).setVisibility(View.INVISIBLE);
                    String subscriptionText = getString(R.string.default_subscription_id_changed) +
                            carrierName;
                    Toast.makeText(getApplicationContext(), subscriptionText, Toast.LENGTH_SHORT).show();
                }
            });
        }

        multiSimcardConstraint.setVisibility(View.VISIBLE);
    }

    private void hideDefaultToolbar(Menu menu, int size) {
        menu.setGroupVisible(R.id.default_menu_items, false);
        if (size > 1) {
            menu.setGroupVisible(R.id.single_message_copy_menu, false);
            menu.setGroupVisible(R.id.multiple_message_copy_menu, true);
        } else {
            menu.setGroupVisible(R.id.multiple_message_copy_menu, false);
            menu.setGroupVisible(R.id.single_message_copy_menu, true);
        }

        ab.setHomeAsUpIndicator(R.drawable.baseline_cancel_24);
        ab.setTitle(String.valueOf(size));

        // TODO: fix this
//        if (ab.getSubtitle() != null && abSubtitle.isEmpty())
//            abSubtitle = ab.getSubtitle().toString();
        ab.setSubtitle("");

        // experimental
        ab.setElevation(10);
    }

    private void showDefaultToolbar(Menu menu) {
        menu.setGroupVisible(R.id.default_menu_items, true);
        menu.setGroupVisible(R.id.single_message_copy_menu, false);

        ab.setHomeAsUpIndicator(null);
        // TODO: fix this
//        ab.setSubtitle(abSubtitle);
//        abSubtitle = "";
        ab.setTitle(smsMetaEntity.getContactName(getApplicationContext()));
    }

    private void _copyItems() {
        if(singleMessagesThreadRecyclerAdapter.selectedItem.getValue() != null) {
            String[] keys = singleMessagesThreadRecyclerAdapter.selectedItem.getValue()
                    .keySet().toArray(new String[0]);
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            Cursor cursor = SMSHandler.fetchSMSInboxById(getApplicationContext(), keys[0]);
            if (cursor.moveToFirst()) {
                do {
                    SMS sms = new SMS(cursor);
                    ClipData clip = ClipData.newPlainText(keys[0], sms.getBody());

                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(getApplicationContext(), "Copied!", Toast.LENGTH_SHORT).show();

                } while (cursor.moveToNext());
            }
            cursor.close();
            singleMessagesThreadRecyclerAdapter.resetSelectedItem(keys[0], true);
        }
    }

    private void _deleteItems() {
        // TODO: fix this
//        if(singleMessagesThreadRecyclerAdapter.selectedItem.getValue() != null) {
//            final String[] keys = singleMessagesThreadRecyclerAdapter.selectedItem.getValue()
//                    .keySet().toArray(new String[0]);
//            if (keys.length > 1) {
//                SMSHandler.deleteMultipleMessages(getApplicationContext(), keys);
//                singleMessagesThreadRecyclerAdapter.resetAllSelectedItems();
//                singleMessagesThreadRecyclerAdapter.removeAllItems(keys);
//            } else {
//                SMSHandler.deleteMessage(getApplicationContext(), keys[0]);
//                singleMessagesThreadRecyclerAdapter.resetSelectedItem(keys[0], true);
//                singleMessagesThreadRecyclerAdapter.removeItem(keys[0]);
//            }
//        }
    }

    @Override
    public void onBackPressed() {
        if (findViewById(R.id.simcard_select_constraint).getVisibility() == View.VISIBLE)
            findViewById(R.id.simcard_select_constraint).setVisibility(View.INVISIBLE);
        if (singleMessagesThreadRecyclerAdapter.hasSelectedItems()) {
            singleMessagesThreadRecyclerAdapter.resetAllSelectedItems();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case SEND_SMS_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0) {
                    Toast.makeText(this, "Let's do this!!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Permission denied!", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

}