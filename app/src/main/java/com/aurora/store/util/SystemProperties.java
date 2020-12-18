package com.aurora.store.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

// source https://stackoverflow.com/questions/28158175/how-to-read-android-properties-with-java
public class SystemProperties {

    private static String GETPROP_EXECUTABLE_PATH = "/system/bin/getprop";

    public static String read(String propName) {
        Process process = null;
        BufferedReader bufferedReader = null;

        try {
            process = new ProcessBuilder().command(GETPROP_EXECUTABLE_PATH, propName).redirectErrorStream(true).start();
            bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = bufferedReader.readLine();

            if (line == null){
                line = ""; //prop not set
            }

            Log.i("read System Property: " + propName + "=" + line);
            return line;

        } catch (Exception e) {
            Log.e("Failed to read System Property " + propName,e);
            return "";

        } finally{
            if (bufferedReader != null){
                try {
                    bufferedReader.close();
                } catch (IOException e) {}
            }

            if (process != null){
                try {
                    process.destroy();
                } catch (Exception e) {
                    Log.e("Failed to kill " + process.toString(),e);
                }
            }
        }
    }
}