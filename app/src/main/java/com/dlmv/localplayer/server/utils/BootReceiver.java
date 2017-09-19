package com.dlmv.localplayer.server.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.dlmv.localplayer.server.main.MainActivity;
import com.dlmv.localplayer.server.main.ServerService;

public class BootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences settings = context.getSharedPreferences(MainActivity.PREFS_NAME, 0);
		boolean start = settings.getBoolean(MainActivity.START, true);
		if (!start) {
			return;
		}
		int port1 = settings.getInt(MainActivity.PORT1, 8123);
		Intent startIntent = new Intent(context, ServerService.class);
		startIntent.putExtra(ServerService.PORT, Integer.toString(port1));
		context.startService(startIntent);
	}

}
