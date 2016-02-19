/*
 * Copyright (C) 2015, Zentri, Inc. All Rights Reserved.
 *
 * The Zentri BLE Android Libraries and Zentri BLE example applications are provided free of charge
 * by Zentri. The combined source code, and all derivatives, are licensed by Zentri SOLELY for use
 * with devices manufactured by Zentri, or devices approved by Zentri.
 *
 * Use of this software on any other devices or hardware platforms is strictly prohibited.
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.zentri.zentri_ble_command_demo;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.zentri.zentri_ble_command.ErrorCode;
import com.zentri.zentri_ble_command.GPIODirection;
import com.zentri.zentri_ble_command.GPIOFunction;
import com.zentri.zentri_ble_command.ZentriOSBLEManager;
import com.zentri.zentri_ble_command_demo.R.id;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Pattern;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;


public class MainActivity extends AppCompatActivity
{
    private static final String TAG = "SmartCap";

    private static final String LOC_PERM = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final int BLE_ENABLE_REQ_CODE = 1;
    private static final int LOC_ENABLE_REQ_CODE = 2;

    private static final long SCAN_PERIOD = 30000;
    private static final long CONNECT_TIMEOUT_MS = 10000;

    private static final String PATTERN_MAC_ADDRESS = "(\\p{XDigit}{2}:){5}\\p{XDigit}{2}";

    private SmoothProgressBar mScanProgressBar;
    public ProgressDialog mConnectProgressDialog;
    private DeviceList mDeviceList;
    private Button mScanButton;

    private Handler mHandler;
    private Runnable mStopScanTask;
    private Runnable mConnectTimeoutTask;

    private ZentriOSBLEManager mZentriOSBLEManager;
    private boolean mConnecting = false;
    private boolean mConnected = false;
    private boolean mErrorDialogShowing = false;

    private String mCurrentDeviceName;

    private ServiceConnection mConnection;
    private ZentriOSBLEService mService;
    private boolean mBound = false;

    private LocalBroadcastManager mLocalBroadcastManager;
    private BroadcastReceiver mBroadcastReceiver;
    private IntentFilter mReceiverIntentFilter;

    private Dialog mLocationEnableDialog;
    private Dialog mPermissionRationaleDialog;

    // Connected stuff
    private ToggleButton mModeButton;
    private int mCurrentMode;
    private Button mSendTextButton;
    private EditText mTextToSendBox;
    private TextView mReceivedDataTextBox;
    private ScrollView mScrollView;
    private Button mClearTextButton;
    private ToggleButton mToggleIm;
    private Button mShowIm;
    private ImageView imView;

    private ProgressDialog mDisconnectDialog;
    private boolean mDisconnecting = false;
    private Runnable mDisconnectTimeoutTask;

    private boolean x = false;

    private boolean mRecording = false;
    private String mFileNameLog;
    private byte[] imBytesSplit;
    private byte[] imBytes;
    private int count_bytes;
    private int len_image;
    private int val;
    private boolean header_done = false;

    Calendar timeNow;

    int RSSI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main);

        Toolbar toolbar = new Toolbar(this.getBaseContext());
       // Toolbar toolbar = (Toolbar) findViewById(id.toolbar);
        toolbar.setTitle(R.string.app_name_short);
        setSupportActionBar(toolbar);

        initProgressBar();
        initScanButton();
        initDeviceList();
        initBroadcastManager();
        initServiceConnection();
        initBroadcastReceiver();
        initReceiverIntentFilter();

        startService(new Intent(this, ZentriOSBLEService.class));

        mHandler = new Handler();


        mStopScanTask = new Runnable()
        {
            @Override
            public void run() {
                stopScan();
            }
        };

        mConnectTimeoutTask = new Runnable()
        {
            @Override
            public void run()
            {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dismissDialog(mConnectProgressDialog);
                        showErrorDialog(R.string.con_timeout_message, false);
                        mConnecting = false;
                        mConnected = false;
                        if (mZentriOSBLEManager != null && mZentriOSBLEManager.isConnected()) {
                            mZentriOSBLEManager.disconnect(ZentriOSBLEService.DISABLE_TX_NOTIFY);

                        }
                    }
                });
            }
        };

        mModeButton = (ToggleButton) findViewById(R.id.toggle_str_comm);
        mModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mModeButton.isChecked()) {
                    mCurrentMode = mZentriOSBLEManager.MODE_STREAM;
                } else {
                    mCurrentMode = mZentriOSBLEManager.MODE_COMMAND_REMOTE;
                }
                x = mZentriOSBLEManager.setMode(mCurrentMode);
                Log.d(TAG, "Mode set to: " + mCurrentMode);
                Log.d(TAG, "Truconnect Manager returned: " + x);
            }
        });


        mTextToSendBox = (EditText) findViewById(R.id.editText);
        mSendTextButton = (Button) findViewById(R.id.button_send);
        mSendTextButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                String data = mTextToSendBox.getText().toString();
                String dataToSend = "0";
                if (mCurrentMode == mZentriOSBLEManager.MODE_STREAM && data != null && !data.isEmpty())
                {
                    if (data.equals("R")) { // READ RTC
                        dataToSend = "*R#";
                        mZentriOSBLEManager.writeData(dataToSend);
                        Log.d(TAG, "Sent: " + dataToSend);
                    }
                    else if (data.equals("T")) { // SET RTC
                        timeNow = Calendar.getInstance();
                        int second = timeNow.get(Calendar.SECOND);
                        int minute = timeNow.get(Calendar.MINUTE);
                        int hour = timeNow.get(Calendar.HOUR_OF_DAY);
                        int dayOfWeek = timeNow.get(Calendar.DAY_OF_WEEK);
                        int dayOfMonth = timeNow.get(Calendar.DAY_OF_MONTH);
                        int month = timeNow.get(Calendar.MONTH);
                        int year = timeNow.get(Calendar.YEAR);
                        //String rtc_data = Integer.toString(second) + Integer.toString(minute) + Integer.toString(hour) + Integer.toString(dayOfWeek) + Integer.toString(dayOfMonth) + Integer.toString(month) + Integer.toString(year);
                        String rtc_data = String.format("%02d%02d%02d%d%02d%02d%04d",second,minute,hour,dayOfWeek,dayOfMonth,month+1,year);
                        dataToSend = "*T" + rtc_data + "#";
                        mZentriOSBLEManager.writeData(dataToSend);
                        Log.d(TAG, "Sent: " + dataToSend);
                    }
                    else if (data.equals("C")) { // TAKE IMAGE
                        dataToSend = "*C#";
                        mZentriOSBLEManager.writeData(dataToSend);
                        Log.d(TAG, "Sent: " + dataToSend);
                    }
                    else if (data.startsWith("*")) { // Own command
                        dataToSend = data;
                        mZentriOSBLEManager.writeData(dataToSend);
                        Log.d(TAG, "Sent: " + dataToSend);
                    }
                    else {
                        dataToSend = data;
                        mZentriOSBLEManager.writeData(dataToSend);
                        Log.d(TAG, "Sent: " + dataToSend);
                    }
                }

                if (mCurrentMode == mZentriOSBLEManager.MODE_COMMAND_REMOTE) {
                    if (data.isEmpty()) {
                        //mTruconnectManager.GPIOGetUsage();
                    }
                    else if (Integer.parseInt(data) == 1) {
                        mZentriOSBLEManager.GPIOFunctionSet(10, GPIOFunction.CONN_GPIO);
                        mZentriOSBLEManager.GPIOFunctionSet(11, GPIOFunction.STDIO);
                        mZentriOSBLEManager.GPIODirectionSet(11, GPIODirection.HIGH_IMPEDANCE);
                    }
                    else if (Integer.parseInt(data) == 0) {
                        mZentriOSBLEManager.save();
                        mZentriOSBLEManager.reboot();
                    }
                }

                mTextToSendBox.setText("");//clear input after send
            }
        });

        mClearTextButton = (Button) findViewById(id.clear_button);
        mClearTextButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                 clearReceivedTextBox();
            }
        });

        mToggleIm = (ToggleButton) findViewById(R.id.toggle_im);
        mToggleIm.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mToggleIm.isChecked())
                {
                    // TODO start logging
                    doStartRecording();
                    startRecording();
                    count_bytes = 0;
                    header_done = false;
                }
                else
                {
                    // TODO stop logging
                    doStopRecording();
                    stopRecording();
                }
                Log.d(TAG, "Image recording: " + mRecording);
            }
        });

        mShowIm = (Button) findViewById(R.id.show_im);
        mShowIm.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                /*imView = (ImageView) findViewById(R.id.imageView);
                Bitmap bmp = BitmapFactory.decodeFile(mFileNameLog);
                imView.setImageBitmap(bmp);*/
                imView = (ImageView) findViewById(R.id.imageView);
                Bitmap bmp = BitmapFactory.decodeByteArray(imBytes, 0, len_image);
                imView.setImageBitmap(bmp);
                Log.d(TAG, "Showing Image");
                Log.d(TAG, "File path: " + mFileNameLog);
            }
        });

        /*mReceivedDataTextBox = (TextView) findViewById(R.id.receivedDataBox);
        mReceivedDataTextBox.setMovementMethod(new ScrollingMovementMethod());*/
        mReceivedDataTextBox = (TextView) findViewById(R.id.receivedDataBox);
        mScrollView = (ScrollView) findViewById(R.id.scroll_view);

        GUISetCommandMode();//set up gui for command mode initially

        mDisconnectTimeoutTask = new Runnable()
        {
            @Override
            public void run()
            {
                dismissProgressDialog();
                showErrorDialog(R.string.error, true);//R.string.discon_err_message);
            }
        };



    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle item selection
        switch (item.getItemId())
        {
            case R.id.action_about:
                openAboutDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        mDeviceList.clear();
        mConnected = false;
        mConnecting = false;

        Intent intent = new Intent(this, ZentriOSBLEService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        mLocalBroadcastManager.registerReceiver(mBroadcastReceiver, mReceiverIntentFilter);
        //mZentriOSBLEManager.connect(mZentriOSBLEManager.getDeviceName(),true);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
    }

    @Override
    protected void onStop()
    {
        mHandler.removeCallbacks(mStopScanTask);

        //ensure dialogs are closed
        dismissDialog(mConnectProgressDialog);
        dismissDialog(mLocationEnableDialog);
        dismissDialog(mPermissionRationaleDialog);

        if (mBound)
        {
            mLocalBroadcastManager.unregisterReceiver(mBroadcastReceiver);
            unbindService(mConnection);
            mBound = false;
        }

        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        stopService(new Intent(this, ZentriOSBLEService.class));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == BLE_ENABLE_REQ_CODE)
        {
            mService.initTruconnectManager();//try again
            if (mZentriOSBLEManager.isInitialised())
            {
                if (requirementsMet())
                {
                    startScan();
                }
            }
            else
            {
                showErrorDialog(R.string.init_fail_msg, true);
            }
        }
    }

    @Override //look into this
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        switch (requestCode)
        {
            case LOC_ENABLE_REQ_CODE:
            {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    if (requirementsMet())
                    {
                        startScan();
                    }
                }
                else
                {
                    //show unrecoverable error dialog
                    showErrorDialog(R.string.error_permission_denied, true);
                }
            }
        }
    }

    private void initProgressBar()
    {
        mScanProgressBar = (SmoothProgressBar) findViewById(id.update_button);
        mScanProgressBar.setVisibility(View.VISIBLE);
    }

    private void initScanButton()
    {
        mScanButton = (Button) findViewById(id.button_scan);
        mScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDeviceList.clear();
                startScan();
            }
        });
    }

    private void initDeviceList()
    {
        ListView deviceListView = (ListView) findViewById(R.id.listView);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.listitem, R.id.textView);

        initialiseListviewListener(deviceListView);
        mDeviceList = new DeviceList(adapter, deviceListView);
    }

    private void initServiceConnection()
    {
        mConnection = new ServiceConnection()
        {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service)
            {
                ZentriOSBLEService.LocalBinder binder = (ZentriOSBLEService.LocalBinder) service;
                mService = binder.getService();
                mBound = true;

                mZentriOSBLEManager = mService.getManager();

                //if requirements not met, action will already be taken
                if (requirementsMet())
                {
                    startScan();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName arg0)
            {
                mBound = false;
            }
        };
    }

    private void initBroadcastReceiver()
    {
        mBroadcastReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                // Get extra data included in the Intent
                String action = intent.getAction();

                switch (action)
                {
                    case ZentriOSBLEService.ACTION_SCAN_RESULT:
                        String name = ZentriOSBLEService.getData(intent);

                        //dont show devices with no name (mac addresses)
                        if (name != null && !Pattern.matches(PATTERN_MAC_ADDRESS, name))
                        {
                            addDeviceToList(name);
                        }
                        break;

                    case ZentriOSBLEService.ACTION_CONNECTED:
                        String deviceName = ZentriOSBLEService.getData(intent);

                        mConnected = true;
                        mHandler.removeCallbacks(mConnectTimeoutTask);//cancel timeout timer
                        dismissDialog(mConnectProgressDialog);
                        showToast("Connected to " + deviceName, Toast.LENGTH_SHORT);
                        Log.d(TAG, "Connected to " + deviceName);

                        startDeviceInfoActivity();
                        break;

                    case ZentriOSBLEService.ACTION_DISCONNECTED:
                        mConnected = false;
                        break;

                    case ZentriOSBLEService.ACTION_ERROR:
                        ErrorCode errorCode = ZentriOSBLEService.getErrorCode(intent);
                        //handle errors
                        if (errorCode == ErrorCode.CONNECT_FAILED)
                        {
                            if (!mConnected && mConnecting)
                            {
                                mConnecting = false;//allow another attempt to connect
                                dismissDialog(mConnectProgressDialog);
                            }
                            else
                            {
                                mConnected = false;
                            }

                            showErrorDialog(R.string.con_err_message, false);
                        }
                        break;
                }
            }
        };
    }

    public void initBroadcastManager()
    {
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
    }

    public void initReceiverIntentFilter()
    {
        mReceiverIntentFilter = new IntentFilter();
        mReceiverIntentFilter.addAction(ZentriOSBLEService.ACTION_SCAN_RESULT);
        mReceiverIntentFilter.addAction(ZentriOSBLEService.ACTION_CONNECTED);
        mReceiverIntentFilter.addAction(ZentriOSBLEService.ACTION_DISCONNECTED);
        mReceiverIntentFilter.addAction(ZentriOSBLEService.ACTION_ERROR);
    }

    private void startBLEEnableIntent()
    {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, BLE_ENABLE_REQ_CODE);
    }

    private void initialiseListviewListener(ListView listView)
    {
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                mCurrentDeviceName = mDeviceList.get(position);

                if (!mConnecting) {
                    mConnecting = true;

                    stopScan();
                    Log.d(TAG, "Connecting to BLE device " + mCurrentDeviceName);
                    mZentriOSBLEManager.connect(mCurrentDeviceName);

                    showConnectingDialog(view.getContext());

                    mHandler.postDelayed(mConnectTimeoutTask, CONNECT_TIMEOUT_MS);
                }
            }
        });
    }

    private void startScan()
    {
        if (mZentriOSBLEManager != null)
        {
            runOnUiThread(new Runnable()
              {
                  @Override
                  public void run()
                  {
                      mZentriOSBLEManager.startScan();
                  }
              });
            startProgressBar();
            disableScanButton();
            mHandler.postDelayed(mStopScanTask, SCAN_PERIOD);
        }
    }

    private void stopScan()
    {
        if (mZentriOSBLEManager != null && mZentriOSBLEManager.stopScan())
        {
            stopProgressBar();
            enableScanButton();
        }
    }

    private void showConnectingDialog(final Context context)
    {
        if (!isFinishing())
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    final ProgressDialog dialog = new ProgressDialog(MainActivity.this);
                    String title = getString(R.string.progress_title);
                    String msg = getString(R.string.progress_message);
                    dialog.setIndeterminate(true);//Dont know how long connection could take.....
                    dialog.setCancelable(true);

                    mConnectProgressDialog = dialog.show(context, title, msg);
                    mConnectProgressDialog.setCancelable(true);
                    mConnectProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener()
                    {
                        @Override
                        public void onCancel(DialogInterface dialogInterface)
                        {
                            dialogInterface.dismiss();
                        }
                    });
                }
            });
        }
    }

    private void startLocationEnableIntent()
    {
        Log.d(TAG, "Directing user to enable location services");
        Intent enableBtIntent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivityForResult(enableBtIntent, LOC_ENABLE_REQ_CODE);
    }

    private void showLocationEnableDialog()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLocationEnableDialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.loc_enable_title)
                        .setMessage(R.string.loc_enable_msg)
                        .setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startLocationEnableIntent();
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                showErrorDialog(R.string.error_loc_disabled, true);
                            }
                        }).create();
                mLocationEnableDialog.show();
                Resources res = getResources();
                Util.setTitleColour(res, mLocationEnableDialog, R.color.zentri_orange);
                Util.setDividerColour(res, mLocationEnableDialog, R.color.transparent);
            }
        });
    }

    private boolean requestPermissions()
    {
        boolean result = true;

        if (ContextCompat.checkSelfPermission(MainActivity.this, LOC_PERM)
                != PackageManager.PERMISSION_GRANTED)
        {
            result = false;

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, LOC_PERM))
            {

                // Show an explanation to the user
                showPermissionsRationaleDialog();
            }
            else
            {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{LOC_PERM},
                        LOC_ENABLE_REQ_CODE);
            }
        }

        return result;
    }

    /**
     * Checks if requirements for this app to run are met.
     * @return true if requirements to run are met
     */
    private boolean requirementsMet()
    {
        boolean reqMet = false;

        if (!mZentriOSBLEManager.isInitialised())
        {
            startBLEEnableIntent();
        }
        else if (!requestPermissions())
        {
        }
        else if (!Util.isPreMarshmallow() && !Util.isLocationEnabled(this))
        {
            showLocationEnableDialog();
        }
        else
        {
            reqMet = true;
        }

        return reqMet;
    }

    private void showPermissionsRationaleDialog()
    {
        mPermissionRationaleDialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.permission_rationale_title)
                .setMessage(R.string.permission_rationale_msg)
                .setPositiveButton(R.string.enable, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{LOC_PERM},
                                LOC_ENABLE_REQ_CODE);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        showErrorDialog(R.string.error_permission_denied, true);
                    }
                }).create();

        mPermissionRationaleDialog.show();
        Resources res = getResources();
        Util.setTitleColour(res, mPermissionRationaleDialog, R.color.zentri_orange);
        Util.setDividerColour(res, mPermissionRationaleDialog, R.color.transparent);
    }

    private void startDeviceInfoActivity()
    {
        startActivity(new Intent(getApplicationContext(), DeviceInfoActivity.class));
    }

    private void startProgressBar()
    {
        updateProgressBar(true);
    }

    private void stopProgressBar()
    {
        updateProgressBar(false);
    }

    private void enableScanButton()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mScanButton.setEnabled(true);
            }
        });
    }

    private void disableScanButton()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                mScanButton.setEnabled(false);
            }
        });
    }

    private void showToast(final String msg, final int duration)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                Toast.makeText(getApplicationContext(), msg, duration).show();
            }
        });
    }

    private void showErrorDialog(final int msgID, final boolean finishOnClose)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                Util.showErrorDialog(MainActivity.this, R.string.error, msgID, finishOnClose);
            }
        });
    }

    private void dismissDialog(final Dialog dialog)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (dialog != null)
                {
                    dialog.dismiss();
                }
            }
        });
    }

    //Only adds to the list if not already in it
    private void addDeviceToList(final String name)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                mDeviceList.add(name);
            }
        });
    }

    private void updateProgressBar(final boolean start)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (start)
                {
                    mScanProgressBar.progressiveStart();
                }
                else
                {
                    mScanProgressBar.progressiveStop();
                }
            }
        });
    }

    private void openAboutDialog()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                Util.makeAboutDialog(MainActivity.this);
            }
        });
    }



    //set up gui elements for command mode operation
    private void GUISetCommandMode()
    {
        //mSendTextButton.setEnabled(false);
        //mTextToSendBox.setVisibility(View.INVISIBLE);
    }

    //set up gui elements for command mode operation
    private void GUISetStreamMode()
    {
        mSendTextButton.setEnabled(true);
        mTextToSendBox.setVisibility(View.VISIBLE);
    }

    private void updateReceivedTextBox(String newData)
    {
        mReceivedDataTextBox.append(newData);
    }

    private void clearReceivedTextBox()
    {
        mReceivedDataTextBox.setText("");
    }

    private void doStartRecording() {
        File sdCard = Environment.getExternalStorageDirectory();

        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String currentDateTimeString = format.format(new Date());
        String fileName = sdCard.getAbsolutePath() + "/ams001_" + currentDateTimeString + ".log";

        this.setFileNameLog(fileName);
        this.startRecording();

        showToast("Logging Started", Toast.LENGTH_SHORT);
    }

    private void doStopRecording() {
        this.stopRecording();
        showToast("Logging Stopped", Toast.LENGTH_SHORT);
    }

    public void setFileNameLog( String fileNameLog ) {
        mFileNameLog = fileNameLog;
    }

    public void startRecording() {
        mRecording = true;
    }

    public void stopRecording() {
        mRecording = false;
    }

    private boolean writeLog(String buffer) {
        String state = Environment.getExternalStorageState();
        File logFile = new File ( mFileNameLog );

        if (Environment.MEDIA_MOUNTED.equals(state)) {

            try {
                FileOutputStream f = new FileOutputStream( logFile, true );

                PrintWriter pw = new PrintWriter(f);
                pw.print( buffer );
                pw.flush();
                pw.close();

                f.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            this.stopRecording();
            return false;
        } else {
            this.stopRecording();
            return false;
        }

        return true;
    }

    private void saveImage(byte[] data) {
        File sdCard = Environment.getExternalStorageDirectory();
        String fileName = sdCard.getAbsolutePath() + "/ams001_image.jpg";
        File testimage = new File(fileName);

        if (testimage.exists()) {
            testimage.delete();
        }

        try {
            FileOutputStream fos = new FileOutputStream(testimage.getPath());
            fos.write(data);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void dismissProgressDialog()
    {
        if (mDisconnectDialog != null)
        {
            mDisconnectDialog.dismiss();
        }
    }
}