package com.dlmv.localplayer.server.files;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import com.dlmv.localplayer.server.utils.FileUtils;
import com.dlmv.localplayer.server.utils.LoginDB;
import com.dlmv.localplayer.server.utils.LoginDB.PasswordData;
import com.dlmv.localplayer.server.utils.RootApplication;
import com.test.smbstreamer.Streamer;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

class SambaFile extends AbstractFile {
	
	private final SmbFile myFile;
	private final Context myContext;
	
	SambaFile(String path, Context c) throws FileException {
		try {
			SmbFile f = new SmbFile(path);
			myContext = c;
			if (f.getShare() != null) {
				PasswordData pd = ((RootApplication) myContext.getApplicationContext()).LoginDB().get(shareString(f));
				if (pd != null) {
					NtlmPasswordAuthentication npa = new NtlmPasswordAuthentication("", pd.Login, pd.Password);
					f = new SmbFile(path, npa);
				}
			}
			myFile = f;
			myType = myFile.isDirectory() ? MediaType.DIR : FileUtils.mediaTypeForPath(getPath());
		} catch (SmbAuthException e) {
			throw new FileAuthException(shareString(), e);
		} catch (Exception e) {
			throw new FileException(e);
		}
	}
	
	private SambaFile(SmbFile f, Context c) throws FileException {
		try {
			myContext = c;
			if (f.getShare() != null) {
				PasswordData pd = ((RootApplication) myContext.getApplicationContext()).LoginDB().get(shareString(f));
				if (pd != null) {
					NtlmPasswordAuthentication npa = new NtlmPasswordAuthentication("", pd.Login, pd.Password);
					f = new SmbFile(f.getPath(), npa);
				}
			}
			myFile = f;
			myType = myFile.isDirectory() ? MediaType.DIR : FileUtils.mediaTypeForPath(getPath());
		} catch (SmbAuthException e) {
			throw new FileAuthException(shareString(), e);
		} catch (Exception e) {
			throw new FileException(e);
		}
	}
	
	SambaFile(String path, Context c, String login, String password) throws FileException {
		try {
			Log.d("STORE", "1");
			SmbFile f = new SmbFile(path);
			myContext = c;
			if (f.getShare() != null) {
				Log.d("STORE", "2");
				PasswordData pd = new PasswordData(shareString(f), login, password);
				NtlmPasswordAuthentication npa = new NtlmPasswordAuthentication("", pd.Login, pd.Password);
				f = new SmbFile(path, npa);
				try {
					Log.d("STORE", "3");
					f.exists();
					Log.d("STORE", "4");
					((RootApplication) myContext.getApplicationContext()).LoginDB().save(pd);
				} catch (SmbException e) {
					e.printStackTrace();
				}
			}

			myFile = f;
			myType = myFile.isDirectory() ? MediaType.DIR : FileUtils.mediaTypeForPath(getPath());
		} catch (SmbAuthException e) {
			throw new FileAuthException(shareString(), e);
		} catch (Exception e) {
			throw new FileException(e);
		}
	}

	@Override
	public String getPath() {
		return myFile.getPath();
	}

	static String shareString(SmbFile f) {
		return "smb://" + f.getServer() + "/" + f.getShare();
	}

	String shareString() {
		return shareString(myFile);
	}

	@Override
	public boolean readable() throws FileException {
		try {
			return myFile.canRead();
		} catch (SmbAuthException e) {
			throw new FileAuthException(shareString(), e);
		} catch (SmbException e) {
			throw new FileException(e);
		}
	}
	
	@Override
	public boolean exists() throws FileException {
		try {
			return myFile.exists();
		} catch (SmbAuthException e) {
			throw new FileAuthException(shareString(), e);
		} catch (Exception e) {
			throw new FileException(e);
		}
	}

	@Override
	public String getSizeString() throws FileException {
		try {
			return myFile.canRead() && myFile.isFile() ? FileUtils.fileSize(myFile.length()) : "?";
		} catch (SmbAuthException e) {
			throw new FileAuthException(shareString(), e);
		} catch (SmbException e) {
			throw new FileException(e);
		}
	}

	@Override
	public List<AbstractFile> children() throws FileException {
		try {
			if (myFile.isDirectory()) {
				ArrayList<AbstractFile> res = new ArrayList<AbstractFile>();
				for (SmbFile c : myFile.listFiles()) {
					res.add(new SambaFile(c, myContext));
				}
				return res;
			}
		} catch (SmbAuthException e) {
			throw new FileAuthException(shareString(), e);
		} catch (Exception e) {
			throw new FileException(e);
		}
		return null;
	}
	
	@Override
	public InputStream getInputStream() throws FileException {
		try {
			return new SmbFileInputStream(myFile);
		} catch (SmbAuthException e) {
			throw new FileAuthException(shareString(), e);
		} catch (Exception e) {
			throw new FileException(e);
		}
	}
	
	@Override
	public void setAsSource(MediaPlayer mp, Context c) throws FileException {
		try {
			Streamer s = Streamer.getInstance();
			String hash = "/" + FileUtils.md5(getPath()) + FileUtils.getExtension(getPath());
			s.setStreamSrc(myFile, hash);
			mp.setDataSource(c, Uri.parse(Streamer.URL + hash));
			mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
		} catch (SmbAuthException e) {
			throw new FileAuthException(shareString(), e);
		} catch (Exception e) {
			throw new FileException(e);
		}
	}

	@Override
	public void test() throws FileException {
		try {
			if (myFile.isFile()) {
				getInputStream().close();
			} else {
				myFile.listFiles();
			}
		} catch (SmbAuthException e) {
			throw new FileAuthException(shareString(), e);
		} catch (Exception e) {
			throw new FileException(e);
		}
	}

}
