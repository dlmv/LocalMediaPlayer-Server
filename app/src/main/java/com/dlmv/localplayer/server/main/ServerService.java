package com.dlmv.localplayer.server.main;

import com.dlmv.localmediaplayer.server.R;
import com.dlmv.localplayer.server.utils.RootApplication;

import android.app.*;
import android.content.*;
import android.os.*;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;



public class ServerService extends Service {

	static boolean exists = false;

	public final static String PORT = "server_port";
	final static String STREAMING_PORT = "streaming_port";
	final static String IP = "server_ip";

	final static String ASK_STATE = "mp_ask_state";

	final static int STATE_STARTED = 0;
	final static int STATE_STARTING = 1;
	final static int STATE_FAILED = 2;
	final static int STATE_STOPPING = 3;
	final static int STATE_STOPPED = 4;

	private int myState;

	private int myPort;
	private int myStreamingPort;
	private String myError = "";

	final Handler myHandler = new Handler() {
		public void handleMessage (Message msg) {
			Intent i = new Intent();
			i.putExtra(PORT, Integer.toString(myPort));
			i.putExtra(STREAMING_PORT, Integer.toString(myStreamingPort));
			WebServer server = ((RootApplication)getApplication()).Server;
			if (server != null) {
				i.putExtra(IP, server.getIp());
			}
			switch (myState) {
				case STATE_STARTED:
					Toast.makeText(getApplicationContext(), "Server is running on port: " + Integer.toString(myPort), Toast.LENGTH_SHORT).show();
					i.setAction(MainActivity.STARTED);
					sendBroadcast(i);
					showNotification();
					break;
				case STATE_STARTING:
					i.setAction(MainActivity.STARTING);
					sendBroadcast(i);
					break;
				case STATE_FAILED:
					Toast.makeText(getApplicationContext(), myError, Toast.LENGTH_LONG).show();
					i.setAction(MainActivity.STOPPING);
					sendBroadcast(i);
					stopSelf();
					break;
				case STATE_STOPPING:
					i.setAction(MainActivity.STOPPING);
					sendBroadcast(i);
					break;
				case STATE_STOPPED:
					Toast.makeText(getApplicationContext(), "Server stopped", Toast.LENGTH_SHORT).show();
					i.setAction(MainActivity.STOPPED);
					sendBroadcast(i);
					ServerService.this.stopForeground(true);
					break;
			}
		}
	};

	@Override
	public void onCreate() {
		exists = true;
		super.onCreate();
		myState = STATE_STARTING;
		myHandler.sendEmptyMessage(0);

		myMessageReceiver = new MessageReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(ASK_STATE);
		registerReceiver(myMessageReceiver, filter);
	}

	@Override
	public int onStartCommand(final Intent intent, int flags, int startId) {
		final Thread starter = new Thread(new Runnable() {
			public void run () {
				try {
					String portStr = intent.getStringExtra(PORT);
					SharedPreferences settings = getSharedPreferences(MainActivity.PREFS_NAME, 0);
					if (portStr == null) {
						myPort = settings.getInt(MainActivity.PORT1, 8123);
					} else {
						myPort = Integer.parseInt(portStr);
					}
					final int port = myPort;
					WebServer server = WebServer.tryCreateWebServer(port, ServerService.this);
					myPort = server.Port;
					((RootApplication)getApplication()).Server = server;
					myStreamingPort = server.getStreamingPort();
					String pass = settings.getString(MainActivity.PASSWORD, "");
					String mpass = settings.getString(MainActivity.MASTER_PASSWORD, "");


					server.setPassword(pass);
					server.setMasterPassword(mpass);
					myState = STATE_STARTED;
					myHandler.sendEmptyMessage(0);
				} catch (Exception e) {
					myError = e.getMessage();
					myState = STATE_FAILED;
					myHandler.sendEmptyMessage(0);
				}
			}
		});
		starter.start();
		return START_REDELIVER_INTENT;
		//FIXME on 2.3 onstartcommand() not called after re-creation
	}

	@Override
	public void onDestroy() {
		myState = STATE_STOPPING;
		myHandler.sendEmptyMessage(0);
		final WebServer server = ((RootApplication)getApplication()).Server;
		if (server != null) {
			final Thread finisher = new Thread(new Runnable() {
				public void run () {
					server.stop();
					((RootApplication)getApplication()).Server = null;
					myState = STATE_STOPPED;
					myHandler.sendEmptyMessage(0);
					exists = false;
				}
			});
			finisher.start();
		} else {
			myState = STATE_STOPPED;
			myHandler.sendEmptyMessage(0);
			exists = false;
		}
		if (myMessageReceiver != null) {
			unregisterReceiver(myMessageReceiver);
		}
		super.onDestroy();
	}

	public IBinder onBind(Intent intent) {
		return null;
	}

	private void showNotification() {
		CharSequence text = "LocalPlayer server is running";

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, MainActivity.class), 0);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
				.setContentTitle(text)
				.setContentText(text)
				.setSmallIcon(R.drawable.notification)
				.setContentIntent(contentIntent)
				.setOngoing(true);



		Notification notification = builder.build();

		this.startForeground(1, notification);
	}

	private class MessageReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			try {
				Thread.sleep(200);//wait for activity to start waiting // maybe not needed?
			} catch (InterruptedException ignored) {
			}
			myHandler.sendEmptyMessage(0);
		}
	}

	private MessageReceiver myMessageReceiver;

}
