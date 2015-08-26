package com.sleepdatacollector.tommzy.sleepdatacollector.services;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.sleepdatacollector.tommzy.sleepdatacollector.Utils;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;


public class SleepDetectService extends Service {
    private static final int SAMPLE_RATE = 30000;
    public static final String TAG = "SleepDetectService";

    MediaRecorder mRecorder;
    SensorManager sensorMgr = null;
    Sensor lightSensor = null;
    float lightIntensity;
    Timer timer;


    final Handler handler = new Handler();
    Timer writeTimer = new Timer();

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "SleepService onCreate");

        // Create the calibrateTimer
        timer = new Timer();
        // Set up light sensor
        sensorMgr = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        lightSensor = sensorMgr.getDefaultSensor(Sensor.TYPE_LIGHT);

        // Set up media recorder
        mRecorder = new MediaRecorder();
        try {
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }catch (IllegalStateException e){
            Log.i(TAG, "setup Audio Source failed");
        }

        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(Utils.getAudioSampleFilePath(this));
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);


    }

    SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float max = event.values[0];
            lightIntensity = max > lightIntensity? max:lightIntensity;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    @Override
    public void onDestroy() {
        Log.d("SleepService", "onDestroy");


        // Stop the calibrateTimer
        timer.cancel();

        // Stop the audio recorder
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startid) {
        Log.d("SleepService", "onStartCommand");

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e("SleepService", "MediaRecorder prepare() failed");
        }

        // Start the audio recorder
        mRecorder.start();

        // Start the light sensor
        sensorMgr.registerListener(sensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);

        // Start sampling the sensors
        sampleSensors();

        // Don't want to auto restart the service
        return START_NOT_STICKY;
    }


    /**
     * sampleSensors()
     * Gets light and sound data from sensors at a fixed rate and sends broadcast with the values in
     * it (reveiced in the SleepFragment)
     */
    private void sampleSensors() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Intent i = new Intent("SensorData");
                i.putExtra("maxAmplitude", Integer.toString(mRecorder.getMaxAmplitude()));
                i.putExtra("lightIntensity", Float.toString(lightIntensity));
                sendBroadcast(i);

            }
        }, 0, SAMPLE_RATE);
    }
}
