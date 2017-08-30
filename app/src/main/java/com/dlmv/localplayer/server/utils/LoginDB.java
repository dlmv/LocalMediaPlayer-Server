package com.dlmv.localplayer.server.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;

public class LoginDB {

	public static class PasswordData {
		public final String Share;
		public final String Login;
		public final String Password;
		
		public PasswordData(String s, String l, String p) {
			Share = s;
			Login = l;
			Password = p;	
		}
		
		public String getXml() throws UnsupportedEncodingException {
			return "<share name=\"" + URLEncoder.encode(Share, "UTF-8") + "\"/>";
		}
	}

	private SQLiteDatabase myDb;


	public LoginDB(Context c) {
		Helper helper = new Helper(c);
		myDb = helper.getWritableDatabase();
	}


	private static String TABLE_NAME = "shares";
	private static String DATABASE_NAME = "Shares.db";

	private static final String INSERT = "insert into " + TABLE_NAME + " (share,login,password) values (?,?,?)";
	private static final String DELETE = "delete from " + TABLE_NAME + " where share = ?";
	private static final String SELECT = "select share,login,password from " + TABLE_NAME + " where share = ?";

	public void save(PasswordData b) {
		SQLiteStatement insertStmt = myDb.compileStatement(INSERT);
		insertStmt.bindString(1, b.Share);
		insertStmt.bindString(2, b.Login);
		insertStmt.bindString(3, b.Password);
		insertStmt.execute();
	}

	public ArrayList<PasswordData> listShares() {
		ArrayList<PasswordData> res = new ArrayList<PasswordData>();
		Cursor cursor = myDb.query(TABLE_NAME, new String[] { "id","share","login","password" }, null, null, null, null, "id asc");
		if (cursor.moveToFirst()) {
			do {
				String share = cursor.getString(1);
				String login = cursor.getString(2);
				String password = cursor.getString(3);
				PasswordData b= new PasswordData(share, login, password);
				res.add(b);
			} while (cursor.moveToNext());
		}
		cursor.close();
		return res;
	}
	
	public PasswordData get(String s) {
		Cursor cursor = myDb.rawQuery(SELECT, new String[] {s});
		PasswordData res = null;
		if (cursor.moveToFirst()) {
			String share = cursor.getString(0);
			String login = cursor.getString(1);
			String password = cursor.getString(2);
			res = new PasswordData(share, login, password);
		}
		cursor.close();
		return res;
		
	}
	
	public void delete(String share) {
		SQLiteStatement deleteStmt = myDb.compileStatement(DELETE);
		deleteStmt.bindString(1, share);
		deleteStmt.execute();
	}

	private static class Helper extends SQLiteOpenHelper {

		Helper(Context context) {
			super(context, DATABASE_NAME, null, 1);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + TABLE_NAME + " (id INTEGER PRIMARY KEY, share TEXT NOT NULL UNIQUE, login TEXT NOT NULL, password TEXT NOT NULL)");

		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
			onCreate(db);

		}

	}
}
