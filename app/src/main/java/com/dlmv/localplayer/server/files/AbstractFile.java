package com.dlmv.localplayer.server.files;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import android.content.Context;
import android.media.MediaPlayer;

public abstract class AbstractFile {

	public enum MediaType {
		AUDIO,
		IMAGE,
		OTHER,
		DIR,
		UP,
	}

	public abstract String getPath();
	public abstract boolean readable() throws FileException;
	public abstract boolean exists() throws FileException;
	public abstract String getSizeString() throws FileException;
	
	MediaType myType;
	
	public MediaType getMediaType() {
		return myType;
	}
	
	public static class FileException extends Exception {
		private static final long serialVersionUID = -3759825869889998293L;
		FileException(Throwable throwable) {
			super(throwable);
		}
		FileException(String message, Throwable throwable) {
			super(message, throwable);
		}
	};
	
	public static class FileAuthException extends FileException {
		private static final long serialVersionUID = 8218872182332120663L;
		FileAuthException(String message, Throwable throwable) {
			super(message, throwable);
		}
	}

	public String getName() {
		String path = getPath();
		if (path.equals("/") || path.equals("smb://")) {
			return path;
		}
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		int divider = path.lastIndexOf("/");
		return path.substring(divider + 1);
	}

	public void test() throws FileException {

	}
	

	public static String parent(String Path) {
		if (Path.equals("/") || Path.equals("smb://")) {
			return null;
		} else {
			String path = Path.substring(0, Path.length() - 1);
			int divider = path.lastIndexOf("/");
			return path.substring(0, divider) + "/";
		}
	}

	private static final String ourTemplate = "<absfile path=\"%PATH%\" size=\"%SIZE%\" readable=\"%READABLE%\" type=\"%TYPE%\" />";

	public String getXml() throws FileException  {
		try {
			return ourTemplate
					.replace("%PATH%", URLEncoder.encode(getPath(), "UTF-8"))
					.replace("%SIZE%", getSizeString())
					.replace("%READABLE%", Boolean.toString(readable()))
					.replace("%TYPE%", getMediaType().name())
					;
		} catch (UnsupportedEncodingException e) {
			throw new FileException(e);
		}
	}
	
	public static AbstractFile create(String path, Context c) throws FileException {
		if (path.startsWith("smb://")) {
			try {
				SambaFile res = new SambaFile(path, c);
				res.test();
				return res;
			} catch (Exception e) {
				return new SambaFile(path + "/", c);
			}
		} else {
			return new LocalFile(path);
		}
	}
	
	public static AbstractFile create(String path, Context c, String login, String password) throws FileException {
		if (path.startsWith("smb://")) {
			try {
				SambaFile res = new SambaFile(path, c, login, password);
				res.test();
				return res;
			} catch (Exception e) {
				return new SambaFile(path + "/", c, login, password);
			}
		} else {
			return new LocalFile(path);
		}
	}
	
	public abstract List<AbstractFile> children() throws FileException;
	public abstract InputStream getInputStream() throws FileException;
	public abstract void setAsSource(MediaPlayer mp, Context c) throws FileException;
	

}