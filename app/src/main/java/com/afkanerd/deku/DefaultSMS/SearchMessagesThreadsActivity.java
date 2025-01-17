package com.afkanerd.deku.DefaultSMS;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.afkanerd.deku.DefaultSMS.Commons.Helpers;
import com.afkanerd.deku.DefaultSMS.Models.Conversations.Conversation;
import com.afkanerd.deku.DefaultSMS.AdaptersViewModels.SearchConversationRecyclerAdapter;
import com.afkanerd.deku.DefaultSMS.Models.Conversations.ThreadedConversations;
import com.afkanerd.deku.DefaultSMS.DAO.ThreadedConversationsDao;
import com.afkanerd.deku.DefaultSMS.Models.Contacts;
import com.afkanerd.deku.DefaultSMS.AdaptersViewModels.SearchViewModel;

import java.util.List;

public class SearchMessagesThreadsActivity extends AppCompatActivity {

    SearchViewModel searchViewModel;
    MutableLiveData<String> searchString = new MutableLiveData<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_messages_threads);
        searchViewModel = new ViewModelProvider(this).get(
                SearchViewModel.class);


        Toolbar myToolbar = (Toolbar) findViewById(R.id.search_messages_toolbar);
        setSupportActionBar(myToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        SearchConversationRecyclerAdapter searchConversationRecyclerAdapter =
                new SearchConversationRecyclerAdapter(getApplicationContext());

        SearchView searchView = findViewById(R.id.search_view_input);
        CustomContactsCursorAdapter customContactsCursorAdapter = new CustomContactsCursorAdapter(getApplicationContext(),
                Contacts.filterContacts(getApplicationContext(), ""), 0);
        onSearchRequested();
        ListView suggestionsListView = findViewById(R.id.search_messages_suggestions_list);
        suggestionsListView.setAdapter(customContactsCursorAdapter);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchString.setValue(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if(newText.length() != 1) {
                    customContactsCursorAdapter.changeCursor(
                            Contacts.filterContacts(getApplicationContext(), newText));
                    return true;
                }
                return false;
            }
        });

        RecyclerView messagesThreadRecyclerView = findViewById(R.id.search_results_recycler_view);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        messagesThreadRecyclerView.setLayoutManager(linearLayoutManager);

        messagesThreadRecyclerView.setAdapter(searchConversationRecyclerAdapter);

        searchString.observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                try {
                    if(s.isEmpty())
                        searchConversationRecyclerAdapter.searchString = null;
                    else
                        searchConversationRecyclerAdapter.searchString = s;
                    searchViewModel.search(getApplicationContext(), s);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        ThreadedConversationsDao threadedConversationsDao =
                ThreadedConversations.getDao(getApplicationContext());

        if(getIntent().hasExtra(Conversation.THREAD_ID)) {
            searchViewModel.getByThreadId(threadedConversationsDao,
                            getIntent().getStringExtra(Conversation.THREAD_ID)).observe(this,
                    new Observer<List<ThreadedConversations>>() {
                        @Override
                        public void onChanged(List<ThreadedConversations> smsList) {
                            if(!searchString.getValue().isEmpty() && smsList.isEmpty())
                                findViewById(R.id.search_nothing_found).setVisibility(View.VISIBLE);
                            else {
                                findViewById(R.id.search_nothing_found).setVisibility(View.GONE);
                            }
                            Log.d(getLocalClassName(), "Found for address: " + smsList.size());
                            searchConversationRecyclerAdapter.mDiffer.submitList(smsList);
                        }
                    });
        }
        else {
            searchViewModel.get(threadedConversationsDao).observe(this,
                    new Observer<List<ThreadedConversations>>() {
                        @Override
                        public void onChanged(List<ThreadedConversations> smsList) {
                            if (!searchString.getValue().isEmpty() && smsList.isEmpty())
                                findViewById(R.id.search_nothing_found).setVisibility(View.VISIBLE);
                            else {
                                findViewById(R.id.search_nothing_found).setVisibility(View.GONE);
                            }
                            Log.d(getLocalClassName(), "Found: " + smsList.size());
                            searchConversationRecyclerAdapter.mDiffer.submitList(smsList);
                        }
                    });
        }
    }


    public static class CustomContactsCursorAdapter extends CursorAdapter {

        public CustomContactsCursorAdapter(Context context, Cursor c, int flags) {
            super(context, c, flags);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
//            return LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false);
            return LayoutInflater.from(context).inflate(R.layout.conversations_threads_layout, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
            String address = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));

            TextView textView = view.findViewById(R.id.messages_thread_address_text);
            textView.setTextSize(14);
            textView.setText(name);

            TextView textView2 = view.findViewById(R.id.messages_thread_text);
            textView2.setTextSize(12);
            textView2.setText(address);

            final int color = Helpers.generateColor(address);
            Drawable drawable = context.getDrawable(R.drawable.baseline_account_circle_24);
            drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);

//            ImageView imageView = view.findViewById(R.id.messages_threads_contact_photo);
//            imageView.setAdjustViewBounds(true);
//            imageView.setMaxHeight(150);
//            imageView.setMaxWidth(150);

//            ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
//            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
//            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
//
//            imageView.setLayoutParams(layoutParams);
//            imageView.setImageDrawable(drawable);

            view.findViewById(R.id.messages_threads_layout).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(context, ConversationActivity.class);
                    intent.putExtra(Conversation.ADDRESS, address);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            });
        }
    }
}