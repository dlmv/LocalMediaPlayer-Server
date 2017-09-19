package com.dlmv.localplayer.server.main;

import java.io.*;
import java.net.*;
import java.util.*;

import android.content.SharedPreferences;
import android.os.*;
import android.util.Log;

import com.dlmv.localplayer.server.files.AbstractFile;
import com.dlmv.localplayer.server.files.AbstractFile.FileAuthException;
import com.dlmv.localplayer.server.files.AbstractFile.FileException;
import com.dlmv.localplayer.server.utils.LoginDB;
import com.dlmv.localplayer.server.utils.LoginDB.PasswordData;

//import javax.jmdns.*;

import com.dlmv.localplayer.server.utils.FileUtils;
import com.dlmv.localplayer.server.utils.MediaReceiver;
import com.dlmv.localplayer.server.utils.NaturalOrderComparator;
import com.dlmv.localplayer.server.utils.RootApplication;
import com.dlmv.localplayer.server.utils.ServerPath;
import com.test.smbstreamer.Streamer;

import android.content.ComponentName;
import android.content.Context;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import nanohttpd.*;

public class WebServer extends NanoHTTPD {

	final static String ResponseTemplate = "<response valid=\"%VALID%\" reason=\"%REASON%\">\n%BODY%</response>";

	private final String ResultTemplate = "<result path=\"%PATH%\" request=\"%REQUEST%\">\n%FILES%</result>\n";
	
	private final String LoginsTemplate = "<loginlist>\n%LOGINS%</loginlist>\n";

	private String myIp = "";

	private final int myStreamingPort;

	public final int Port;

	//	private WifiManager.MulticastLock myLock = null;
	//	private ArrayList<JmDNS> myJmDNSes = new ArrayList<JmDNS>();
	private Context myContext;

	//public static WebServer Instance;

	private ComponentName myReceiver;
	
	private int initStreamer(int basePort) throws IOException {
		int i = 1;
		while(i < 1000) {
			try {
				new Streamer(basePort + i);
				return basePort + i;
			} catch (BindException ignored) {
			}
			try {
				new Streamer(basePort - i);
				return basePort - i;
			} catch (BindException ignored) {
			}
			++i;
		}
		return -1;
	}

	static WebServer tryCreateWebServer(int port, Context context) throws IOException {
		/*int i = 0;
		while(i < 1000) {
			try {
				return new WebServer(port + i, context);
			} catch (BindException ignored) {
			}
			try {
				return new WebServer(port - i, context);
			} catch (BindException ignored) {
			}
			++i;
		}*/
		return new WebServer(port, context);
	}

	private WebServer(int port, Context context) throws IOException {
		super(port, Environment.getExternalStorageDirectory());
		Log.d("WTF", "start");
		Port = port;
		myContext = context;
		myStreamingPort = initStreamer(port);
		myController = new PlayerController(myContext);
		myReceiver = new ComponentName(myContext.getPackageName(), MediaReceiver.class.getName());
		myController.getAudioManager().registerMediaButtonEventReceiver(myReceiver);
		new LoginDB(context);
		//		expose();
	}
	
	int getStreamingPort() {
		return myStreamingPort;
	}

	public PlayerController getPlayerController() {
		return myController;
	}

	void notifyClients() {
		for (String ip : myNotifiers.keySet()) {
			myNeedToNotifyClient.put(ip, true);
			synchronized (myNotifiers.get(ip)) {
				Log.e("notify", "notify: " + ip);
				myNotifiers.get(ip).notifyAll();
			}
		}
	}

	private PlayerController myController;

	private HashMap<String, Object> myNotifiers = new HashMap<>();
	private HashMap<String, Boolean> myNeedToNotifyClient = new HashMap<>();

	private HashSet<String> myAuthorizedIps = new HashSet<>();

	private String myPassword = "";
    private String myMasterPassword = "";


    void setPassword(String p) {
		myPassword = p;
        clearAuthorized();
        savePasswords();
	}

    void setMasterPassword(String p) {
        myMasterPassword = p;
        savePasswords();
    }

