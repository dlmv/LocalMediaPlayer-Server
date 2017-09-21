package com.dlmv.localplayer.server.files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import com.dlmv.localplayer.server.utils.FileUtils;

class LocalFile extends AbstractFile {
	
	private final File myFile;
	
	LocalFile(String path) {
		myFile = new File(path);
		myType = myFile.isDirectory() ? MediaType.DIR : FileUtils.mediaTypeForPath(getPath());
	}
	
	private LocalFile(File f) {
		myFile = f;
		myType = myFile.isDirectory() ? MediaType.DIR : FileUtils.mediaTypeForPath(getPath());
	}

	@Override
	public String getPath() {
		String path =  myFile.getPath();
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		return path;
	}

	@Override
	public boolean readable() {
		return myFile.canRead();
	}
	
	@Override
	public boolean exists() {
		return myFile.exists();
	}

	@Override
	public String getSizeString() {
		return myFile.canRead() && myFile.isFile() ? FileUtils.fileSize(myFile.length()) : "?";
	}

	@Override
	public List<AbstractFile> children() throws FileException {
		if (myFile.isDirectory()) {
			ArrayList<AbstractFile> res = new ArrayList<AbstractFile>();
			for (File c : myFile.listFiles()) {
				res.add(new LocalFile(c));
			}
			return res;
		}
		return null;
	}

	@Override
	public InputStream getInputStream() throws FileException {
		try {
			return new FileInputStream(myFile);
		} catch (FileNotFoundException e) {
			throw new FileException(e);
		}
	}

	@Override
	public void setAsSource(MediaPlayer mp, Context c) throws FileException {
		try {
			mp.setDataSource(c, Uri.parse(myFile.getPath()));
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
		} catch (Exception e) {
			throw new FileException(e);
		}
	}

}
