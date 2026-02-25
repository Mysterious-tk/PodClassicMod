package com.example.podclassic.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;

import com.example.podclassic.base.BaseApplication;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Locale;

public class FileUtil {
    public static final int TYPE_AUDIO = 1;
    public static final int TYPE_VIDEO = 2;
    public static final int TYPE_IMAGE = 3;
    public static final int TYPE_TEXT = 4;

    private FileUtil() {
    }

    public static String getSDCardPath() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            return null;
        }
        Context context = BaseApplication.context;
        StorageManager mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        Class<?> storageVolumeClazz;
        try {
            storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Object result = getVolumeList.invoke(mStorageManager);
            if (result == null) {
                return null;
            }
            final int length = Array.getLength(result);
            if (length > 1) {
                Object storageVolumeElement = Array.get(result, 1);
                return (String) getPath.invoke(storageVolumeElement);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    public static String uriToPath(final Uri uri) {
        Context context = BaseApplication.context;
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = android.content.ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));
                return getDataColumn(context, contentUri, null, null);
            } else if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};
                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        String column = "_data";
        String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public static int getFileType(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".flac") || name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg")) {
            return TYPE_AUDIO;
        } else if (name.endsWith(".jpg") || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".png") || name.endsWith(".heic") || name.endsWith(".heif")) {
            return TYPE_IMAGE;
        } else if (name.endsWith(".3gp") || name.endsWith(".mp4") || name.endsWith(".mkv")) {
            return TYPE_VIDEO;
        } else if (name.endsWith(".txt")) {
            return TYPE_TEXT;
        }
        return 0;
    }

    public static boolean isAudio(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".flac") || name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg");
    }

    public static boolean isFileExist(File file) {
        return file.isFile() && file.exists();
    }

    public static boolean isFileExist(String path) {
        return isFileExist(new File(path));
    }
}
