package com.chx.newarktest;

import android.content.pm.ActivityInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, MachineStateCallback{

    private TextView mLockDoor, mCompressDoor, mDecompressDoor, mOpenWindow, mCloseWindow;
    private Communicator mCommunicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);
        initView();
        mCommunicator = Communicator.getInstance();
        mCommunicator.startCommunicate(this);
        mCommunicator.setCallback(this);
    }

    public void initView() {
        mLockDoor = (TextView) findViewById(R.id.lock_door);
        mCompressDoor = (TextView) findViewById(R.id.compress_door);
        mDecompressDoor = (TextView) findViewById(R.id.decompress_door);
        mOpenWindow = (TextView) findViewById(R.id.open_window);
        mCloseWindow = (TextView) findViewById(R.id.close_window);
        mLockDoor.setOnClickListener(this);
        mCompressDoor.setOnClickListener(this);
        mDecompressDoor.setOnClickListener(this);
        mOpenWindow.setOnClickListener(this);
        mCloseWindow.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.lock_door:
                onLockDoor();
                break;
            case R.id.compress_door:
                onCompressDoor();
                break;
            case R.id.decompress_door:
                onDecompressDoor();
                break;
            case R.id.open_window:
                onOpenWindow();
                break;
            case R.id.close_window:
                onCloseWindow();
                break;
        }
    }

    public void onLockDoor() {
        mCommunicator.lockDoor();
    }

    public void onCompressDoor() {
        mCommunicator.compressDoor();
    }

    public void onDecompressDoor() {
        mCommunicator.decompressDoor();
    }

    public void onOpenWindow() {
        mCommunicator.openWindow();
    }

    public void onCloseWindow() {
        mCommunicator.closeWindow();
    }

    @Override
    public void onUpdateMachineState(MachineState state) {
        if (state == null) return;
        boolean[] doorState = state.getDoorState();
        if (doorState[2]) {
            mLockDoor.setSelected(false);
            mCompressDoor.setSelected(false);
            mDecompressDoor.setSelected(true);
        } else {
            if (doorState[1]) {
                mLockDoor.setSelected(true);
                mCompressDoor.setSelected(true);
                mDecompressDoor.setSelected(false);
            } else if (doorState[0]) {
                mLockDoor.setSelected(true);
                mCompressDoor.setSelected(false);
                mDecompressDoor.setSelected(false);
            } else {
                mLockDoor.setSelected(false);
                mCompressDoor.setSelected(false);
                mDecompressDoor.setSelected(false);
            }
        }

        boolean[] windowState = state.getWindowState();
        if (windowState[0]) {
            mOpenWindow.setSelected(true);
            mCloseWindow.setSelected(false);
        } else if (windowState[2]){
            mOpenWindow.setSelected(false);
            mCloseWindow.setSelected(true);
        } else {
            mOpenWindow.setSelected(false);
            mCloseWindow.setSelected(false);
        }
    }
}
