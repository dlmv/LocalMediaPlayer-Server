package com.dlmv.localplayer.server.utils;

import android.app.Application;

import com.dlmv.localplayer.server.main.WebServer;

public class RootApplication extends Application {
    private LoginDB Login;

    public WebServer Server;

    @Override
    public void onCreate() {
        super.onCreate();
        Login = new LoginDB(this.getApplicationContext());
    }

    public LoginDB LoginDB() {
        return Login;
    }

}
