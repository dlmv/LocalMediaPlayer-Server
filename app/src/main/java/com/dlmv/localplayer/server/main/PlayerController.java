package com.dlmv.localplayer.server.main;

import java.io.UnsupportedEncodingException;
import java.util.*;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.PowerManager;

import com.dlmv.localplayer.*;
import com.dlmv.localplayer.PlayerStatus.PlaylistItem;
import com.dlmv.localplayer.server.files.AbstractFile;
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
	}

	void stopBackground() {
		stopBackgroundInternal();
		doAfter();
	}

	void playBackground(String uri) {
		stopBackgroundInternal();
		try {
			AbstractFile f = AbstractFile.create(uri, myContext);
			myBackMP = new MediaPlayer();
			myBackMP.setWakeMode(myContext, PowerManager.PARTIAL_WAKE_LOCK);
			f.setAsSource(myBackMP, myContext);
			myBackMP.setOnCompletionListener(this);
			myBackMP.setOnErrorListener(this);
			myBackMP.prepare();
			myBackMP.setLooping(true);
			setMpVolumeInternal(myStatus.myBackMpVolume, myBackMP);
			myBackMP.start();
		} catch (Exception e) {
			e.printStackTrace();
			myPlayErrorMessage = "Corrupted Background File";
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
		myStatus.myRadioUri = null;
		myStatus.myPlaylist.clear();
		doAfter();
	}

	private synchronized void addToPlaylist(String uri) {
		myStatus.myPlaylist.add(new PlaylistItem(uri));
		if (myStatus.myPlaylist.size() == myStatus.myCurrentTrackNo + 2) {
			prepareNext(getNextNum(myStatus.myCurrentTrackNo));
		}
		doAfter();
	}

	synchronized void seekTo(int pos) {
		if (myStatus.myRadioUri != null) {
			return;
		}
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
		if (myStatus.myRadioUri != null) {
			myStatus.myRadioUri = null;
			stopPlaying();
		}
		processEnqueue(uri);
		doAfter();
	}

	synchronized void enqueueAndPlay(String uri) {
		if (myStatus.myRadioUri != null) {
			myStatus.myRadioUri = null;
		}
		stopPlaying();
		myStatus.myPlaylist.clear();
		this.processEnqueue(uri);
		startPlaying(0);
		doAfter();
	}

	synchronized void remove(int num) {
		removeFromList(num);
		doAfter();
	}
	
	synchronized void setStopAfter(int num, int type) {
		myStatus.myStopAfter = num;
		myStatus.myStopAfterType = type;
		doAfter();
	}
	
	synchronized String getStatus() {
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
			myPlayErrorMessage = "UnsupportedEncodingException";
			e.printStackTrace();
		}
		if (myPlayErrorMessage == null) {
			res = WebServer.ResponseTemplate.replace("%BODY%", res).replace("%VALID%", "true").replace("%REASON%", "Ok");
		} else {
			res = WebServer.ResponseTemplate.replace("%BODY%", res).replace("%VALID%", "false").replace("%REASON%", myPlayErrorMessage);
			myPlayErrorMessage = null;
		}
		return res;
	}
	
	private void onException() {
		myStatus.myState = PlayerStatus.State.STOPPED;
		myStatus.myCurrentTrackNo = -1;
		myMP = null;
		myNextMP = null;
		myNextMPisPrepared = false;
		myStatus.myRadioUri = null;
		myStatus.myPlaylist.clear();
		Streamer s = Streamer.getInstance();
		s.clearSrc(Collections.<String> emptySet());
		doAfter();
	}

	private void removeFromList(int num) {
		if (num < myStatus.myPlaylist.size()) {
			if (num == myStatus.myCurrentTrackNo) {
				stopPlaying();
				myStatus.myPlaylist.remove(num);
			} else	if (num > myStatus.myCurrentTrackNo) {
				myStatus.myPlaylist.remove(num);
			} else	if (num < myStatus.myCurrentTrackNo) {
				myStatus.myPlaylist.remove(num);
				myStatus.myCurrentTrackNo -= 1;
			}
			if (num == myNextNum) {
				prepareNext(this.getNextNum(myStatus.myCurrentTrackNo));
			} else 	if (num < myNextNum) {
				myNextNum -= 1;
			}
		}
	}

	private String myPlayErrorMessage;

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
		} catch (Exception e) {
			e.printStackTrace();
			stopPlaying();
			//			myCorruptedUris.add(uri);
			myPlayErrorMessage = "Corrupted File:  " + uri.substring(uri.lastIndexOf("/") + 1);
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
				f.setAsSource(myNextMP, myContext);
				myNextMP.prepareAsync();
			} catch (Exception e) {
				e.printStackTrace();
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
				if (myStatus.myRadioUri != null) {
					myPlayErrorMessage = "Corrupted Radio Stream:  " + myStatus.myRadioUri;
					myMP = null;
					stopPlaying();
					return false;
				}
				if (myStatus.myCurrentTrackNo < myStatus.myPlaylist.size()) {
					String uri = myStatus.myPlaylist.get(myStatus.myCurrentTrackNo).Path;
					myPlayErrorMessage = "Corrupted File:  " + uri.substring(uri.lastIndexOf("/") + 1);
				} else {
					myPlayErrorMessage = "Unknown playback error";
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
				myPlayErrorMessage = "Corrupted Background File";
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