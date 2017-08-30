package com.dlmv.localplayer.server.files;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import android.content.Context;
import android.media.MediaPlayer;

import com.dlmv.localplayer.server.utils.FileUtils;

class FtpFile extends AbstractFile {// TODO later
	
	private final FTPFile myFile;
	private final String myPath;
	private final FTPClient myClient;
	
	FtpFile(FTPFile f, String path, FTPClient c) {
		myFile = f;
		myPath = path;
		myClient = c;
		myType = myFile.isDirectory() ? MediaType.DIR : FileUtils.mediaTypeForPath(getPath());
	}

	@Override
	public String getPath() {
		return myPath;
	}

	@Override
	public boolean readable() {
		return myFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION);
	}
	
	@Override
	public boolean exists() {
		return true;
	}

	@Override
	public String getSizeString() {
		return /*myFile.canRead() &&*/ myFile.isFile() ? FileUtils.fileSize(myFile.getSize()) : "?";
	}

	@Override
	public List<AbstractFile> children() throws FileException {
		if (myFile.isDirectory()) {
			ArrayList<AbstractFile> res = new ArrayList<AbstractFile>();
			try {
				for (FTPFile c : myClient.listFiles(getPath())) {
					res.add(new FtpFile(c, getPath() + "/" + c.getName(), myClient));
				}
			} catch (IOException e) {
				throw new FileException(e);
			}
			return res;
		}
		return null;
	}

	@Override
	public InputStream getInputStream() throws FileException {
		try {
			return myClient.retrieveFileStream(getPath());
		} catch (Exception e) {
			throw new FileException(e);
		}
	}

	@Override
	public void setAsSource(MediaPlayer mp, Context c) throws FileException {
		try {
			
		} catch (Exception e) {
			throw new FileException(e);
		}
	}

}
