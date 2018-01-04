package app.pankaj.fingerprintscan.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/**
 * Created by pankajpathak on 20/12/17.
 */

public class SqliteUtil
{
    private static final String DB_NAME="mydb";
    private static final String TABLE_REGISTRATION="registration";
    private static final String REGISTRATION_EMAIL="EMAILID";
    private static final String REGISTRATION_NAME="NAME";
    private static final String REGISTRATION_IMAGE1="IMAGE1";
    private static final String REGISTRATION_IMAGE2="IMAGE2";
    private static final String REGISTRATION_IMAGE3="IMAGE3";
    private static SQLiteDatabase sqLiteDatabase;

    public static void createTables(Context context)
    {
        initializeSqlite(context).execSQL("create table if not exists "+TABLE_REGISTRATION+
                "("+
                    REGISTRATION_EMAIL +" VARCHAR(50) PRIMARY KEY,"+
                    REGISTRATION_NAME +" VARCHAR(50),"+
                    REGISTRATION_IMAGE1 +" VARCHAR(50),"+
                    REGISTRATION_IMAGE2 +" VARCHAR(50),"+
                    REGISTRATION_IMAGE3 +" VARCHAR(50)"+
                ")");
    }

    public synchronized static int registerUser(Context context,String email,String name,String IMAGE1)
    {
        String selectQuery="select * from "+TABLE_REGISTRATION+" where "+
                REGISTRATION_EMAIL+"='"+email+"'";
        if(initializeSqlite(context).rawQuery(selectQuery,null).getCount()>0)
            return -1;
        ContentValues cv=new ContentValues();
        cv.put(REGISTRATION_EMAIL,email);
        cv.put(REGISTRATION_NAME,name);
        cv.put(REGISTRATION_IMAGE1,IMAGE1);
        return (int)initializeSqlite(context).insert(TABLE_REGISTRATION,null,cv);
    }

    private static SQLiteDatabase initializeSqlite(Context context)
    {
        if(sqLiteDatabase==null)
            sqLiteDatabase=context.openOrCreateDatabase(DB_NAME,Context.MODE_PRIVATE,null);
        return sqLiteDatabase;
    }
}
