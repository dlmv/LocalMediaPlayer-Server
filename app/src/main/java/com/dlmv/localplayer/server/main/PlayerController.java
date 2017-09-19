package com.dlmv.localplayer.server.main;

import java.io.UnsupportedEncodingException;
import java.util.*;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.PowerManager;
import android.util.Log;

import com.dlmv.localplayer.*;
import com.dlmv.localplayer.PlayerStatus.PlaylistItem;
import com.dlmv.localplayer.server.files.AbstractFile;
import com.dlmv.localplayer.server.files.AbstractFile.FileAuthException;
import com.dlmv.localplayer.server.utils.FileUtils;
import com.dlmv.localplayer.server.utils.NaturalOrderComparator;
import com.dlmv.localplayer.server.utils.RootApplication;
import com.test.smbstreamer.Streamer;

public class PlayerController implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnBufferingUpdateListener {
	private MediaPlayer myMP;
	private MediaPlayer myNextMP;
	private MediaPlayer myBackMP;
	private boolean myNextMPisPrepared;
	private String myNextMPUri;
	private int myNextNum = 0;

	private final WifiLock myWifiLock;


	//	private HashSet<String> myCorruptedUris = new HashSet<String>();

	private PlayerStatus myStatus = new PlayerStatus();

	private int getNextNum(int current) {
		if (myStatus.myType.equals(PlayerStatus.PlaylistType.CYCLIC) && (current + 1) >= myStatus.myPlaylist.size()) {
			return 0;
		}
		return current + 1;
	}

	private AudioManager myAudioManager;
	AudioManager getAudioManager() {
		return myAudioManager;
	}

	private Context myContext;

	PlayerController(Context c) {
		myContext = c;
		myAudioManager = (AudioManager)myContext.getSystemService(Context.AUDIO_SERVICE);
		myWifiLock = ((WifiManager) myContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, "lmplock");
	}
	
	private void doAfter() {
		WebServer server = ((RootApplication)myContext.getApplicationContext()).Server;
		if (server != null) {
			server.notifyClients();
		}
		checkWifiLock();
	}

	private void checkWifiLock() {
		synchronized(myWifiLock) {
			if (myWifiLock.isHeld()) {
				if (myMP == null && myBackMP == null) {
					myWifiLock.release();
				}
			} else {
				if (myMP != null || myBackMP != null) {
					myWifiLock.acquire();
				}
			}
		}
	}

	void setVolume(int vol) {
		if (vol >=0 && vol <= myAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) {
			myAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
		}
		doAfter();
	}

	private void setMpVolumeInternal(int vol, MediaPlayer mp) {
		float volume = vol == myStatus.myMpMaxVolume ? 1 :(float) (1 - (Math.log(myStatus.myMpMaxVolume - vol) / Math.log(myStatus.myMpMaxVolume)));
		if (mp != null) {
			mp.setVolume(volume, volume);
		}
	}

	void setMpVolume(int vol) {
		if (vol >=0 && vol <= myStatus.myMpMaxVolume) {
			myStatus.myMpVolume = vol;
			setMpVolumeInternal(myStatus.myMpVolume, myMP);
			setMpVolumeInternal(myStatus.myMpVolume, myNextMP);
		}
		doAfter();
	}

	void setBackVolume(int vol) {
		if (vol >=0 && vol <= myStatus.myBackMpMaxVolume) {
			myStatus.myBackMpVolume = vol;
			setMpVolumeInternal(myStatus.myBackMpVolume, myBackMP);
		}
		doAfter();
	}
	
	private void stopBackgroundInternal() {
		if (myBackMP != null) {
			myBackMP.stop();
			myBackMP.reset();
			myBackMP.release();
			myBackMP = null;
		}
		myStatus.myBackState = PlayerStatus.State.STOPPED;
		myStatus.myBackItem = null;
	}

	void resumeBackground() {
		if (myStatus.myBackState.equals(PlayerStatus.State.PAUSED) && myBackMP != null) {
			myBackMP.start();
			myStatus.myBackState = PlayerStatus.State.PLAYING;
		}
		doAfter();
	}

