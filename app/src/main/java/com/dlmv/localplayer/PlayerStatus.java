package com.dlmv.localplayer;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class PlayerStatus {
	public enum State {
		PLAYING,
		PAUSED,
		STOPPED,
		WAITING,
	}
	public State myState = State.STOPPED;
	
	public String myRadioUri = null;

	public enum PlaylistType {
		LINEAR,
		CYCLIC,
	}
	public PlaylistType myType = PlaylistType.LINEAR;
	
	public PlaylistType getNextType() {
		if (myType.equals(PlaylistType.LINEAR)) {
			return PlaylistType.CYCLIC;
		}
		return PlaylistType.LINEAR;
	}
	
	public static class PlaylistItem {
		public final String Path;
		public PlaylistItem(String uri) {
			Path = uri;
		}
		public String getName() {
			String name = Path;
			if (name.endsWith("/")) {
				name = name.substring(0, name.length() - 1);
			}
			int divider = name.lastIndexOf("/");
			return name.substring(divider + 1);
		}
	}
	
	public static final int STOP = 0;
	public static final int PAUSE = 1;

	public ArrayList<PlaylistItem> myPlaylist = new ArrayList<PlaylistItem>();
	public int myCurrentTrackNo;
	public int myCurrentDuration;
	public int myCurrentPosition;
	public int myBufferPercent;
	public int myVolume;
	public int myMaxVolume;
	public int myStopAfter = -1;
	public int myStopAfterType = STOP;
	
	public int myMpVolume = 100;
	public int myMpMaxVolume = 100;
	public int myBackMpVolume = 100;
	public int myBackMpMaxVolume = 100;

	private final String ourStatusTemplate = "<status state=\"%STATE%\" playing=\"%NUM%\" stopAfter=\"%STOPNUM%\" stopType=\"%STOPTYPE%\" duration=\"%DURATION%\" radio=\"%RADIO%\" position=\"%POSITION%\" buffered=\"%BUFFER%\" volume=\"%VOLUME%\" maxvolume=\"%MAXVOLUME%\" mpvolume=\"%MPVOLUME%\" mpmaxvolume=\"%MPMAXVOLUME%\" backmpvolume=\"%BACKMPVOLUME%\" backmpmaxvolume=\"%BACKMPMAXVOLUME%\" playtype=\"%TYPE%\">\n%PLAYLIST%</status>\n";
	private final String ourItemTemplate = "<item path=\"%PATH%\"/>\n";

	public String getXml() throws UnsupportedEncodingException  {
		String pls = "";
		for (PlaylistItem s : myPlaylist) {
			try {
				pls += ourItemTemplate.replace("%PATH%", URLEncoder.encode(s.Path,"UTF-8"));
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		String res = ourStatusTemplate.replace("%STATE%", myState.name())
				.replace("%NUM%", Integer.toString(myCurrentTrackNo))
				.replace("%STOPNUM%", Integer.toString(myStopAfter))
				.replace("%STOPTYPE%", Integer.toString(myStopAfterType))
				.replace("%DURATION%", Integer.toString(myCurrentDuration))
				.replace("%POSITION%", Integer.toString(myCurrentPosition))
				.replace("%BUFFER%", Integer.toString(myBufferPercent))
				.replace("%VOLUME%", Integer.toString(myVolume))
				.replace("%MAXVOLUME%", Integer.toString(myMaxVolume))
				.replace("%MPVOLUME%", Integer.toString(myMpVolume))
				.replace("%MPMAXVOLUME%", Integer.toString(myMpMaxVolume))
				.replace("%BACKMPVOLUME%", Integer.toString(myBackMpVolume))
				.replace("%BACKMPMAXVOLUME%", Integer.toString(myBackMpMaxVolume))
				.replace("%TYPE%", myType.name())
				.replace("%RADIO%", myRadioUri == null ? "null" : myRadioUri)
				.replace("%PLAYLIST%", pls);
		return res;
	}
	
	public static PlayerStatus fromDom(Element e) throws UnsupportedEncodingException {
		PlayerStatus status = new PlayerStatus();
		status.myState = State.valueOf(e.getAttribute("state"));
		status.myType = PlaylistType.valueOf(e.getAttribute("playtype"));
		status.myCurrentDuration = Integer.parseInt(e.getAttribute("duration"));
		status.myCurrentPosition = Integer.parseInt(e.getAttribute("position"));
		status.myVolume = Integer.parseInt(e.getAttribute("volume"));
		status.myMaxVolume = Integer.parseInt(e.getAttribute("maxvolume"));
		status.myMpVolume = Integer.parseInt(e.getAttribute("mpvolume"));
		status.myMpMaxVolume = Integer.parseInt(e.getAttribute("mpmaxvolume"));
		status.myBackMpVolume = Integer.parseInt(e.getAttribute("backmpvolume"));
		status.myBackMpMaxVolume = Integer.parseInt(e.getAttribute("backmpmaxvolume"));
		status.myCurrentTrackNo = Integer.parseInt(e.getAttribute("playing"));
		status.myStopAfter = Integer.parseInt(e.getAttribute("stopAfter"));
		status.myStopAfterType = Integer.parseInt(e.getAttribute("stopType"));
		status.myRadioUri = e.getAttribute("radio");
		status.myRadioUri = "null".equals(status.myRadioUri) ? null : status.myRadioUri;
		NodeList list1 = e.getElementsByTagName("item");
		status.myPlaylist = new ArrayList<PlaylistItem>();
		for (int i = 0; i < list1.getLength(); ++i) {
			Element e1 = (Element)list1.item(i);
			String path = URLDecoder.decode(e1.getAttribute("path"), "UTF-8");
			PlaylistItem it = new PlaylistItem(path);
			status.myPlaylist.add(it);
		}
		return status;
	}
	
	
}