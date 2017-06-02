package com.chx.newarktest;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.ModbusMaster;
import com.serotonin.modbus4j.exception.ModbusInitException;
import com.serotonin.modbus4j.ip.IpParameters;
import com.serotonin.modbus4j.msg.ModbusRequest;
import com.serotonin.modbus4j.msg.ModbusResponse;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersRequest;
import com.serotonin.modbus4j.msg.WriteRegistersRequest;
import com.serotonin.modbus4j.msg.WriteRegistersResponse;
import com.serotonin.util.queue.ByteQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by chx on 2016/12/9.
 */

public class Communicator {
    private static final String TAG = "Communicator";

    private static Communicator mInstance;
    private Context mContext;
    private MachineStateCallback mCallback;

    //发送获取到的机器状态的广播，界面接受广播同步状态
    public static final String ACTION_STATE = "com.avatar.newark.get.state.finish";
    public static final String FLAG_GET_STATE = "com.avatar.newark.get.machine.state";

    private static final Object syncLock = new Object();

    private static final int CONTROL_INTERVAL = 100;//按钮脉冲信号的间隔时间
    private static final int HANDLE_INTERVAL = 500;//点击按钮之后的延迟同步状态
    private static final int QUERY_INTERVAL = 500;//后台一直同步状态的间隔时间

    private MachineState mCurrentState, mNextState;

    int startAddress = Constant.STATE_433;//最低地址
    int endAddress = Constant.STATE_543;//最高地址

    int unsignedShortMax = 0xffff;

    private ScheduledExecutorService mControlExecutor;
    private ExecutorService mQueryExecutor;
    private ExecutorService mSendRequestExecutor;
    private final int HANDLER_QUERY = 0;
    private final int HANDLER_CALLBACK = 1;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HANDLER_QUERY:
                    queryAllMessages();
                    break;
                case HANDLER_CALLBACK:
//                    mCallback.onComplete((MachineState) msg.obj);
//                    if (msg.obj != null) {
                        mCurrentState = (MachineState) msg.obj;
//                    }
//                    if (mCurrentState == null) {
//                        return;
//                    }
//                    Intent intent = new Intent(ACTION_STATE);
//                    intent.putExtra(FLAG_GET_STATE, mCurrentState);
//                    mContext.sendBroadcast(intent);
                    mCallback.onUpdateMachineState(mCurrentState);
                    break;
                default:
            }
        }
    };
//    private ReadCallback mCallback;
    private ModbusMaster mTcpMaster;
    private Logger mLogger;
    private static final String IP = "192.168.1.9";
    private static final int PORT = 502;
    private static final int SLAVE_ID = 247;
    private short mEmergency;

    private int[] mSingleShortAddress = new int[] {
            Constant.STATE_433,
            Constant.STATE_543
    };

    private Communicator() {
        mControlExecutor  = Executors.newScheduledThreadPool(1);
        mQueryExecutor = Executors.newCachedThreadPool();
        mSendRequestExecutor = Executors.newSingleThreadExecutor();
        mLogger = LoggerFactory.getLogger(Communicator.class);
        ModbusFactory modbusFactory = new ModbusFactory();

        IpParameters params = new IpParameters();
        params.setHost(IP);
        if (502 != PORT) {
            params.setPort(PORT);
        }

        mTcpMaster = modbusFactory.createTcpMaster(params, true);
        mSendRequestExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    mTcpMaster.init();
                    mTcpMaster.setTimeout(100);
                } catch (ModbusInitException e)  {
                    mLogger.error("modbus init error", e);
                }
            }
        });
    }

    public static Communicator getInstance() {
        if (mInstance == null) {
            synchronized (syncLock) {
                if (mInstance == null) {
                    mInstance = new Communicator();
                }
            }
        }
        return mInstance;
    }

//    public void setCallback(ReadCallback callback) {
//        mCallback = callback;
//        queryAllMessages();
//    }

    public void startCommunicate(Context context) {
        setContext(context);
        queryAllMessages();
    }

    public void setCallback(MachineStateCallback callback) {
        mCallback = callback;
    }

    public void reStartCommunicate() {
        mHandler.removeMessages(HANDLER_QUERY);
        mHandler.sendEmptyMessageDelayed(HANDLER_QUERY, HANDLE_INTERVAL);
    }

    public void setContext(Context context) {
        mContext = context;
    }