	void pauseBackground() {
		if (myStatus.myBackState.equals(PlayerStatus.State.PLAYING) && myBackMP != null) {
			myBackMP.pause();
			myStatus.myBackState = PlayerStatus.State.PAUSED;
		}
		doAfter();
	}

	void stopBackground() {
		stopBackgroundInternal();
		doAfter();
	}

	void playBackground(String uri) {
		stopBackgroundInternal();
		try {
			AbstractFile f = AbstractFile.create(uri, myContext);
			f.test();
			myBackMP = new MediaPlayer();
			myStatus.myBackState = PlayerStatus.State.WAITING;
			myBackMP.setWakeMode(myContext, PowerManager.PARTIAL_WAKE_LOCK);
			f.setAsSource(myBackMP, myContext);
			myBackMP.setOnCompletionListener(this);
			myBackMP.setOnErrorListener(this);
			myBackMP.prepare();
			myBackMP.setLooping(true);
			setMpVolumeInternal(myStatus.myBackMpVolume, myBackMP);
			myBackMP.start();
            myStatus.myBackItem = new PlaylistItem(uri);
			myStatus.myBackState = PlayerStatus.State.PLAYING;
		} catch (FileAuthException e) {
			stopBackground();
			setErrorMessage("loginNeeded: " + e.getMessage());
			doAfter();
		} catch (Exception e) {
			e.printStackTrace();
			stopBackground();
			setErrorMessage("Corrupted Background File");
			doAfter();
		}
		doAfter();
	}

