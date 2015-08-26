package com.sleepdatacollector.tommzy.sleepdatacollector.services;
/**
 * SleepService.java
 * Sam Fitness
 *
 * @version 1.0.0
 *
 * @author Jake Haas
 * @author Evan Safford
 * @author Nate Ford
 * @author Haley Andrews
 *
 * Copyright (c) 2014, 2015. Wellness-App-MQP. All Rights Reserved.
 *
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY
 * KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
 * PARTICULAR PURPOSE.
 */

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.sleepdatacollector.tommzy.sleepdatacollector.PowerConnectionReceiver;
import com.sleepdatacollector.tommzy.sleepdatacollector.Utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Timer;


public class SleepService extends Service {
    public static final String TAG = "SleepSerivce";

    float lightIntensity;


    // Raw Sensor Data
    private int audioAmplitude;

    Timer timer;
    boolean initWrite=true;

    // Calibrated Sensor Data (defaults set if left uncalibrated)
    private float calibratedLight = 19;
    private int calibratedAmplitude = 300;
    private int calibratedSleepHour = 8;
    private int calibratedWakeHour = 12;

    // Calibration
    private final int CALIBRATE_TIME = 10;
    private final int NOISE_MARGIN = 200;
    private final int LIGHT_MARGIN = 15;
    private CountDownTimer calibrateTimer;
    private float avgBy = 0;
    private int threshold = 3;
    private int wakeup = 0;

    // Tracking Statuses
    private boolean isTracking = true;
    private boolean isAsleep = false;
    private int numWakeups = 0;
    private double stationary=0.00;

    // Final Sleep Times
    private String fallAsleepTime = "";
    private String wakeUpTime = "";
    private int sleepHour;
    private int sleepMin;
    private String sleepAmPm;
    private int wakeHour;
    private int wakeMin;
    private String wakeAmPm;
    private float totalDuration = 0.0F;

    SimpleDateFormat dateFormat;
    private int date;
    private Date now;


    private final BroadcastReceiver recieveFromSleepService = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            Calendar calendar = new GregorianCalendar();
            calendar.setTime(new Date());

            SimpleDateFormat dateFormat = new SimpleDateFormat("MMddyyyy", Locale.US);
            date = Integer.valueOf(dateFormat.format(calendar.getTime()));