/**************************************base method start******************************************/
    private byte[] query(int start, int readLength) {
        try {
            //ReadHoldingRegistersRequest的功能码是03
            ModbusRequest request = new ReadHoldingRegistersRequest(SLAVE_ID, start, readLength);
            ModbusResponse response = mTcpMaster.send(request);

            ByteQueue byteQueue = new ByteQueue(readLength * 2 + 3);
            response.write(byteQueue);

            return byteQueue.peek(3, byteQueue.size() - 3);
        } catch (Exception e) {
            mLogger.error("queryAllWarnings error", e);
            return null;
        }
    }

    private void control(final int start, final short[] values) {
        try {
            //WriteRegistersRequest的功能码是10
            WriteRegistersRequest request = new WriteRegistersRequest(SLAVE_ID, start, values);
            WriteRegistersResponse response = (WriteRegistersResponse) mTcpMaster.send(request);

            if (response.isException())
                Log.i(TAG, "Exception response: message=" + response.getExceptionMessage());
            else
                Log.i(TAG, "write success");
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Runnable getControlRunnable(final int start, final short[] data) {
        return new Runnable() {
            @Override
            public void run() {
                control(start, data);
            }
        };
    }

    public Callable<byte[]> getQueryCallable(final int start, final int length) {
        return new Callable<byte[]>() {
            @Override
            public byte[] call() throws Exception {
                return query(start, length);
            }
        };
    }

    /**
     * 发送查询请求
     * @param c 查询动作
     * @return 查询的数据
     */
    public byte[] sendQueryRequest(Callable<byte[]> c) {
        Future<byte[]> f = mQueryExecutor.submit(c);
        byte[] bytes;
        try {
            bytes = f.get();
        } catch (Exception e) {
            e.printStackTrace();
            bytes = null;
        }
        return bytes;
    }

    /**
     * 发送控制请求
     * @param r 控制动作
     * @param r0 清零动作
     */
    public void sendControlRequest(Runnable r, Runnable r0) {
        mControlExecutor.submit(r);
        mControlExecutor.schedule(r0, CONTROL_INTERVAL, TimeUnit.MILLISECONDS);
    }
/**************************************base method end********************************************/

/**************************************control start**********************************************/

    /**
     * 反锁门
     */
    public void lockDoor() {
        sendControlRequest(
                getControlRunnable(Constant.CONTROL_543, new short[] {Constant.VALUE_9_BIT}),
                getControlRunnable(Constant.CONTROL_543, new short[] {0}));
    }

    /**
     * 门压紧
     */
    public void compressDoor() {
        sendControlRequest(
                getControlRunnable(Constant.CONTROL_543, new short[] {Constant.VALUE_8_BIT}),
                getControlRunnable(Constant.CONTROL_543, new short[] {0}));
    }

    /**
     * 门解压
     */
    public void decompressDoor() {
        sendControlRequest(
                getControlRunnable(Constant.CONTROL_543, new short[] {Constant.VALUE_10_BIT}),
                getControlRunnable(Constant.CONTROL_543, new short[] {0}));
    }

    /**
     * 开窗
     */
    public void openWindow() {
        sendControlRequest(
                getControlRunnable(Constant.CONTROL_543, new short[] {Constant.VALUE_12_BIT}),
                getControlRunnable(Constant.CONTROL_543, new short[] {0}));
    }

    /**
     * 关窗
     */
    public void closeWindow() {
        sendControlRequest(
                getControlRunnable(Constant.CONTROL_543, new short[] {Constant.VALUE_11_BIT}),
                getControlRunnable(Constant.CONTROL_543, new short[] {0}));
    }
/**************************************control end************************************************/

/**************************************query start************************************************/
    /**
     * 查询所有状态和警示数据
     */
    public void queryAllMessages() {
        mSendRequestExecutor.submit(new Runnable() {
            @Override
            public void run() {
                mNextState = new MachineState();
//                queryAllStates();
//                queryAllWarnings();
                queryTotalMessage();
                Message msg = Message.obtain();
                msg.what = HANDLER_CALLBACK;
                msg.obj = mNextState;
                mHandler.sendMessage(msg);
            }
        });
        mHandler.sendEmptyMessageDelayed(HANDLER_QUERY, QUERY_INTERVAL);
    }

    /**
     * 一次请求返回所有数据
     */

    private void queryTotalMessage() {
        try {
            byte[] bytes = sendQueryRequest(getQueryCallable(startAddress,
                    endAddress - startAddress + 1));
            if (bytes == null) {
                mNextState = null;
                return;
            }
            for (int i : mSingleShortAddress) {
                byte[] newBytes = new byte[2];
                System.arraycopy(bytes, (i - startAddress) * 2, newBytes, 0, 2);
                fillState(i, newBytes);
            }
        } catch (Exception e) {
            mLogger.error("queryTotalMessage error:\n", e);
            mNextState = null;
        }
    }

/**************************************query end**************************************************/
    private void fillState(int start, byte[] data) {
        int[] ints = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            ints[i] = data[i] & 0xff;
        }
        if (data.length == 2) {
            int value = ints[0] * 0x100 | ints[1];
            mNextState.fillData(start, value);
        }
    }
}
