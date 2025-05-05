package com.example.caesartv.data.local;

import androidx.room.TypeConverter;
import com.example.caesartv.domain.model.MediaUrl;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;

public class Converters {
    @TypeConverter
    public static List<MediaUrl> fromString(String value) {
        Type listType = new TypeToken<List<MediaUrl>>(){}.getType();
        return new Gson().fromJson(value, listType);
    }

    @TypeConverter
    public static String fromList(List<MediaUrl> list) {
        return new Gson().toJson(list);
    }
}