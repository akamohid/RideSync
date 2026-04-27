package edu.nust.pdc.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

public final class JsonLines {
    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    private JsonLines() {
    }

    public static void write(BufferedWriter writer, Object message) throws IOException {
        writer.write(GSON.toJson(message));
        writer.write('\n');
        writer.flush();
    }

    public static <T> T read(BufferedReader reader, Class<T> type) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        return GSON.fromJson(line, type);
    }
}