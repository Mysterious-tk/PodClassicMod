package com.example.podclassic.object;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.example.podclassic.R;
import com.example.podclassic.base.BaseApplication;
import com.example.podclassic.storage.SaveMusics;
import com.example.podclassic.util.MediaUtil;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;



public class MusicList {
    public String name;
    public int id;

    public int size;
    public int type = 0;

    public static final int TYPE_SINGER = 1;
    public static final int TYPE_ALBUM = 2;

    private ArrayList<Music> list;
    public ArrayList<Music> getList() {
        if (list == null) {
            if (type == TYPE_SINGER) {
                list = MediaUtil.INSTANCE.searchMusic(MediaUtil.NAME + " like? or " + MediaUtil.SINGER + "=?", new String[] {"%" + name + "%", name});
            } else if (type == TYPE_ALBUM) {
                list = MediaUtil.INSTANCE.searchMusic(MediaUtil.ALBUM + "=?", new String[] {name});
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

    public Bitmap getImage() {
        String uriAlbums = "content://media/external/audio/albums";
        String[] projection = new String[]{"album_art"};
        Cursor cursor = BaseApplication.getContext().getContentResolver().query(Uri.parse(uriAlbums + "/" + id), projection, null, null, null);
        String image = null;


        if (cursor.getCount() > 0) {
            cursor.moveToNext();
            image = cursor.getString(0);
            cursor.close();
        }

        if (image == null) {
            return null;
        }
        return BitmapFactory.decodeFile(image);
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
        return toString().hashCode();
    }

}
