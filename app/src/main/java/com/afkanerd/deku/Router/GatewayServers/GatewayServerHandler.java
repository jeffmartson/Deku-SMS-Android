package com.afkanerd.deku.Router.GatewayServers;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;

import com.afkanerd.deku.DefaultSMS.Models.Database.Datastore;
import com.afkanerd.deku.DefaultSMS.Models.Database.Migrations;

import java.util.ArrayList;
import java.util.List;

public class GatewayServerHandler {
    Datastore databaseConnector;

    public GatewayServerHandler(Context context){
         databaseConnector = Room.databaseBuilder(context, Datastore.class,
                         Datastore.databaseName)
                 .addMigrations(new Migrations.Migration4To5())
                .addMigrations(new Migrations.Migration5To6())
                 .addMigrations(new Migrations.Migration6To7())
                .build();
    }

    public LiveData<List<GatewayServer>> getAllLiveData() throws InterruptedException {
        final LiveData<List<GatewayServer>>[] liveData = new LiveData[]{new MutableLiveData<>()};
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                GatewayServerDAO gatewayServerDAO = databaseConnector.gatewayServerDAO();
                liveData[0] = gatewayServerDAO.getAll();
            }
        });
        thread.start();
        thread.join();

        return liveData[0];
    }
    public List<GatewayServer> getAll() throws InterruptedException {
        final List<GatewayServer>[] gatewayServerList = new List[]{new ArrayList<>()};
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                GatewayServerDAO gatewayServerDAO = databaseConnector.gatewayServerDAO();
                gatewayServerList[0] = gatewayServerDAO.getAllList();
            }
        });
        thread.start();
        thread.join();

        return gatewayServerList[0];
    }

    public GatewayServer get(long id) throws InterruptedException {
        final GatewayServer[] gatewayServer = {new GatewayServer()};
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                GatewayServerDAO gatewayServerDAO = databaseConnector.gatewayServerDAO();
                gatewayServer[0] = gatewayServerDAO.get(id);
            }
        });
        thread.start();
        thread.join();

        return gatewayServer[0];
    }

    public void delete(long id) throws InterruptedException {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                GatewayServerDAO gatewayServerDAO = databaseConnector.gatewayServerDAO();
                GatewayServer gatewayServer = new GatewayServer();
                gatewayServer.setId(id);
                gatewayServerDAO.delete(gatewayServer);
            }
        });
        thread.start();
        thread.join();
    }

    public void add(GatewayServer gatewayServer) throws InterruptedException {
        gatewayServer.setDate(System.currentTimeMillis());
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                GatewayServerDAO gatewayServerDAO = databaseConnector.gatewayServerDAO();
                gatewayServerDAO.insert(gatewayServer);
            }
        });
        thread.start();
        thread.join();
    }


    public void update(GatewayServer gatewayServer) throws InterruptedException {
        gatewayServer.setDate(System.currentTimeMillis());

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                GatewayServerDAO gatewayServerDAO = databaseConnector.gatewayServerDAO();
                gatewayServerDAO.update(gatewayServer);
            }
        });
        thread.start();
        thread.join();
    }

    public void close() {
        databaseConnector.close();
    }

//    public static List<GatewayServer> fetchAll(Context context) throws InterruptedException {
//        Datastore databaseConnector = Room.databaseBuilder(context, Datastore.class,
//                Datastore.databaseName).build();
//
//        final List<GatewayServer>[] encryptedContentList = new List[]{new ArrayList<>()};
//
//        Thread fetchEncryptedMessagesThread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                GatewayServerDAO gatewayServerDAO = databaseConnector.gatewayServerDAO();
//                encryptedContentList[0] = gatewayServerDAO.getAll();
//            }
//        });
//
//        fetchEncryptedMessagesThread.start();
//        fetchEncryptedMessagesThread.join();
//
//        return encryptedContentList[0];
//    }
}