    private void savePasswords() {
        SharedPreferences settings = myContext.getSharedPreferences(MainActivity.PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(MainActivity.PASSWORD, myPassword);
        editor.putString(MainActivity.MASTER_PASSWORD, myMasterPassword);
        editor.apply();
    }

	void authorize(String ip) {
        myAuthorizedIps.add(ip);
        myNotifiers.put(ip, new Object());
        myNeedToNotifyClient.put(ip, false);
    }

    void clearAuthorized() {
        myAuthorizedIps.clear();
        myNotifiers.clear();
        myNeedToNotifyClient.clear();
    }


	@Override
	public Response serve(String uri, String method, Properties header, Properties parms, Properties files, String ip) {
		try {
			if (uri == null) {
				return new NanoHTTPD.Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "404");
			}

			if (uri.startsWith("/")) {
                uri = uri.substring(1);
            }

			if (!myAuthorizedIps.contains(ip)) {
				if (uri.equals(ServerPath.LOGIN)) {
					String pass = (String) parms.get(ServerPath.PASSWORD);
					if ("".equals(myPassword) || (pass != null && pass.equals(myPassword))) {
						authorize(ip);
						return new Response(HTTP_OK, MIME_PLAINTEXT, "");
					}
				}
				return new Response("401 Unauthorized", MIME_PLAINTEXT, "");
			}
			
			if (uri.equals(ServerPath.LOGIN)) {
				return new Response(HTTP_OK, MIME_PLAINTEXT, "");
			}

			if (uri.equals(ServerPath.SET_PASSWORD)) {
				String pass = (String) parms.get(ServerPath.PASSWORD);
				String mpass = (String) parms.get(ServerPath.MASTER_PASSWORD);
				if (pass != null && mpass != null && mpass.equals(myMasterPassword)) {
					setPassword(pass);
				}
				return new Response(HTTP_OK, MIME_PLAINTEXT, "");
			}

			if (uri.equals(ServerPath.CHECK)) {
				try {
					String path = (String) parms.get(ServerPath.PATH);
					if (path == null || "".equals(path)) {
						return new NanoHTTPD.Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "404");
					}
					String login = (String) parms.get(ServerPath.LOGIN);
					String password = (String) parms.get(ServerPath.PASSWORD);
					AbstractFile f;
					if (login != null && password != null) {
						f = AbstractFile.create(path, myContext, login, password);
					} else {
						f = AbstractFile.create(path, myContext);
					}
					f.test();
					String res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", String.valueOf(true)).replace("%REASON%", "Ok");
					return new Response(HTTP_OK, MIME_PLAINTEXT, res);
				} catch (FileAuthException e) {
					String res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", String.valueOf(false)).replace("%REASON%", "loginNeeded: " + e.getMessage());
					return new Response(HTTP_OK, MIME_PLAINTEXT, res);
				}
			}

			if (uri.equals(ServerPath.CHECK_SHARE)) {
				try {
					String path = (String) parms.get(ServerPath.PATH);
					if (path == null || "".equals(path)) {
						return new NanoHTTPD.Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "404");
					}
					String login = (String) parms.get(ServerPath.LOGIN);
					String password = (String) parms.get(ServerPath.PASSWORD);
					AbstractFile f;
					if (login != null && password != null) {
						f = AbstractFile.create(path, myContext, login, password);
					} else {
						f = AbstractFile.create(path, myContext);
					}
					f.children();
					String res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", String.valueOf(true)).replace("%REASON%", "Ok");
					return new Response(HTTP_OK, MIME_PLAINTEXT, res);
				} catch (FileAuthException e) {
					String res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", String.valueOf(false)).replace("%REASON%", "loginNeeded: " + e.getMessage());
					return new Response(HTTP_OK, MIME_PLAINTEXT, res);
				}
			}