            if (action.equals("SensorData")) {
                Bundle extras = intent.getExtras();

                try {
                    String maxAmplitudeIn = extras.getString("maxAmplitude");
                    String lightIntensityIn = extras.getString("lightIntensity");

                    audioAmplitude = Integer.parseInt(maxAmplitudeIn);
                    lightIntensity = Float.parseFloat(lightIntensityIn);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                writeToFile(audioAmplitude, lightIntensity);
            }

            if(action.equalsIgnoreCase("Activity_Message")){
                Bundle extra = intent.getExtras();
                String activityType = extra.getString("ActivityType");
                Log.i(TAG, activityType);

                if((!activityType.equalsIgnoreCase("still"))){
                    stationary=54.45;
                }else{
                    stationary=0.00;
                }
            }
            checkSleepStatus();
        }
    };

    private void writeToFile(int audioAmplitude, float lightIntensity) {
        Log.i(TAG,"WriteToFile");
        String baseDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        String fileName = "AnalysisData.csv";
        String filePath = baseDir + File.separator + fileName;
        Log.i(TAG,"File Path"+filePath);
        Log.i(TAG,"Audio: "+audioAmplitude);
        Log.i(TAG,"Light: "+lightIntensity);
        File f = new File(filePath );
        CSVWriter writer = null;
        CSVReader reader = null;
        FileWriter mFileWriter = null;
        FileReader mFileReader = null;
        //File not Exist
        if(!f.exists()){
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // File exist
        if(f.exists() && !f.isDirectory()){
            try {
                mFileWriter = new FileWriter(filePath , true);
            } catch (IOException e) {
                e.printStackTrace();
            }
            writer = new CSVWriter(mFileWriter);
        }
        else {
            try {
                writer = new CSVWriter(new FileWriter(filePath));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        double soundValue=0;
        double lightValue=0;
        if(audioAmplitude>500){
            if(audioAmplitude>1000){
                soundValue=0;
            }else {
                soundValue = (1 - (audioAmplitude - 500) / 500) * 34.84;
            }
        }else{
            soundValue=34.84;
        }

        if(lightIntensity>75){
            if(lightIntensity>150){
                lightValue=0;
            }else{
                lightValue=(1-(lightIntensity-75)/75)*4.15;
            }
        }else{
            lightValue=4.15;
        }

        Log.i(TAG,"soundValue: "+soundValue);
        Log.i(TAG,"lightValue: "+lightValue);
//        String[] data = {"Ship Name","Scientist Name", "...",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").formatter.format(date)});
        String[] data = {new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime())
                ,Integer.toString(audioAmplitude)
                ,Double.toString(soundValue)
                ,Float.toString(lightIntensity)
                ,Double.toString(lightValue)};
        if(f.toString().length()==0){
            writer.writeNext(new String[]{"Time", "Sound","Sound Value" ,"Light", "Light Value"});
            initWrite=false;
        }else {
            writer.writeNext(data);
        }
        try {
//            writer.flush();
            writer.close();
            MediaScannerConnection.scanFile(this, new String[]{f.getAbsolutePath()}, null, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            mFileReader = new FileReader(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        reader = new CSVReader(mFileReader);
        try {
            Log.i(TAG,Integer.toString(reader.readAll().size()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "SleepService onCreate");

        isTracking = true;

        // Create the calibrateTimer
        timer = new Timer();
        now = new Date();
        dateFormat = new SimpleDateFormat("MMddyyyy", Locale.US);
        IntentFilter intentFilter = new IntentFilter("SensorData");
        registerReceiver(recieveFromSleepService, intentFilter);
        calibrateSensors();
        startSleepTracking();
    }

    /**
     * startSleepTracking()
     * Begins tracking sleep
     */
    private void startSleepTracking() {
        isTracking = true;

        Log.d("SleepFragment", "Starting sleep service");

        totalDuration = 0.0F;

//            calibrateSensors();
        checkSleepStatus();
    }


    /**
     * stopSleepTracking()
     * Stops tracking sleep
     */
    private void stopSleepTracking() {
//        getActivity().stopService(new Intent(getActivity(), SleepService.class));//TODO ask activity to shutdown the service
//        Log.d("SleepFragment", "Stopping sleep service");

        isTracking = false;
    }

    /**
     * calibrateSensors()
     * Calibrates the light/sound levels by getting avgs (get values every 1sec for 10sec period) and
     * adding a preset margin to allow for movement/daybreak
     */
    private void calibrateSensors() {

//        calibratedLight = 0;
//        calibratedAmplitude = 0;
        avgBy = -1; //first light value is 0 and needs to be disregarded

        calibrateTimer = new CountDownTimer((CALIBRATE_TIME * 1000), 1000) {
            public void onTick(long millisUntilFinished) {
                //add up total values for averaging
                calibratedLight += lightIntensity;
                calibratedAmplitude += audioAmplitude;
                avgBy++;
            }

            public void onFinish() {
                //calculate avgs
                calibratedLight = Math.round(calibratedLight / avgBy);
                calibratedAmplitude = calibratedAmplitude / Math.round(avgBy);

                //after averages are calculated, add in some margin of noise/light
                calibratedLight += LIGHT_MARGIN;
                calibratedAmplitude += NOISE_MARGIN;

                Log.d("SleepMonitor", "Calibrated sensors");

                checkSleepStatus();

                calibrateTimer.cancel();
            }
        }.start();

    }


    /**
     * checkSleepStatus()
     * Checks time and light/sound levels to see if the user falls within all sleep thresholds,
     * sets isAsleep equal to true or false and sets fallAsleepTime and wakeUpTime
     */
    //TODO Add equation here
    private void checkSleepStatus() {

        //Time check first
        if (!SleepHourCheck()) {
//            Log.d("StopSleepTracking:", "Outside hour range.");
            stopSleepTracking();
        }else {
            calibrateSensors();
            // Check all conditions to see if user fell asleep
            if (!isAsleep && SleepHourCheck() && SleepLightCheck() && SleepAudioCheck()) {
                Log.d("SleepMonitor", "Fell Asleep:" + fallAsleepTime);

                if(getSleepSum()>75) {
                    isAsleep = true;
                    fallAsleepTime = getTime('S');

                    Log.i(TAG, "Light value Changed: "
                            + "Sound: "
                            + audioAmplitude
                            + "  "
                            + "lightIntensity: "
                            + Float.toString(lightIntensity));
                }else{
                    isAsleep=false;
                }
            }

            // Check to see if user woke up
            if (isAsleep && (!SleepHourCheck() || !SleepLightCheck() || !SleepAudioCheck())) {
                Log.d("SleepMonitor", "Woke Up:" + fallAsleepTime);
                isAsleep = false;
                wakeUpTime = getTime('W');
                wakeup++;

                if(wakeup >= threshold){
                    numWakeups++;
                    wakeup = 0;
                }

                if(numWakeups > 12){
                    numWakeups = threshold - 2;
                    wakeup = 0;
                    threshold += 2;
                }

                totalDuration = getDuration();
                Log.d("getDuration", "Adding " + Float.toString(getDuration()));

                Utils.todaysSleepHours += Float.valueOf(totalDuration);
                //TODO Ã¿ÌìÇåÁã
            }
            Log.i(TAG,"TodaysSleepHourse"+Utils.todaysSleepHours);
        }
    }


    /**
     * getTime(char set)
     * Gets the time in HH:MM:SS format, takes in a char if the sleep/wake variables need to be reset
     *
     * @param set flag if the sleep/wake times need to be updated
     * @return current time in HH:MM:SS format
     */
    private String getTime(char set) {
        Log.d("getTime", "Getting current time");
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR);
        int minute = c.get(Calendar.MINUTE);

        //12:00 AM is treated as hour 0
        if (hour == 0) {
            hour = 12;
        }

        Log.d("CurrentTime", Integer.toString(hour) + ":" + Integer.toString(minute) + getAmPm());

        //if the new sleep time is gotten, set it globally
        if (set == 'S') {
            sleepHour = hour;
            sleepMin = minute;
            sleepAmPm = getAmPm();
            Log.d("SetSleepTime", Integer.toString(sleepHour) + ":" + Integer.toString(sleepMin) + sleepAmPm);
        }

        //if the new wake time is gotten, set it globally
        if (set == 'W') {
            wakeHour = hour;
            wakeMin = minute;
            wakeAmPm = getAmPm();
            Log.d("SetWakeTime", Integer.toString(wakeHour) + ":" + Integer.toString(wakeMin) + wakeAmPm);
        }

        return Integer.toString(hour) + ":" + Integer.toString(minute) + getAmPm();
    }



    private String getHoursTodayFormatted() {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(new Date());

        //       SimpleDateFormat dateFormat = new SimpleDateFormat("MMddyyyy", Locale.US);
        //       int date = Integer.valueOf(dateFormat.format(calendar.getTime()));

        float todaysHours =  Utils.todaysSleepHours;

        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        return String.valueOf(decimalFormat.format(todaysHours));
    }

    /**
     * getAmPm()
     * Checks to see if time of day is AM or PM
     *
     * @return string containing either "AM" or "PM"
     */
    private String getAmPm() {
        Calendar c = Calendar.getInstance();
        int am_pm = c.get(Calendar.AM_PM);
        String amPm;

        if (am_pm == 0)
            amPm = "AM";
        else
            amPm = "PM";

        return amPm;
    }


    /**
     * SleepHourCheck()
     * Checks to see if the current hour is between the valid sleeping hours
     *
     * @return true if hour is valid, false if hour is not valid
     */
    private boolean SleepHourCheck() {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR);
        String amPm = getAmPm();

        if (hour == 0) {
            hour = 12;
        }

        if (hour == 12 && amPm.equals("PM")) {
            return false;
        }

        if (hour == 12 && amPm.equals("AM")) {
            return true;
        }

        if ((hour >= calibratedSleepHour && amPm.equals("PM")) || (hour < calibratedWakeHour && amPm.equals("AM"))) {
            return true;
        }
        return false;
    }


    /**
     * SleepLightCheck()
     * Checks to see if the light level is below the valid sleeping light level
     *
     * @return true if light level is valid, false if light level is not valid
     */
    double sleeplight=0.00;
    private boolean SleepLightCheck() {
        //chect current calibrated light value is normal or not.
        if(calibratedLight>10){
            return false;
        }

        //check lightIntensity is going down or not?
        //using current lightIntensity : using calibrated intensity
        if (lightIntensity < calibratedLight) {
            sleeplight = (1 - (lightIntensity / calibratedLight)) * 4.15;//get weighted sleeplight value
            if (sleeplight < 2.5) {
                return false;
            }
            return true;
        } else {
            sleeplight = (1 - (calibratedLight / 3))*4.15;//get weighted sleeplight value from model data pattern
        }
        sleeplight=0;
        return false;
    }

    /**
     * SleepAudioCheck()
     * Checks to see if the sound level is below the valid sleeping sound level
     *
     * @return true if sound level is valid, false if sound level is not valid
     */
    double audioValue=0.00;
    private boolean SleepAudioCheck() {
        //chect current calibrated light value is normal or not.
        if(calibratedAmplitude>2000){
            return false;
        }

        if (audioAmplitude < calibratedAmplitude) {
            audioValue = (1 - (audioAmplitude / calibratedAmplitude)) * (34.84);
            if (audioValue < 30) {
                return false;
            }
            Log.i(TAG, "calibratedAmtitude: " + calibratedAmplitude);
            return true;
        }else{
            audioValue=(1-(calibratedAmplitude/200))*34.84;
        }

        audioValue=0;
        return false;
    }


    /**
     * getDuration()
     * Calculates the duration of time slept based on the time the user fell asleep, woke up, and previous
     * duration during the night
     *
     * @return duration of sleep for a night
     */
    private float getDuration() {
        Log.d("getDuration", "Getting current duration...");
        int newHours;
        int newMins;
        double duration;

        //both AM or both PM: simply subtract
        if ((sleepAmPm.equals("PM") && wakeAmPm.equals("PM")) || (sleepAmPm.equals("AM") && wakeAmPm.equals("AM"))) {
            newHours = Math.abs(sleepHour - wakeHour);
            newMins = Math.abs(sleepMin - wakeMin);
//            Log.d("getDuration1", "newHours: " + Integer.toString(newHours));
//            Log.d("getDuration1", "newMins: " + Integer.toString(newMins));
        }
        //crossed over midnight: have to take day change into account
        else {
            //take into account waking up in the midnight hour
            if(wakeHour == 12 && wakeAmPm.equals("AM")){
                wakeHour = 0;
            }

            newHours = (12 - sleepHour) + wakeHour - 1;
            newMins = (60 - sleepMin) + wakeMin;
            //           Log.d("getDuration2", "newHours: " + Integer.toString(newHours));
            //           Log.d("getDuration2", "newMins: " + Integer.toString(newMins));
        }

        //check for full hour
        if(newHours == 1 && sleepMin > wakeMin){
            newHours--;
            newMins = (60 - sleepMin) + wakeMin;
//            Log.d("getDuration3", "newHours: " + Integer.toString(newHours));
//            Log.d("getDuration3", "newMins: " + Integer.toString(newMins));

        }

        //add appropriate minutes
        if (newMins >= 60) {
            newMins -= 60;
            newHours += 1;
//            Log.d("getDuration4", "newHours: " + Integer.toString(newHours));
//            Log.d("getDuration4", "newMins: " + Integer.toString(newMins));
        }

//        Log.d("getDuration", "newHours: " + Integer.toString(newHours));
//        Log.d("getDuration", "newMins: " + Integer.toString(newMins));
        //convert to hours and partial hours
        duration = newHours + (newMins / 60.0);

//      Log.d("getDuration", "duration: " + Float.toString((float)duration));

        return (float)duration;
    }

    double getSleepSum(){
        PowerConnectionReceiver powerConnectionReceiver = new PowerConnectionReceiver();
        double charged = 0.00;
        if(PowerConnectionReceiver.getIsChargePluged()){
            charged=4.69;
        }

//        audio 34.84
//        light 4.15
//        phone charing 4.69
//        stationary 54.45
//        total  98.13
        Log.d("SleepValueSum",String.valueOf(audioValue+sleeplight+charged+stationary));
        return audioValue+sleeplight+charged+stationary;
    }


}
