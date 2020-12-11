package com.example.podclassic.object;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.example.podclassic.R;
import com.example.podclassic.base.BaseApplication;
import com.example.podclassic.storage.SaveMusics;
import com.example.podclassic.util.MediaUtil;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.ArrayList;



public class MusicList {
    public String name;
    public Long id;
    public String artist;

    public int size;
    public int type = 0;

    public static final int TYPE_SINGER = 1;
    public static final int TYPE_ALBUM = 2;

    private ArrayList<Music> list;
    public ArrayList<Music> getList() {
        if (list == null) {
            if (type == TYPE_SINGER) {
                list = MediaUtil.INSTANCE.getArtistMusic(name);
            } else if (type == TYPE_ALBUM) {
                list = MediaUtil.INSTANCE.getAlbumMusic(name);
            }
        }
        return list;
    }

    public void setList(ArrayList<Music> list) {
        this.list = list;
    }

    public MusicList() { }

    public MusicList(String name, ArrayList<Music> list) {
        this.list = list;
        this.name = name;
    }

    public int getSize() {
        if (list != null && list.size() != 0) {
            return list.size();
        }
        return size;
    }


    private Bitmap bitmap = null;
    public Bitmap getImage() {
        if (bitmap != null && !bitmap.isRecycled()) {
            return bitmap;
        }
        ContentResolver contentResolver = BaseApplication.getContext().getContentResolver();
        Uri artworkUri = Uri.parse("content://media/external/audio/albumart");


        Uri uri = ContentUris.withAppendedId(artworkUri, id);

        try (InputStream inputStream = contentResolver.openInputStream(uri)) {
            bitmap = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeStream(inputStream), 512, 512, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        }
    }

    @NotNull
    @Override
    public String toString() {
        return name + size;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof MusicList)) {
            return false;
        }
        MusicList musicList = (MusicList) obj;
        try {
            return name.equals(musicList.name);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

}