			if (uri.equals(ServerPath.BROWSE)) {
				try {
					String path = (String) parms.get(ServerPath.PATH);
					if (path == null || "".equals(path)) {
						return new NanoHTTPD.Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "404");
					}
					String login = (String) parms.get(ServerPath.LOGIN);
					String password = (String) parms.get(ServerPath.PASSWORD);
					if (login != null && password != null) {
						AbstractFile.create(path, myContext, login, password);
					}
					return new Response(HTTP_OK, MIME_PLAINTEXT, browse(path));
				} catch (FileAuthException e) {
					String res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", String.valueOf(false)).replace("%REASON%", "loginNeeded: " + e.getMessage());
					return new Response(HTTP_OK, MIME_PLAINTEXT, res);
				}
			}

			if (uri.equals(ServerPath.SEARCH)) {
				try {
					String path = (String) parms.get(ServerPath.PATH);
					String request = (String) parms.get(ServerPath.REQUEST);
					if (path == null || "".equals(path)) {
						return new NanoHTTPD.Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "404");
					}
					myStopSearchFlag = false;
					return new Response(HTTP_OK, MIME_PLAINTEXT, search(path, request));
				} catch (FileAuthException e) {
					String res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", String.valueOf(false)).replace("%REASON%", "loginNeeded: " + e.getMessage());
					return new Response(HTTP_OK, MIME_PLAINTEXT, res);
				}
			}

			if (uri.equals(ServerPath.STOP_SEARCH)) {
				stopSearch();
				return new Response(HTTP_OK, MIME_PLAINTEXT, "");
			}

			if (uri.equals(ServerPath.STATUS)) {
				String res = myController.getStatus(ip);
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}

			if (uri.equals(ServerPath.LAZY_STATUS)) {
				if (!myNeedToNotifyClient.get(ip)) {
					try {
						synchronized (myNotifiers.get(ip)) {
							myNotifiers.get(ip).wait(4000);
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				myNeedToNotifyClient.put(ip, false);
				String res = myController.getStatus(ip);
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}

			if (uri.equals(ServerPath.ENQUEUE)) {
				String path = (String) parms.get(ServerPath.PATH);
				myController.enqueue(path);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.ENQUEUE_AND_PLAY)) {
				String path = (String) parms.get(ServerPath.PATH);
				myController.enqueueAndPlay(path);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}

			if (uri.equals(ServerPath.SET_VOLUME)) {
				int vol = Integer.parseInt((String) parms.get(ServerPath.VOLUME));
				myController.setVolume(vol);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.SET_MP_VOLUME)) {
				int vol = Integer.parseInt((String) parms.get(ServerPath.VOLUME));
				myController.setMpVolume(vol);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.SET_BACKMP_VOLUME)) {
				int vol = Integer.parseInt((String) parms.get(ServerPath.VOLUME));
				myController.setBackVolume(vol);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.VOLUME_UP)) {
				myController.volumeUp();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.VOLUME_DOWN)) {
				myController.volumeDown();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}

			if (uri.equals(ServerPath.PLAY)) {
				myController.play();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.PLAY_NUM)) {
				int num = Integer.parseInt((String) parms.get(ServerPath.NUM));
				myController.play(num);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.REMOVE)) {
				int start = Integer.parseInt((String) parms.get(ServerPath.START));
				int finish = Integer.parseInt((String) parms.get(ServerPath.FINISH));
				myController.remove(start, finish);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.PAUSE)) {
				myController.pause();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}	
			if (uri.equals(ServerPath.SET_PLAYTYPE)) {
				String type = (String) parms.get(ServerPath.TYPE);
				myController.setPlaylistType(type);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}	
			if (uri.equals(ServerPath.STOP)) {
				myController.stop();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.STOP_AFTER)) {
				int num = Integer.parseInt((String) parms.get(ServerPath.NUM));
				int type = Integer.parseInt((String) parms.get(ServerPath.TYPE));
				myController.setStopAfter(num, type);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.startsWith(ServerPath.SEEK_TO)) {
				int num = Integer.parseInt((String) parms.get(ServerPath.NUM));
				int pos = Integer.parseInt((String) parms.get(ServerPath.POSITION));
				if (num == myController.getCurrentTrackNo()) {
					myController.seekTo(pos);
				}
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}

			if (uri.equals(ServerPath.CLEAR)) {
				myController.clearPlaylist();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}

			if (uri.equals(ServerPath.IMAGE)) {
				String path = (String) parms.get(ServerPath.PATH);
				InputStream i = AbstractFile.create(path, myContext).getInputStream();
				return new Response(HTTP_OK, FileUtils.mimeForPath(path), i);
			}
			
			if (uri.equals(ServerPath.PLAY_BACKGROUND)) {
				String path = (String) parms.get(ServerPath.PATH);
				myController.playBackground(path);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.PAUSE_BACKGROUND)) {
				myController.pauseBackground();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.RESUME_BACKGROUND)) {
				myController.resumeBackground();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.STOP_BACKGROUND)) {
				myController.stopBackground();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.LOGIN_LIST)) {
				String res = loginList();
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals(ServerPath.FORGET_LOGIN)) {
				String share = (String) parms.get(ServerPath.PATH);
				((RootApplication)myContext.getApplicationContext()).LoginDB().delete(share);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}	


			return new NanoHTTPD.Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "404");
		} catch (Exception e) {//server must not fall
			e.printStackTrace();
			return new NanoHTTPD.Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "404");
		}
	}

	private volatile boolean myStopSearchFlag = false;

	private void stopSearch() {
		myStopSearchFlag = true;
		Log.d("WTF", "" + myStopSearchFlag);
	}

	private String search(String uri, String request) throws FileAuthException {
		String sres = "";
		try {
			ArrayList<AbstractFile> res = new ArrayList<>();
			AbstractFile f = AbstractFile.create(uri, myContext);
			boolean result = searchRecursive(f, request.toLowerCase(), res);
			if (!result) {
				sres = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", String.valueOf(false)).replace("%REASON%", "Search dismissed");
				Log.d("res", sres);
				return sres;
			}
			String tmp = "";
			for (AbstractFile f1 : res) {
				try {
					tmp += f1.getXml();
				} catch (FileException e) {
					e.printStackTrace();
				}
			}
			String path = uri;
			if (!path.endsWith("/")) {
				path += "/";
			}
			try {
				path = URLEncoder.encode(path, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			sres = ResultTemplate.replace("%PATH%", path).replace("%FILES%", tmp).replace("%REQUEST%", request);
			sres = ResponseTemplate.replace("%BODY%", sres).replace("%VALID%", String.valueOf(true)).replace("%REASON%", "Ok");
			Log.d("res", sres);
		} catch (FileAuthException e) {
			throw e;
		} catch (FileException e) {
			e.printStackTrace();
			sres = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", String.valueOf(false)).replace("%REASON%", "Server error!");
		}
		return sres;
	}
	
	private boolean searchRecursive(AbstractFile f, String request, ArrayList<AbstractFile> res) {
		if (myStopSearchFlag) {
			return false;
		}
		try {
			if  (!f.exists() || !f.readable()) {
				return true;
			}

			if (f.getName().toLowerCase().contains(request)) {
				res.add(f);
			}
			if (f.getMediaType() != AbstractFile.MediaType.DIR) {
				return true;
			}


			List<AbstractFile> list = f.children();
			Collections.sort(list, new NaturalOrderComparator());
			for (AbstractFile f1 : list) {
				if (!searchRecursive(f1, request, res)) {
					return false;
				}
			}
			return true;
		} catch (FileException e) {
			e.printStackTrace();
			return true;
		}
	}

	private String browse(String uri) throws FileAuthException {
		String res = "";
		try {
			AbstractFile f = AbstractFile.create(uri, myContext);
			if  (!f.exists()) {
				res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", String.valueOf(false)).replace("%REASON%", "Not Found");
			} else if (!f.readable()) {
				res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", String.valueOf(false)).replace("%REASON%", "Forbidden");
			} else if (f.getMediaType() == AbstractFile.MediaType.DIR) {
				String tmp = "";
				List<AbstractFile> list = f.children();
				Collections.sort(list, new NaturalOrderComparator());
				for (AbstractFile f1 : list) {
					try {
						tmp += f1.getXml();
					} catch (FileException e) {
						e.printStackTrace();
					}
				}
				String path = uri;
				if (!path.endsWith("/")) {
					path += "/";
				}
				try {
					path = URLEncoder.encode(path, "UTF-8");
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
				res = ResultTemplate.replace("%PATH%", path).replace("%FILES%", tmp).replace("%REQUEST%", "");
				res = ResponseTemplate.replace("%BODY%", res).replace("%VALID%", String.valueOf(true)).replace("%REASON%", "Ok");
			}
		} catch (FileAuthException e) {
			throw e;
		} catch (FileException e) {
			e.printStackTrace();
			res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", String.valueOf(false)).replace("%REASON%", "Server error!");
		}
		return res;
	}
	
	private String loginList() {
		String res = "";
		String tmp = "";
		List<PasswordData> list = ((RootApplication)myContext.getApplicationContext()).LoginDB().listShares();
		for (PasswordData p : list) {
			try {
				tmp += p.getXml();
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		res = LoginsTemplate.replace("%LOGINS%", tmp);
		res = ResponseTemplate.replace("%BODY%", res).replace("%VALID%", String.valueOf(true)).replace("%REASON%", "Ok");
		return res;
	}
	
	public void stop() {
		myController.getAudioManager().unregisterMediaButtonEventReceiver(myReceiver);
		myController.clearPlaylist();
		myController.stopBackground();
		Streamer.getInstance().stop();
		super.stop();
	}

	String getIp() {
		if (getLocalIpAddresses().size() > 0) {
			return getLocalIpAddresses().get(0).getHostAddress();
		}
		return "Not connected";
	}

	private List<InetAddress> getLocalIpAddresses() {
		final List<InetAddress> addresses = new LinkedList<>();
		Method testPtoPMethod = null;
		try {
			testPtoPMethod = NetworkInterface.class.getMethod("isPointToPoint");
		} catch (NoSuchMethodException ignored) {
		}
		try {
			for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				try {
					if (testPtoPMethod != null && (Boolean)testPtoPMethod.invoke(iface)) {
						continue;
					}
				} catch (IllegalAccessException ignored) {
				} catch (InvocationTargetException ignored) {
				}
				for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
					if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
						addresses.add(addr);
						if (myIp.equals("")) {
							myIp = addr.getHostAddress();
						}
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
		return addresses;
	}

}
