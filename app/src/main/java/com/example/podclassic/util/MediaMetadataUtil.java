package com.example.podclassic.util;


import com.example.podclassic.object.Music;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.flac.FlacFileReader;
import org.jaudiotagger.audio.generic.AudioFileReader;
import org.jaudiotagger.audio.mp3.MP3FileReader;
import org.jaudiotagger.audio.ogg.OggFileReader;
import org.jaudiotagger.audio.wav.WavFileReader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class MediaMetadataUtil {
    private MediaMetadataUtil() {}

    public static Music getMediaMetadata(String path) {
        return getMediaMetadata(new File(path));
    }

    public static Music getMediaMetadata(File file) {
        AudioFileReader reader = createFileReader(file.getPath());
        if (reader == null) {
            return null;
        }

        Tag tag = null;
        AudioFile audioFile;
        try {
            audioFile = reader.read(file);
            tag = audioFile.getTag();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Music music;

        if (tag == null) {
            music = new Music(file.getName(), null, null, file.getPath(), 0L);
        } else {
            String name = tag.getFirst(FieldKey.TITLE);
            String album = tag.getFirst(FieldKey.ALBUM);
            String singer = tag.getFirst(FieldKey.ARTIST);

            music = new Music(name ,album, singer, file.getPath(), 0L);
        }

        return music;
    }

    public static ArrayList<Lyric> getLyric(File file) {
        AudioFileReader reader = createFileReader(file.getPath());
        if (reader == null) {
            return null;
        }

        Tag tag = null;

        try {
            AudioFile audioFile = reader.read(file);
            tag = audioFile.getTag();
        } catch (Exception e) {
            e.printStackTrace();
        }


        if (tag == null) {
            return null;
        }
        String lyric = tag.getFirst(FieldKey.LYRICS);
        if (lyric != null) {
            return decodeLyric(lyric);
        }
        return null;
    }

    public static ArrayList<Lyric> getLyric(String path) {
        return getLyric(new File(path));
    }

    private static AudioFileReader createFileReader(String path) {
        switch (StringUtil.getSuffix(path).toLowerCase(Locale.ROOT)) {
            case "wav":
                return new WavFileReader();
            case "ogg":
                return new OggFileReader();
            case "mp3":
                return new MP3FileReader();
            case "flac":
                return new FlacFileReader();
        }
        return null;
    }

    public static class Lyric{
        public final int time;
        public final String lyric;

        public Lyric(int time, String lyric) {
            this.time = time;
            this.lyric = lyric;
        }
    }

    private static ArrayList<Lyric> decodeLyric(String lyric) {
        ArrayList<Lyric> list = new ArrayList<>();
        String content;
        String time;
        for (int i = 0; i < lyric.length(); i++ ) {
            if (lyric.charAt(i) == '[') {
                for (int j = i + 1; j < lyric.length(); j++) {
                    if (lyric.charAt(j) == ']') {
                        if((i + 1) < j) {
                            time = lyric.substring(i + 1, j);
                            for (int k = j + 1; k < lyric.length(); k++) {
                                if (lyric.charAt(k) == '[') {
                                    if (j + 1 < k) {
                                        content = lyric.substring(j + 1, k);
                                        Lyric l = buildLyric(time, content);
                                        if (l != null) {
                                            list.add(l);
                                        }
                                    }
                                    i = k;
                                    break;
                                } else if (k == lyric.length() - 1) {
                                    if (j < k) {
                                        content = lyric.substring(j + 1, k + 1);
                                        Lyric l = buildLyric(time, content);
                                        if (l != null) {
                                            list.add(l);
                                        }
                                    }
                                    i = k + 1;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!list.isEmpty()) {
            return list;
        }
        return null;
    }

    private static Lyric buildLyric(String time, String lyric) {
        try {
            int index_1 = time.indexOf(':');
            int index_2 = time.indexOf('.');
            int min = Integer.parseInt(time.substring(0, index_1));
            int sec = Integer.parseInt(time.substring(index_1 + 1, index_2));
            int mil = Integer.parseInt(time.substring(index_2 + 1));
            int t = min * 60 * 1000 + sec * 1000 + mil;
            return new Lyric(t, lyric);
        } catch (Exception ignored) {
            return null;
        }
    }

}
