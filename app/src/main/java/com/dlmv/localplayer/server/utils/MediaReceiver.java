package com.dlmv.localplayer.server.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import com.dlmv.localplayer.server.main.WebServer;

public class MediaReceiver extends BroadcastReceiver {
	
	@Override
	public void onReceive(Context context, Intent intent) {
		final WebServer server = ((RootApplication)context.getApplicationContext()).Server;
		if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction()) && server != null) {
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE == event.getKeyCode() && KeyEvent.ACTION_DOWN == event.getAction()) {
            	new Thread() {
					@Override
					public void run() {
						server.getPlayerController().playOrPause();
					}
            	}.start();
            }
            if (KeyEvent.KEYCODE_MEDIA_STOP == event.getKeyCode() && KeyEvent.ACTION_DOWN == event.getAction()) {
            	new Thread() {
					@Override
					public void run() {
						server.getPlayerController().stop();
					}
            	}.start();
            }
            if (KeyEvent.KEYCODE_MEDIA_NEXT == event.getKeyCode() && KeyEvent.ACTION_DOWN == event.getAction()) {
            	new Thread() {
					@Override
					public void run() {
						server.getPlayerController().next();
					}
            	}.start();
            }
            if (KeyEvent.KEYCODE_MEDIA_PREVIOUS == event.getKeyCode() && KeyEvent.ACTION_DOWN == event.getAction()) {
            	new Thread() {
					@Override
					public void run() {
						server.getPlayerController().prev();
					}
            	}.start();
            }
        }

	}

}
