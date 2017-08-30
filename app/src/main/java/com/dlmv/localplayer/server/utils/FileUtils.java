package com.dlmv.localplayer.server.utils;

import com.dlmv.localplayer.server.files.AbstractFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class FileUtils {

	public static String mimeForPath(String p) {
		p = p.toLowerCase();
		if (p.endsWith(".mp3")) {
			return "audio/mpeg";
		}
		if (p.endsWith(".ogg")) {
			return "appication/ogg";
		}
		if (p.endsWith(".jpg")) {
			return "image/jpeg";
		}
		if (p.endsWith(".jpeg")) {
			return "image/jpeg";
		}
		if (p.endsWith(".png")) {
			return "image/png";
		}
		if (p.endsWith(".gif")) {
			return "image/gif";
		}
		return "";
	}

	private static List<String> audioExts = Collections.unmodifiableList(Arrays.asList(".mp3", ".ogg"));
	private static List<String> imageExts = Collections.unmodifiableList(
			Arrays.asList(".png",".jpg",".jpeg",".gif"));

	public static AbstractFile.MediaType mediaTypeForPath(String p) {
		if (audioExts.contains(getExtension(p))) {
			return AbstractFile.MediaType.AUDIO;
		}
		if (imageExts.contains(getExtension(p))) {
			return AbstractFile.MediaType.IMAGE;
		}
		return AbstractFile.MediaType.OTHER;
	}

	public static String fileSize(long length) {
		if (length == 0) {
			return "0B";
		}
		String unit = "B";
		double l = length;
		if (l > 1000000000) {
			l = l / 1000000000;
			unit = "GB";
		} else if (l > 1000000) {
			l = l / 1000000;
			unit = "MB";
		} else if (l > 1000) {
			l = l / 1000;
			unit = "kB";
		}
		String res;
		if (l < 10) {
			int t = (int)(l * 10);
			res = Float.toString(((float)t) / 10);
		} else {
			res = Integer.toString((int)l);
		}
		return res + unit;
	}

	public static String getExtension(String p) {
		p = p.toLowerCase();
		if (p.contains(".")) {
			return p.substring(p.lastIndexOf("."));
		}
		return "";
	}
	
	public static String md5(final String s) {
		try {
			// Create MD5 Hash
			MessageDigest digest = java.security.MessageDigest
					.getInstance("MD5");
			digest.update(s.getBytes());
			byte messageDigest[] = digest.digest();

			// Create Hex String
			StringBuilder hexString = new StringBuilder();
			for (byte aMessageDigest : messageDigest) {
				String h = Integer.toHexString(0xFF & aMessageDigest);
				while (h.length() < 2)
					h = "0" + h;
				hexString.append(h);
			}
			return hexString.toString();

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return "";
	}

}
