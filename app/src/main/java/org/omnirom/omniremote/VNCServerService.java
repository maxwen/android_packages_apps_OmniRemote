package org.omnirom.omniremote;

import android.app.Service;
import android.content.Intent;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.Nullable;

public class VNCServerService extends Service {
    private static final String TAG = Utils.TAG;
    public static final String ACTION_START = "org.omnirom.omniremote.ACTION_START";
    public static final String ACTION_STOP = "org.omnirom.omniremote.ACTION_STOP";
    public static final String ACTION_ERROR = "org.omnirom.omniremote.ACTION_ERROR";
    public static final String ACTION_STATUS = "org.omnirom.omniremote.ACTION_STATUS";
    public static final String EXTRA_ERROR_START_FAILED = "start_failed";
    public static final String EXTRA_ERROR_STOP_FAILED = "stop_failed";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_STATUS_STARTED = "started";
    public static final String EXTRA_STATUS_STOPPED = "stopped";
    private FileObserver mFileObserver;

    private PowerManager.WakeLock mWakeLock;
    private PowerManager.WakeLock mScreenLock;
    private Handler mHandler = new Handler();
    private boolean mWorkInProgress;
    private boolean mServiceMode;

    // this is needed cause start server is asynchron
    // se we fire the start and need to wait until the
    // server is creating its pid file to issue that
    // it has been started. So there is a FileObserver
    // waiting for the pid file to arrive
    //
    // since we dont want to wait forever this watchdog
    // runs for max 5s after issuing the start and if
    // the pid file was not seen until then mark the
    // start as failed
    private Runnable mWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "You got bitten by the watch dog");
            mFileObserver.stopWatching();
            mWorkInProgress = false;
            if (!Utils.isRunning(VNCServerService.this)) {
                sendErrorBrodcast(EXTRA_ERROR_START_FAILED);
                if (mServiceMode) {
                    stopSelf();
                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":wakelock");
        mScreenLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG + ":screenLock");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        try {
            mScreenLock.release();
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand " + intent.getAction());
        try {
            mWakeLock.acquire();

            if (ACTION_START.equals(intent.getAction())) {
                // check upfront
                if (Utils.isRunning(this) || mWorkInProgress) {
                    return START_NOT_STICKY;
                }

                String password = intent.getStringExtra("password");
                List<String> parameter = new ArrayList<>();
                if (intent.hasExtra("parameter")) {
                    String[] paramString = intent.getStringArrayExtra("parameter");
                    parameter.addAll(Arrays.asList(paramString));
                }
                mServiceMode = true;
                doStartServer(password, parameter);
            }

            if (ACTION_STOP.equals(intent.getAction())) {
                // check upfront
                if (!Utils.isRunning(this)) {
                    stopSelf();
                    return START_NOT_STICKY;
                }
                if (mWorkInProgress) {
                    return START_NOT_STICKY;
                }
                doStopServer();
                // always stop service
                stopSelf();
                return START_NOT_STICKY;
            }
        } finally {
            mWakeLock.release();
        }
        return START_STICKY;
    }

    private void sendErrorBrodcast(String error) {
        Intent errorIntent = new Intent(ACTION_ERROR);
        errorIntent.putExtra(EXTRA_ERROR, error);
        sendBroadcast(errorIntent);
    }

    private void sendStatusBrodcast(String status) {
        Intent errorIntent = new Intent(ACTION_STATUS);
        errorIntent.putExtra(EXTRA_STATUS, status);
        sendBroadcast(errorIntent);
    }

    private void doStopServer() {
        try {
            Log.i(TAG, "stopServer");

            mWorkInProgress = true;
            // always release does not matter what happens else
            Log.i(TAG, "Release screen lock");
            try {
                mScreenLock.release();
            } catch (Exception e) {
                // ignore
            }
            mHandler.removeCallbacks(mWatchdogRunnable);
            // stop is synchron - so no need for any handling of that here
            if (!Utils.stopServer(this)) {
                sendErrorBrodcast(EXTRA_ERROR_STOP_FAILED);
            } else {
                sendStatusBrodcast(EXTRA_STATUS_STOPPED);
            }
        } catch (Exception e) {
            sendErrorBrodcast(EXTRA_ERROR_STOP_FAILED);
        } finally {
            mWorkInProgress = false;
        }
    }

    private void doStartServer(String password, List<String> parameter) {
        Log.i(TAG, "startServer " + password + " " + parameter);

        mWorkInProgress = true;
        mHandler.removeCallbacks(mWatchdogRunnable);
        try {
            mFileObserver = new FileObserver(Utils.getStateDir(VNCServerService.this).getAbsolutePath()) {
                @Override
                public void onEvent(int i, @Nullable String s) {
                    if (i == FileObserver.CREATE && s.equals(
                            Utils.getPidPath(VNCServerService.this).getName())) {
                        Log.i(TAG, "Start catch by FileObserver " + s);
                        sendStatusBrodcast(EXTRA_STATUS_STARTED);
                        Log.i(TAG, "Aquire screen lock");
                        mScreenLock.acquire();
                        mFileObserver.stopWatching();
                        mHandler.removeCallbacks(mWatchdogRunnable);
                        mWorkInProgress = false;
                    }
                }
            };
            mFileObserver.startWatching();
            if (!Utils.startServer(VNCServerService.this, password, parameter)) {
                mFileObserver.stopWatching();
                sendErrorBrodcast(EXTRA_ERROR_START_FAILED);
                mWorkInProgress = false;
                if (mServiceMode) {
                    stopSelf();
                }
                return;
            }
            if (!Utils.isRunning(VNCServerService.this)) {
                // wait max 5s
                mHandler.postDelayed(mWatchdogRunnable, 5000);
            } else {
                // we where faster then the FileObserver
                mFileObserver.stopWatching();
                Log.i(TAG, "Start catch");
                sendStatusBrodcast(EXTRA_STATUS_STARTED);
                Log.i(TAG, "Aquire screen lock");
                mScreenLock.acquire();
                mWorkInProgress = false;
            }
        } catch (Exception e) {
            mFileObserver.stopWatching();
            sendErrorBrodcast(EXTRA_ERROR_START_FAILED);
            mWorkInProgress = false;
            if (mServiceMode) {
                stopSelf();
            }
        }
    }
}
