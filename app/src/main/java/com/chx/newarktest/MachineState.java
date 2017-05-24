package com.chx.newarktest;

import java.io.Serializable;

/**
 * Created by chx on 2016/12/16.
 */

public class MachineState implements Serializable {
    private boolean mIsDoorCompressed, mIsDoorDecompressed, mIsDoorLocked,
            mIsWindowOpened, mIsWindowClosed, mIsWindowMiddle;

    public void fillData(int start, int value) {
        switch (start) {
            case Constant.STATE_433:
                if ((value & Constant.VALUE_9_BIT) != 0) {
                    mIsDoorCompressed = true;
                }
                if ((value & Constant.VALUE_12_BIT) != 0) {
                    mIsDoorDecompressed = true;
                }
                if ((value & Constant.VALUE_13_BIT) != 0) {
                    mIsDoorLocked = true;
                }
                break;
            case Constant.STATE_543:
                if ((value & Constant.VALUE_13_BIT) != 0) {
                    mIsWindowClosed = true;
                }
                if ((value & Constant.VALUE_14_BIT) != 0) {
                    mIsWindowMiddle = true;
                }
                if ((value & Constant.VALUE_15_BIT) != 0) {
                    mIsWindowOpened = true;
                }
            default:
        }
    }

    public boolean[] getDoorState() {
        return new boolean[]{mIsDoorLocked, mIsDoorCompressed, mIsDoorDecompressed};
    }

    public boolean[] getWindowState() {
        return new boolean[]{mIsWindowOpened, mIsWindowMiddle, mIsWindowClosed};
    }
}
