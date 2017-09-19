package com.dlmv.localplayer.server.main;

import java.io.*;
import java.net.*;
import java.util.*;

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

	private String myPass = "";

	void setPass(String p) {
		myPass = p;
	}

	@Override
	public Response serve(String uri, String method, Properties header, Properties parms, Properties files, String ip) {
		try {
			if (uri == null) {
				return new NanoHTTPD.Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "404");
			}

			if (!myAuthorizedIps.contains(ip)) {
				if ("".equals(myPass)) {
					myAuthorizedIps.add(ip);
					myNotifiers.put(ip, new Object());
					myNeedToNotifyClient.put(ip, false);
					if (uri.equals("/login")) {
						return new Response(HTTP_OK, MIME_PLAINTEXT, "");
					}
				} else {
					if (uri.equals("/login")) {
						String pass = (String) parms.get("password");
						if (pass != null && pass.equals(myPass)) {
							myAuthorizedIps.add(ip);
							myNotifiers.put(ip, new Object());
							myNeedToNotifyClient.put(ip, false);
							return new Response(HTTP_OK, MIME_PLAINTEXT, "");
						}
					}
					return new Response("401 Unauthorized", MIME_PLAINTEXT, "");
				}
			}
			
			if (uri.equals("/login")) {
				return new Response(HTTP_OK, MIME_PLAINTEXT, "");
			}

			if (uri.equals("/test")) {
				try {
					String path = (String) parms.get("path");
					if (path == null || "".equals(path)) {
						return new NanoHTTPD.Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "404");
					}
					String login = (String) parms.get("login");
					String password = (String) parms.get("password");
					AbstractFile f;
					if (login != null && password != null) {
						f = AbstractFile.create(path, myContext, login, password);
					} else {
						f = AbstractFile.create(path, myContext);
					}
					f.test();
					String res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", "true").replace("%REASON%", "Ok");
					return new Response(HTTP_OK, MIME_PLAINTEXT, res);
				} catch (FileAuthException e) {
					String res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", "false").replace("%REASON%", "loginNeeded: " + e.getMessage());
					return new Response(HTTP_OK, MIME_PLAINTEXT, res);
				}
			}

			if (uri.equals("/testShare")) {
				try {
					String path = (String) parms.get("path");
					if (path == null || "".equals(path)) {
						return new NanoHTTPD.Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "404");
					}
					String login = (String) parms.get("login");
					String password = (String) parms.get("password");
					AbstractFile f;
					if (login != null && password != null) {
						f = AbstractFile.create(path, myContext, login, password);
					} else {
						f = AbstractFile.create(path, myContext);
					}
					f.children();
					String res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", "true").replace("%REASON%", "Ok");
					return new Response(HTTP_OK, MIME_PLAINTEXT, res);
				} catch (FileAuthException e) {
					String res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", "false").replace("%REASON%", "loginNeeded: " + e.getMessage());
					return new Response(HTTP_OK, MIME_PLAINTEXT, res);
				}
			}

			if (uri.equals("/browse")) {
				try {
					String path = (String) parms.get("path");
					if (path == null || "".equals(path)) {
						return new NanoHTTPD.Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "404");
					}
					String login = (String) parms.get("login");
					String password = (String) parms.get("password");
					if (login != null && password != null) {
						AbstractFile.create(path, myContext, login, password);
					}
					return new Response(HTTP_OK, MIME_PLAINTEXT, browse(path));
				} catch (FileAuthException e) {
					String res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", "false").replace("%REASON%", "loginNeeded: " + e.getMessage());
					return new Response(HTTP_OK, MIME_PLAINTEXT, res);
				}
			}

			if (uri.equals("/search")) {
				try {
					String path = (String) parms.get("path");
					String request = (String) parms.get("request");
					if (path == null || "".equals(path)) {
						return new NanoHTTPD.Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "404");
					}
					myStopSearchFlag = false;
					return new Response(HTTP_OK, MIME_PLAINTEXT, search(path, request));
				} catch (FileAuthException e) {
					String res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", "false").replace("%REASON%", "loginNeeded: " + e.getMessage());
					return new Response(HTTP_OK, MIME_PLAINTEXT, res);
				}
			}

			if (uri.equals("/stopsearch")) {
				stopSearch();
				return new Response(HTTP_OK, MIME_PLAINTEXT, "");
			}

			if (uri.equals("/status")) {
				String res = myController.getStatus(ip);
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}

			if (uri.equals("/lazystatus")) {
				if (!myNeedToNotifyClient.get(ip)) {
					try {
						synchronized (myNotifiers.get(ip)) {
							myNotifiers.get(ip).wait(4000);
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				Log.e("notify", "notified: " + ip);
				myNeedToNotifyClient.put(ip, false);
				String res = myController.getStatus(ip);
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}

			if (uri.equals("/enqueue")) {
				String path = (String) parms.get("path");
				myController.enqueue(path);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/enqueueandplay")) {
				String path = (String) parms.get("path");
				myController.enqueueAndPlay(path);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}

			if (uri.equals("/setvolume")) {
				int vol = Integer.parseInt((String) parms.get("volume"));
				myController.setVolume(vol);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/setmpvolume")) {
				int vol = Integer.parseInt((String) parms.get("volume"));
				myController.setMpVolume(vol);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/setbackmpvolume")) {
				int vol = Integer.parseInt((String) parms.get("volume"));
				myController.setBackVolume(vol);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/volumeup")) {
				myController.volumeUp();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/volumedown")) {
				myController.volumeDown();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}

			if (uri.equals("/play")) {
				myController.play();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/playnum")) {
				int num = Integer.parseInt((String) parms.get("num"));
				myController.play(num);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/remove")) {
				int start = Integer.parseInt((String) parms.get("start"));
				int finish = Integer.parseInt((String) parms.get("finish"));
				myController.remove(start, finish);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/pause")) {
				myController.pause();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}	
			if (uri.equals("/setplaytype")) {
				String type = (String) parms.get("type");
				myController.setPlaylistType(type);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}	
			if (uri.equals("/stop")) {
				myController.stop();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/stopafter")) {
				int num = Integer.parseInt((String) parms.get("num"));
				int type = Integer.parseInt((String) parms.get("type"));
				myController.setStopAfter(num, type);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.startsWith("/seekto")) {
				int num = Integer.parseInt((String) parms.get("num"));
				int pos = Integer.parseInt((String) parms.get("position"));
				if (num == myController.getCurrentTrackNo()) {
					myController.seekTo(pos);
				}
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}

			if (uri.equals("/clearplaylist")) {
				myController.clearPlaylist();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}

			if (uri.equals("/image")) {
				String path = (String) parms.get("path");
				InputStream i = AbstractFile.create(path, myContext).getInputStream();
				return new Response(HTTP_OK, FileUtils.mimeForPath(path), i);
			}
			
			if (uri.equals("/playbackground")) {
				String path = (String) parms.get("path");
				myController.playBackground(path);
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/pausebackground")) {
				myController.pauseBackground();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/resumebackground")) {
				myController.resumeBackground();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/stopbackground")) {
				myController.stopBackground();
				String res = "";
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/loginlist")) {
				String res = loginList();
				return new Response(HTTP_OK, MIME_PLAINTEXT, res);
			}
			if (uri.equals("/forgetlogin")) {
				String share = (String) parms.get("share");
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
				sres = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", "false").replace("%REASON%", "Search dismissed");
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
			sres = ResponseTemplate.replace("%BODY%", sres).replace("%VALID%", "true").replace("%REASON%", "Ok");
			Log.d("res", sres);
		} catch (FileAuthException e) {
			throw e;
		} catch (FileException e) {
			e.printStackTrace();
			sres = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", "false").replace("%REASON%", "Server error!");
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
				res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", "false").replace("%REASON%", "Not Found");
			} else if (!f.readable()) {
				res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", "false").replace("%REASON%", "Forbidden");
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
				res = ResponseTemplate.replace("%BODY%", res).replace("%VALID%", "true").replace("%REASON%", "Ok");
			}
		} catch (FileAuthException e) {
			throw e;
		} catch (FileException e) {
			e.printStackTrace();
			res = ResponseTemplate.replace("%BODY%", "").replace("%VALID%", "false").replace("%REASON%", "Server error!");
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
		res = ResponseTemplate.replace("%BODY%", res).replace("%VALID%", "true").replace("%REASON%", "Ok");
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
