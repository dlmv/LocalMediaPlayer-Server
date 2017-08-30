package com.test.smbstreamer;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;

import com.dlmv.localplayer.server.utils.FileUtils;

import jcifs.smb.SmbFile;

public class Streamer extends StreamServer {

	public static int PORT = 8124;
	public static String URL = "http://127.0.0.1:" + PORT;

	private HashMap<String, SmbFile> mySources = new HashMap<String, SmbFile>();
	// private InputStream stream;
	// private long length;
	private static Streamer instance;

	// private CBItem source;
	// private String mime;

	public Streamer(int port) throws IOException {
		super(port, new File("/"));
		PORT = port;
		URL = "http://127.0.0.1:" + PORT;
		instance = this;
	}

	public static Streamer getInstance() {
		if (instance == null)
			try {
				instance = new Streamer(PORT);
			} catch (IOException e) {
				e.printStackTrace();
			}
		return instance;
	}

	public static boolean isStreamMedia(String name) {
		return name.matches("^.*\\.(?i)(mp3|mp4|avi|3gp|3gpp)$");
	}

	public void setStreamSrc(SmbFile src, String uri) {
		mySources.put(uri, src);
	}

	public void clearSrc(Collection<String> s) {
		mySources.keySet().retainAll(s);
	}

	@Override
	public Response serve(String uri, String method, Properties header, Properties parms, Properties files) {
		try {
			Response res = null;
			SmbFile sf = mySources.get(uri);
			StreamSource source = null;
			try {
				source = new SMBMediaSource(sf, FileUtils.mimeForPath(sf.getName()));
			} catch (StreamSourceException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (source == null) {
				return new Response(HTTP_NOTFOUND, MIME_PLAINTEXT, null);
			}
			try {
				if (res == null) {
					long startFrom = 0;
					long endAt = -1;
					String range = header.getProperty("range");
					if (range != null) {
						if (range.startsWith("bytes=")) {
							range = range.substring("bytes=".length());
							int minus = range.indexOf('-');
							try {
								if (minus > 0) {
									startFrom = Long.parseLong(range.substring(0,
											minus));
									endAt = Long.parseLong(range
											.substring(minus + 1));
								}
							} catch (NumberFormatException nfe) {
							}
						}
					}
					//				Log.d("Explorer", "Request: " + range + " from: " + startFrom
					//						+ ", to: " + endAt);

					// Change return code and add Content-Range header when skipping
					// is requested
					source.open();
					long fileLen = source.length();
					if (range != null && startFrom >= 0) {
						if (startFrom >= fileLen) {
							res = new Response(HTTP_RANGE_NOT_SATISFIABLE,
									MIME_PLAINTEXT, null);
							res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
						} else {
							if (endAt < 0)
								endAt = fileLen - 1;
							long newLen = fileLen - startFrom;
							if (newLen < 0)
								newLen = 0;
							//						Log.d("Explorer", "start=" + startFrom + ", endAt="
							//								+ endAt + ", newLen=" + newLen);
							final long dataLen = newLen;
							source.moveTo(startFrom);
							//						Log.d("Explorer", "Skipped " + startFrom + " bytes");

							res = new Response(HTTP_PARTIALCONTENT,
									source.getMimeType(), source);
							res.addHeader("Content-length", "" + dataLen);
						}
					} else {
						res = new Response(HTTP_OK, source.getMimeType(), source);
						res.addHeader("Content-Length", "" + fileLen);
					}
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
				res = new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT, null);
			}
			res.addHeader("Content-Disposition",
					"filename=\"" + source.getFileName() + "\"");
			res.addHeader("Accept-Ranges", "bytes"); // Announce that the file
			// server accepts partial
			// content requestes
			return res;
		} catch (Exception e) {
			e.printStackTrace();
			return new Response(HTTP_NOTFOUND, MIME_PLAINTEXT, null);
		}
	}

}
