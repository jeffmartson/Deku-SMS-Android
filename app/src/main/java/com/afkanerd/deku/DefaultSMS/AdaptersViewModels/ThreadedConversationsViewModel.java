package com.afkanerd.deku.DefaultSMS.AdaptersViewModels;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingLiveData;

import com.afkanerd.deku.DefaultSMS.Commons.Helpers;
import com.afkanerd.deku.DefaultSMS.DAO.ConversationDao;
import com.afkanerd.deku.DefaultSMS.Models.Archive;
import com.afkanerd.deku.DefaultSMS.Models.Contacts;
import com.afkanerd.deku.DefaultSMS.Models.Conversations.Conversation;
import com.afkanerd.deku.DefaultSMS.Models.Conversations.ThreadedConversations;
import com.afkanerd.deku.DefaultSMS.DAO.ThreadedConversationsDao;
import com.afkanerd.deku.DefaultSMS.Models.NativeSMSDB;
import com.afkanerd.deku.E2EE.ConversationsThreadsEncryption;
import com.afkanerd.deku.E2EE.ConversationsThreadsEncryptionDao;
import com.afkanerd.deku.E2EE.E2EEHandler;

import java.util.ArrayList;
import java.util.List;

public class ThreadedConversationsViewModel extends ViewModel {
    ThreadedConversationsDao threadedConversationsDao;
    int pageSize = 20;
    int prefetchDistance = 3 * pageSize;
    boolean enablePlaceholder = false;
    int initialLoadSize = 2 * pageSize;
    int maxSize = PagingConfig.MAX_SIZE_UNBOUNDED;

    public LiveData<PagingData<ThreadedConversations>> get(){

        Pager<Integer, ThreadedConversations> pager = new Pager<>(new PagingConfig(
                pageSize,
                prefetchDistance,
                enablePlaceholder,
                initialLoadSize,
                maxSize
        ), ()-> this.threadedConversationsDao.getAllWithoutArchived());
        return PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), this);
    }

    public LiveData<PagingData<ThreadedConversations>> getEncrypted(Context context) throws InterruptedException {
        List<String> address = new ArrayList<>();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                ConversationsThreadsEncryptionDao conversationsThreadsEncryptionDao =
                        ConversationsThreadsEncryption.getDao(context);
                List<ConversationsThreadsEncryption> conversationsThreadsEncryptionList =
                        conversationsThreadsEncryptionDao.getAll();

                for(ConversationsThreadsEncryption conversationsThreadsEncryption :
                        conversationsThreadsEncryptionList) {
                    String derivedAddress =
                            E2EEHandler.getAddressFromKeystore(
                                    conversationsThreadsEncryption.getKeystoreAlias());
                    address.add(derivedAddress);
                }
            }
        });
        thread.start();
        thread.join();

        Pager<Integer, ThreadedConversations> pager = new Pager<>(new PagingConfig(
                pageSize,
                prefetchDistance,
                enablePlaceholder,
                initialLoadSize
        ), ()-> this.threadedConversationsDao.getByAddress(address));
        return PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), this);
    }

    public LiveData<PagingData<ThreadedConversations>> getNotEncrypted(Context context) throws InterruptedException {
        List<String> address = new ArrayList<>();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                ConversationsThreadsEncryptionDao conversationsThreadsEncryptionDao =
                        ConversationsThreadsEncryption.getDao(context);
                List<ConversationsThreadsEncryption> conversationsThreadsEncryptionList =
                        conversationsThreadsEncryptionDao.getAll();

                for(ConversationsThreadsEncryption conversationsThreadsEncryption :
                        conversationsThreadsEncryptionList) {
                    String derivedAddress =
                            E2EEHandler.getAddressFromKeystore(
                                    conversationsThreadsEncryption.getKeystoreAlias());
                    address.add(derivedAddress);
                }
            }
        });
        thread.start();
        thread.join();
        Pager<Integer, ThreadedConversations> pager = new Pager<>(new PagingConfig(
                pageSize,
                prefetchDistance,
                enablePlaceholder,
                initialLoadSize
        ), ()-> this.threadedConversationsDao.getNotInAddress(address));
        return PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), this);
    }


    public void insert(ThreadedConversations threadedConversations) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                threadedConversationsDao.insert(threadedConversations);
            }
        }).start();
    }

    public void filterInsert(Context context, List<ThreadedConversations> threadedConversations,
                                              List<ThreadedConversations> completeList) {
        List<ThreadedConversations> insertList = new ArrayList<>();
        for(ThreadedConversations threadedConversation : threadedConversations) {
            String contactName = Contacts.retrieveContactName(context,
                    threadedConversation.getAddress());
            threadedConversation.setContact_name(contactName);
            if(!completeList.contains(threadedConversation)) {
                insertList.add(threadedConversation);
            } else {
                ThreadedConversations oldThread =
                        completeList.get(completeList.indexOf(threadedConversation));
                if(oldThread.diffReplace(threadedConversation))
                    insertList.add(oldThread);
            }
        }

        List<ThreadedConversations> deleteList = new ArrayList<>();
        if(threadedConversations.isEmpty()) {
            deleteList = completeList;
        } else {
            for (ThreadedConversations threadedConversation : completeList) {
                if (!threadedConversations.contains(threadedConversation)) {
                    deleteList.add(threadedConversation);
                }
            }
            threadedConversationsDao.insertAll(insertList);
        }
        threadedConversationsDao.delete(deleteList);
    }

    public void loadNatives(Context context) {
        Thread loadNativeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Cursor cursor = NativeSMSDB.fetchAll(context);

                List<ThreadedConversations> threadedConversations =
                        ThreadedConversations.buildRaw(cursor);
                List<ThreadedConversations> completeList = threadedConversationsDao.getAll();
                filterInsert(context, threadedConversations, completeList);
            }
        });
        loadNativeThread.setName("load_native_thread");
        loadNativeThread.start();
    }

    public void setThreadedConversationsDao(ThreadedConversationsDao threadedConversationsDao) {
        this.threadedConversationsDao = threadedConversationsDao;
    }

    public void archive(List<Archive> archiveList) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                threadedConversationsDao.archive(archiveList);
            }
        }).start();
    }


    public void delete(Context context, List<ThreadedConversations> threadedConversations) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String[] ids = new String[threadedConversations.size()];
                ConversationDao conversationDao = Conversation.getDao(context);
                for(int i=0; i<threadedConversations.size(); ++i) {
                    ids[i] = threadedConversations.get(i).getThread_id();
                    conversationDao.delete(threadedConversations.get(i).getThread_id());
                }
                threadedConversationsDao.delete(threadedConversations);
                NativeSMSDB.deleteThreads(context, ids);
            }
        }).start();
    }

    public void refresh(Context context, final ConversationDao conversationDaoInstance) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ConversationDao conversationDao = conversationDaoInstance == null ?
                        Conversation.getDao(context) : conversationDaoInstance;
                List<Conversation> conversations = conversationDao.getForThreading();
                List<ThreadedConversations> threadedConversationsList =
                        ThreadedConversations.buildRaw(context, conversations);
//                List<ThreadedConversations> all = threadedConversationsDao.getAll();
//                filterInsert(context, threadedConversationsList, all);
                threadedConversationsDao.insertAll(threadedConversationsList);
            }
        }).start();
    }
}