	void volumeUp() {
		int vol = myAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		int maxvol = myAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		if (vol < maxvol) {
			myAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol + 1, 0);
		}
		doAfter();
	}

	void volumeDown() {
		int vol = myAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		if (vol > 0) {
			myAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol - 1, 0);
		}
		doAfter();
	}

	synchronized void play() {
		if (myStatus.myState.equals(PlayerStatus.State.STOPPED)) {
			startPlaying(0);
		} else if (myStatus.myState.equals(PlayerStatus.State.PAUSED)) {
			resumePlaying();
		}
		doAfter();
	}

	public synchronized void playOrPause() {
		if (myStatus.myState.equals(PlayerStatus.State.STOPPED)) {
			startPlaying(0);
		} else if (myStatus.myState.equals(PlayerStatus.State.PLAYING)) {
			pausePlaying();
		} else if (myStatus.myState.equals(PlayerStatus.State.PAUSED)) {
			resumePlaying();
		}
		doAfter();
	}

	public synchronized void next() {
		if (getNextNum(myStatus.myCurrentTrackNo) < myStatus.myPlaylist.size()) {
			startPlaying(getNextNum(myStatus.myCurrentTrackNo));
		}
	}

	public synchronized void prev() {
		if (myStatus.myCurrentTrackNo - 1 > 0) {
			startPlaying(myStatus.myCurrentTrackNo - 1);
		}
	}

	synchronized void play(int num) {
		if (myStatus.myState.equals(PlayerStatus.State.STOPPED) || myStatus.myState.equals(PlayerStatus.State.PLAYING) || myStatus.myState.equals(PlayerStatus.State.PAUSED)) {
			startPlaying(num);
		}
		doAfter();
	}

	synchronized void pause() {
		pausePlaying();
		doAfter();
	}

	public synchronized void stop() {
		stopPlaying();
		doAfter();
	}

	synchronized void clearPlaylist() {
		stopPlaying();
		myStatus.myPlaylist.clear();
		doAfter();
	}

	private synchronized void addToPlaylist(String uri) {
		myStatus.myPlaylist.add(new PlaylistItem(uri));
		if (myStatus.myPlaylist.size() == myStatus.myCurrentTrackNo + 2 && (myStatus.myState.equals(PlayerStatus.State.PAUSED) || myStatus.myState.equals(PlayerStatus.State.PLAYING))) {
			prepareNext(getNextNum(myStatus.myCurrentTrackNo));
		}
		doAfter();
	}

	synchronized void seekTo(int pos) {
		if (myStatus.myState.equals(PlayerStatus.State.PAUSED) || myStatus.myState.equals(PlayerStatus.State.PLAYING)) {
			if (pos < myStatus.myBufferPercent * myMP.getDuration() / 100) {
				myMP.seekTo(pos);
			} else {
				myMP.seekTo(myStatus.myBufferPercent * myMP.getDuration() / 100);
			}
		}
		doAfter();
	}

	private synchronized void prepare(int num) {
		prepareNext(num);
		doAfter();
	}

	synchronized void setPlaylistType(String type) {
		myStatus.myType = PlayerStatus.PlaylistType.valueOf(type);
		prepareNext(getNextNum(myStatus.myCurrentTrackNo));
		doAfter();
	}

	synchronized void enqueue(String uri) {
		processEnqueue(uri);
		doAfter();
	}

	synchronized void enqueueAndPlay(String uri) {
		stopPlaying();
		myStatus.myPlaylist.clear();
		this.processEnqueue(uri);
		startPlaying(0);
		doAfter();
	}

	synchronized void remove(int start, int finish) {
		removeFromList(start, finish);
		doAfter();
	}
	
	synchronized void setStopAfter(int num, int type) {
		myStatus.myStopAfter = num;
		myStatus.myStopAfterType = type;
		doAfter();
	}
	
	synchronized String getStatus(String ip) {
		if (myStatus.myState.equals(PlayerStatus.State.PLAYING) || myStatus.myState.equals(PlayerStatus.State.PAUSED)) {
			myStatus.myCurrentDuration = myMP.getDuration();
			myStatus.myCurrentPosition = myMP.getCurrentPosition();
		} else {
			myStatus.myCurrentDuration = 0;
			myStatus.myCurrentPosition = 0;
		}
		myStatus.myVolume = myAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		myStatus.myMaxVolume = myAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		String res;
		try {
			res = myStatus.getXml();
		} catch (UnsupportedEncodingException e) {
			res = "";
			setErrorMessage("UnsupportedEncodingException");
			e.printStackTrace();
		}
		String errorMessage = getErrorMessage(ip);
		if (errorMessage == null) {
			res = WebServer.ResponseTemplate.replace("%BODY%", res).replace("%VALID%", "true").replace("%REASON%", "Ok");
		} else {
			res = WebServer.ResponseTemplate.replace("%BODY%", res).replace("%VALID%", "false").replace("%REASON%", errorMessage);
		}
		return res;
	}
	
	private void onException() {
		myStatus.myState = PlayerStatus.State.STOPPED;
		myStatus.myCurrentTrackNo = -1;
		myMP = null;
		myNextMP = null;
		myNextMPisPrepared = false;
		myStatus.myPlaylist.clear();
		Streamer s = Streamer.getInstance();
		s.clearSrc(Collections.<String> emptySet());
		doAfter();
	}

	private void removeFromList(int start, int finish) {
		if (start <= finish && start >= 0 && finish < myStatus.myPlaylist.size()) {
			if (myStatus.myCurrentTrackNo >= start && myStatus.myCurrentTrackNo <= finish) {
				stopPlaying();
				myStatus.myPlaylist.subList(start, finish + 1).clear();
			} else	if (start > myStatus.myCurrentTrackNo) {
				myStatus.myPlaylist.subList(start, finish + 1).clear();
			} else	if (finish < myStatus.myCurrentTrackNo) {
				myStatus.myPlaylist.subList(start, finish + 1).clear();
				myStatus.myCurrentTrackNo -= (finish - start + 1);
			}
			if (myNextNum >= start && myNextNum <= finish) {
				prepareNext(this.getNextNum(myStatus.myCurrentTrackNo));
			} else 	if (finish < myNextNum) {
				myNextNum -= (finish - start + 1);
			}
		}
	}

	private String myPlayErrorMessage = null;
	private final HashSet<String> myNotifiedIps = new HashSet<>();

	private Timer myErrorTimer;

	private void setErrorMessage(String message) {
		synchronized (myNotifiedIps) {
			myNotifiedIps.clear();
			myPlayErrorMessage = message;
			final Timer t = new Timer();
			myErrorTimer = t;
			myErrorTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					if (myErrorTimer == t) {
						myNotifiedIps.clear();
						myPlayErrorMessage = null;
						Log.d("WTF", "clear error");
					}
				}
			}, 20000);
		}
	}

	private String getErrorMessage(String ip) {
		synchronized (myNotifiedIps) {
			if (!myNotifiedIps.contains(ip)) {
				myNotifiedIps.add(ip);
				return myPlayErrorMessage;
			} else {
				return null;
			}
		}
	}

	private void startPlaying(int num) {
		String uri;
		if (myStatus.myPlaylist.size() > num) {
			uri = myStatus.myPlaylist.get(num).Path;
		} else {
			stopPlaying();
			return;
		}
		if (uri.startsWith("smb://")) {
			myStatus.myBufferPercent = 0;
		} else {
			myStatus.myBufferPercent = 100;
		}
		if (myNextMPisPrepared) {
			if (uri.equals(myNextMPUri)) {
				if (myMP != null) {
					myMP.stop();
					myMP.reset();
					myMP.release();
					myMP = null;
				}
				myMP = myNextMP;
				myNextMP = null;
				myMP.start();
				myStatus.myCurrentTrackNo = num;
				myStatus.myState = PlayerStatus.State.PLAYING;
				prepareNext(getNextNum(myStatus.myCurrentTrackNo));
				return;
			}
		}
		if (num == myStatus.myCurrentTrackNo && (myStatus.myState.equals(PlayerStatus.State.PLAYING) || myStatus.myState.equals(PlayerStatus.State.PAUSED))) {
			myMP.seekTo(0);
			myMP.start();
			myStatus.myState = PlayerStatus.State.PLAYING;
			return;
		}
		myStatus.myState = PlayerStatus.State.WAITING;
		if (myMP != null) {
			myMP.stop();
			myMP.reset();
			myMP.release();
			myMP = null;
		}
		try {
			AbstractFile f = AbstractFile.create(uri, myContext);
			f.test();
			myMP = new MediaPlayer();
			myMP.setWakeMode(myContext, PowerManager.PARTIAL_WAKE_LOCK);
			f.setAsSource(myMP, myContext);
			myMP.setOnCompletionListener(this);
			myMP.setOnErrorListener(this);
			myMP.setOnSeekCompleteListener(this);
			myMP.setOnBufferingUpdateListener(this);
			myMP.prepare();
			setMpVolumeInternal(myStatus.myMpVolume, myMP);
			myMP.start();
			myStatus.myCurrentTrackNo = num;
			myStatus.myState = PlayerStatus.State.PLAYING;
			prepareNext(getNextNum(myStatus.myCurrentTrackNo));
		} catch (FileAuthException e) {
			stopPlaying();
			setErrorMessage("loginNeeded: " + e.getMessage());
			doAfter();
		} catch (Exception e) {
			e.printStackTrace();
			stopPlaying();
			setErrorMessage("Corrupted File:  " + uri.substring(uri.lastIndexOf("/") + 1));
			doAfter();
		}
	}

	private void prepareNext(int num) {
		boolean wasPrepared = myNextMPisPrepared;
		myNextMPisPrepared = false;
		if (myStatus.myPlaylist.size() > num) {
			String uri = myStatus.myPlaylist.get(num).Path;
			if (myNextMP != null) {
				if (wasPrepared) {
					myNextMP.stop();
				}
				myNextMP.reset();
				myNextMP.release();
				myNextMP = null;
			}
			myNextMPUri = uri;
			myNextNum = num;
			myNextMP = new MediaPlayer();
			myNextMP.setWakeMode(myContext, PowerManager.PARTIAL_WAKE_LOCK);
			myNextMP.setOnCompletionListener(this);
			myNextMP.setOnErrorListener(this);
			myNextMP.setOnPreparedListener(this);
			myNextMP.setOnSeekCompleteListener(this);
			myNextMP.setOnBufferingUpdateListener(this);
			setMpVolumeInternal(myStatus.myMpVolume, myNextMP);
			try {
				AbstractFile f = AbstractFile.create(uri, myContext);
				f.test();
				f.setAsSource(myNextMP, myContext);
				myNextMP.prepareAsync();
			} catch (FileAuthException e) {
				myNextMPisPrepared = false;
				myNextMP.reset();
				myNextMP.release();
				myNextMP = null;
				setErrorMessage("loginNeeded: " + e.getMessage());
				doAfter();
			} catch (Exception e) {
				e.printStackTrace();
				myNextMPisPrepared = false;
				myNextMP.reset();
				myNextMP.release();
				myNextMP = null;
				setErrorMessage("Corrupted File:  " + uri.substring(uri.lastIndexOf("/") + 1));
				doAfter();
			}
			
		}
	}
	
	private void processEnqueue(String uri) {
		try {
			AbstractFile f = AbstractFile.create(uri, myContext);
			enqueueRecursive(f);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void enqueueRecursive(AbstractFile f) {
		try {
			if  (!f.exists() || !f.readable()) {
				return;
			}
			if (f.getMediaType() != AbstractFile.MediaType.DIR) {
				if (FileUtils.mediaTypeForPath(f.getPath()).equals(AbstractFile.MediaType.AUDIO)) {
					addToPlaylist(f.getPath());
				}
				return;
			}
			List<AbstractFile> list = f.children();
			Collections.sort(list, new NaturalOrderComparator());
			for (AbstractFile d : list) {
				enqueueRecursive(d);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void pausePlaying() {
		if (myStatus.myState.equals(PlayerStatus.State.PLAYING)) {
			myMP.pause();
			myStatus.myState = PlayerStatus.State.PAUSED;
		}
	}

	private void resumePlaying() {
		if (myStatus.myState.equals(PlayerStatus.State.PAUSED)) {
			myMP.start();
			myStatus.myState = PlayerStatus.State.PLAYING;
		}
	}

	private void stopPlaying() {
		myStatus.myState = PlayerStatus.State.STOPPED;
		myStatus.myCurrentTrackNo = -1;
		myStatus.myStopAfter = -1;
		try {
			if (myMP != null) {
				myMP.stop();
				myMP.reset();
				myMP.release();
				myMP = null;
			}
			if (myNextMP != null) {
				if (myNextMPisPrepared) {
					myNextMP.stop();
					myNextMPisPrepared = false;
				}
				myNextMP.reset();
				myNextMP.release();
				myNextMP = null;
			}
			myNextMPisPrepared = false;
			Streamer s = Streamer.getInstance();
			s.clearSrc(Collections.<String> emptySet());
		} catch (Throwable e) {
			onException();
		}
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		if (mp == myNextMP) {
			mp.start();
			mp.pause();//TODO: check if it helps
			myNextMPisPrepared = true;
		}
	}

	@Override
	public void onSeekComplete(MediaPlayer mp) {
		WebServer server = ((RootApplication)myContext.getApplicationContext()).Server;
		if (server != null) {
			server.notifyClients();
		}
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		try {
			if (mp == myMP) {
				if (myStatus.myCurrentTrackNo < myStatus.myPlaylist.size()) {
					String uri = myStatus.myPlaylist.get(myStatus.myCurrentTrackNo).Path;
					setErrorMessage("Corrupted File:  " + uri.substring(uri.lastIndexOf("/") + 1));
				} else {
					setErrorMessage("Unknown playback error");
				}
			}
			if (mp == myNextMP) {
				//			myCorruptedUris.add(myNextMPUri);
				myNextMPisPrepared = false;
				new Thread() {
					@Override
					public void run() {
						prepare(getNextNum(getNextNum(myStatus.myCurrentTrackNo)));
					}
				}.start();
			}
			if (mp == myBackMP) {
				setErrorMessage("Corrupted Background File");
			}
		} finally {
			doAfter();
		}
		return false;
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		if (mp == myMP) {
			new Thread() {
				@Override
				public void run() {
					if (myStatus.myStopAfter != myStatus.myCurrentTrackNo) {
						play(getNextNum(myStatus.myCurrentTrackNo));
					} else {
						if (myStatus.myStopAfterType == PlayerStatus.PAUSE) {
							myStatus.myStopAfter = -1;
							play(getNextNum(myStatus.myCurrentTrackNo));
							pause();
						} else {
							myStatus.myStopAfter = -1;
							PlayerController.this.stop();
						}
					}
				}
			}.start();
		}
	}


	@Override
	public void onBufferingUpdate(MediaPlayer mp, int percent) {
		if (mp == myMP) {
			myStatus.myBufferPercent = percent;
		}
	}

	int getCurrentTrackNo() {
		return myStatus.myCurrentTrackNo;
	}

}