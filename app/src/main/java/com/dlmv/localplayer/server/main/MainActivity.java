package com.dlmv.localplayer.server.main;

import com.dlmv.localmediaplayer.server.R;
import com.dlmv.localplayer.server.utils.RootApplication;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class MainActivity extends Activity {

	private Button myStartButton;
	private Button myStopButton;
	private EditText myPortEdit;
	private EditText myStreamingPortEdit;
	private EditText myIpEdit;

	private String myPort;
	private String myStreamingPort;
	private String myIp = "Not connected";

	public static final String PREFS_NAME = "TestMpPrefs";
	public static final String PASSWD = "password";
	public static final String PORT1 = "port1";
	public static final String START = "start";
	public static final String START_LAUNCH = "start_merle";


	private ProgressDialog myProgress;
	private Handler myHandler = new Handler() {
		public synchronized void handleMessage(Message message) {
			SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
			if (message.what == 1) {
				int port1 = settings.getInt(PORT1, 8123);
				myPort = Integer.toString(port1);

				changeState(STOPPED);
			}
			
			myProgress.dismiss();
			if (myNeedToStart && myStartButton.isEnabled()) {
				myNeedToStart = false;
				myStartButton.performClick();
			}
		}
	};

	final static String STARTED = "mpservice_start";
	final static String STOPPED = "mpservice_stop";
	final static String STARTING = "mpservice_starting";
	final static String STOPPING = "mpservice_stopping";

	private boolean isMyServiceRunning() {
		if (ServerService.exists) {
			return true;
		}
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if ("com.dlmv.localplayer.server.main.ServerService".equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	private class DataUpdateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			myPort = intent.getStringExtra(ServerService.PORT);
			myStreamingPort = intent.getStringExtra(ServerService.STREAMING_PORT);
			String ip = intent.getStringExtra(ServerService.IP);
			if (ip != null) {
				myIp = ip;
			} else {
				myIp = "Not connected";
			}
			changeState(intent.getAction());
		}
	}

	private synchronized void changeState(String state) {
		if (state.equals(STARTED)) {
			myStartButton.setEnabled(false);
			myStopButton.setEnabled(true);
			myPortEdit.setText(myPort);
			myStreamingPortEdit.setText(myStreamingPort);
			myIpEdit.setText(myIp);
			myPortEdit.setFocusable(false);
			myStreamingPortEdit.setFocusable(false);
			synchronized(MainActivity.this) {
				MainActivity.this.notify();
			}
		}
		if (state.equals(STOPPED)) {
			myStartButton.setEnabled(true);
			myStopButton.setEnabled(false);
			myPortEdit.setText(myPort);
			myStreamingPortEdit.setText(myStreamingPort);
			myIpEdit.setText(myIp);
			myPortEdit.setFocusableInTouchMode(true);
			myStreamingPortEdit.setFocusableInTouchMode(true);
			synchronized(MainActivity.this) {
				MainActivity.this.notify();
			}
		}
		if (state.equals(STARTING)) {
			myStartButton.setEnabled(false);
			myStopButton.setEnabled(false);
			myPortEdit.setFocusable(false);
			myStreamingPortEdit.setFocusable(false);
		}
		if (state.equals(STOPPING)) {
			myStartButton.setEnabled(false);
			myStopButton.setEnabled(false);
			myPortEdit.setText(myPort);
			myStreamingPortEdit.setText(myStreamingPort);
			myIpEdit.setText(myIp);
			myPortEdit.setFocusable(false);
			myStreamingPortEdit.setFocusable(false);
		}
	}

	private void waitForResponse(String title, String message) {
		myProgress = ProgressDialog.show(this, title, message, true, false);
		final Thread checker = new Thread(new Runnable() {
			public void run() {
				try {
					while (true) {
						if (!isMyServiceRunning()) {
							myHandler.sendEmptyMessage(1);
							break;
						}
						Thread.sleep(1000);
					}
				} catch (InterruptedException ignored) {
				}
			}
		});
		final Thread waiter = new Thread(new Runnable() {
			public void run() {
				synchronized(MainActivity.this) {
					try {
						MainActivity.this.wait();
					} catch (InterruptedException ignored) {
					}
				}
				checker.interrupt();
				myHandler.sendEmptyMessage(0);
			}
		});
		checker.start();
		waiter.start();
	}

	private DataUpdateReceiver myDataUpdateReceiver;
	private boolean myNeedToStart;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.mainwindow);
		final View buttonView = findViewById(R.id.start_stop_buttons);

		myStartButton = buttonView.findViewById(R.id.ok_button);
		myStartButton.setText("Start");
		myStartButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
				String pass = settings.getString(PASSWD, "");
				int port1 = Integer.parseInt(myPortEdit.getText().toString());
				if (port1 < 8000) {
					Toast.makeText(MainActivity.this, "Better to use ports greater than 8000", Toast.LENGTH_SHORT).show();
					return;
				}
				if (port1 > 65535) {
					Toast.makeText(MainActivity.this, "Port number is too big", Toast.LENGTH_SHORT).show();
					return;
				}
				SharedPreferences.Editor editor = settings.edit();
				editor.putInt(PORT1, port1);
				editor.apply();
				changeState(STARTING);
				Intent startIntent = new Intent(MainActivity.this, ServerService.class);
				startIntent.putExtra(ServerService.PORT, myPortEdit.getText().toString());
				startIntent.putExtra(ServerService.STREAMING_PORT, myStreamingPortEdit.getText().toString());
				startIntent.putExtra(ServerService.PASS, pass);
				startService(startIntent);
				waitForResponse("Please, wait...", "Starting");
			}
		});

		myStopButton = buttonView.findViewById(R.id.cancel_button);
		myStopButton.setText("Stop");
		myStopButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				Intent startIntent = new Intent(MainActivity.this, ServerService.class);
				stopService(startIntent);
				waitForResponse("Please, wait...", "Stopping");
			}
		});

		Button passButton = findViewById(R.id.pass);
		passButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				View dialogView = View.inflate(MainActivity.this, R.layout.enter_password, null);
				final EditText input = dialogView.findViewById(R.id.name);
				final AlertDialog.Builder d = new AlertDialog.Builder(MainActivity.this)
				.setMessage("Password")
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog1, int which) {
						SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
						SharedPreferences.Editor editor = settings.edit();
						editor.putString(PASSWD, input.getText().toString());
						WebServer server = ((RootApplication)getApplication()).Server;
						if (server != null) {
							server.setPass(input.getText().toString());
						}
						editor.apply();
						dialog1.dismiss();
					}
				});
				d.setView(dialogView);
				d.show();
			}
		});

		myPortEdit = findViewById(R.id.port);
		myStreamingPortEdit = findViewById(R.id.stream_port);
		myIpEdit = findViewById(R.id.ip);

		myIpEdit.setFocusable(false);

		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		boolean startOnBoot = settings.getBoolean(START, true);
		CheckBox startBox = findViewById(R.id.start_on_boot);
		startBox.setChecked(startOnBoot);
		startBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
				SharedPreferences.Editor editor = settings.edit();
				editor.putBoolean(START, isChecked);
				editor.apply();
			}
		});
		boolean startOnLaunch = settings.getBoolean(START_LAUNCH, true);
		myNeedToStart = startOnLaunch;
		CheckBox startLaunchBox = findViewById(R.id.start_on_launch);
		startLaunchBox.setChecked(startOnLaunch);
		startLaunchBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
				SharedPreferences.Editor editor = settings.edit();
				editor.putBoolean(START_LAUNCH, isChecked);
				editor.apply();
			}
		});
		myDataUpdateReceiver = new DataUpdateReceiver();
		IntentFilter filter = new IntentFilter( );
		filter.addAction(STARTED);
		filter.addAction(STOPPED);
		filter.addAction(STARTING);
		filter.addAction(STOPPING);
		registerReceiver(myDataUpdateReceiver, filter);
		sendBroadcast(new Intent(ServerService.ASK_STATE));
		waitForResponse("Please, wait...", "Looking for running service");
	}

	@Override
	public void onDestroy() {
		if (myDataUpdateReceiver != null) {
			unregisterReceiver(myDataUpdateReceiver);
		}
		super.onDestroy();

	}
}
