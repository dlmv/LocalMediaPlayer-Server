package com.dlmv.localplayer.server.files;

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
	
	SambaFile(String path, Context c) throws SmbException, MalformedURLException {
		SmbFile f = new SmbFile(path);
		myContext = c;
		if (f.getShare() != null) {
			PasswordData pd = ((RootApplication)myContext.getApplicationContext()).LoginDB().get("smb://" + f.getServer() + "/" + f.getShare());
			if (pd != null) {
				NtlmPasswordAuthentication npa = new NtlmPasswordAuthentication("", pd.Login, pd.Password);
				f = new SmbFile(path, npa);
			}
		}
		myFile = f;
		myType = myFile.isDirectory() ? MediaType.DIR : FileUtils.mediaTypeForPath(getPath());
	}
	
	private SambaFile(SmbFile f, Context c) throws SmbException, MalformedURLException {
		myContext = c;
		if (f.getShare() != null) {
			PasswordData pd = ((RootApplication)myContext.getApplicationContext()).LoginDB().get("smb://" + f.getServer() + "/" + f.getShare());
			if (pd != null) {
				NtlmPasswordAuthentication npa = new NtlmPasswordAuthentication("", pd.Login, pd.Password);
				f = new SmbFile(f.getPath(), npa);
			}
		}
		myFile = f;
		myType = myFile.isDirectory() ? MediaType.DIR : FileUtils.mediaTypeForPath(getPath());
	}
	
	SambaFile(String path, Context c, String login, String password) throws SmbException, MalformedURLException {
		Log.d("STORE", "1");
		SmbFile f = new SmbFile(path);
		myContext = c;
		if (f.getShare() != null) {
			Log.d("STORE", "2");
			PasswordData pd = new PasswordData("smb://" + f.getServer() + "/" + f.getShare(), login, password);
			NtlmPasswordAuthentication npa = new NtlmPasswordAuthentication("", pd.Login, pd.Password);
			f = new SmbFile(path, npa);
			try {
				Log.d("STORE", "3");
				f.exists();
				Log.d("STORE", "4");
				((RootApplication)myContext.getApplicationContext()).LoginDB().save(pd);
			} catch (SmbException e) {
				e.printStackTrace();
			}
		}
		
		myFile = f;
		myType = myFile.isDirectory() ? MediaType.DIR : FileUtils.mediaTypeForPath(getPath());
	}

	@Override
	public String getPath() {
		return myFile.getPath();
	}

	@Override
	public boolean readable() throws FileException {
		try {
			return myFile.canRead();
		} catch (SmbAuthException e) {
			throw new FileAuthException(e);
		} catch (SmbException e) {
			throw new FileException(e);
		}
	}
	
	@Override
	public boolean exists() throws FileException {
		try {
			return myFile.exists();
		} catch (SmbAuthException e) {
			throw new FileAuthException(e);
		} catch (SmbException e) {
			throw new FileException(e);
		}
	}

	@Override
	public String getSizeString() throws FileException {
		try {
			return myFile.canRead() && myFile.isFile() ? FileUtils.fileSize(myFile.length()) : "?";
		} catch (SmbAuthException e) {
			throw new FileAuthException(e);
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
		} catch (Exception e) {
			throw new FileException(e);
		}
		return null;
	}
	
	@Override
	public InputStream getInputStream() throws FileException {
		try {
			return new SmbFileInputStream(myFile);
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
		} catch (Exception e) {
			throw new FileException(e);
		}
	}

}
