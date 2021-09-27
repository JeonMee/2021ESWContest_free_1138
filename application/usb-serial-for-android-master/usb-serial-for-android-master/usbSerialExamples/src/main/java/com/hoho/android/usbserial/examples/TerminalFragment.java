package com.hoho.android.usbserial.examples;

import android.animation.ObjectAnimator;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.json.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.PUT;


public class TerminalFragment extends Fragment implements SerialInputOutputManager.Listener, SurfaceHolder.Callback, View.OnClickListener{

    //USB Serial 통신 관련 변수
    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private int deviceId, portNum, baudRate;
    private boolean withIoManager;
    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;
    private ControlLines controlLines;
    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private boolean connected = false;
    private String temp1, temp2, temp3;

    //카메라 관련 변수
    private SurfaceView mCameraView;
    private SurfaceHolder mCameraHolder;
    private Camera mCamera;

    //텍스트, 레이아웃 변수
    private TextView receiveText;
    private FrameLayout window;
    private ImageView image;
    private ImageView fireExtinguisher;
    private FrameLayout detailWindow;
    private TextView detailDist;
    private TextView detailPressure;
    private Button outButton;
    private double D;
    private TextView XfText;
    private TextView YfText;
    private TextView ZfText;
    private TextView xf0Text;
    private TextView yf0Text;
    private TextView zf0Text;
    private double xf1;
    private double yf1;
    private double zf1;

    //각도센서 관련 변수
    private SensorManager mSensorManager = null;
    private UserSensorListener userSensorListener;
    private Sensor mGyroscopeSensor = null;
    private Sensor mAccelerometer = null;
    private float[] mGyroValues = new float[3];
    private float[] mAccValues = new float[3];
    private double mAccPitch, mAccYaw, yaw, yawDegree, roll, rollDegree, pitchDegree;
    private float a = 0.2f;
    private static final float NS2S = 1.0f/1000000000.0f;
    private double pitch = 0;
    private double timestamp;
    private double dt;
    private double temp;
    private boolean gyroRunning;
    private boolean accRunning;

    //네비게이션 관련 변수
    private String[] AX = {null, null, null, null};
    private String[] AY = {null, null, null, null};
    private String[] DX = {null, null, null, null};
    private String[] DY = {null, null, null, null};
    private FrameLayout leftArrow;
    private ImageView leftArrowImage;
    private TextView leftArrowText;
    private FrameLayout rightArrow;
    private ImageView rightArrowImage;
    private TextView rightArrowText;
    private ImageView goStraight;
    private ImageView arrive;
    private int passedNodesCount = 1;
    private double zf0_1 = 1.5;



    Retrofit retrofit = new Retrofit.Builder()
            .baseUrl("http://192.168.50.175:5000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build();
    RetrofitAPI retrofitAPI = retrofit.create(RetrofitAPI.class);

    public interface RetrofitAPI {
        @PUT("Position")
        @Headers("Content-Type: application/json")
        Call<Post> putData(@Body JSONObject position);
    }


    public TerminalFragment() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                    connect();
                }
            }
        };
        mainLooper = new Handler(Looper.getMainLooper());
    }



//----------------------------------------- 생명주기 -------------------------------------------------

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        assert getArguments() != null;
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
        withIoManager = getArguments().getBoolean("withIoManager");
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));

        if(usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
            mainLooper.post(this::connect);
    }


//----------------------------------------- 기본 기능 -------------------------------------------------------


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);

        mCameraView = view.findViewById(R.id.cameraView);

        receiveText = view.findViewById(R.id.receive_text);
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText));
        window = view.findViewById(R.id.window);
        image = view.findViewById(R.id.image);
        fireExtinguisher = view.findViewById(R.id.fireExtinguisher);
        detailWindow = view.findViewById(R.id.detailWindow);
        detailDist = view.findViewById(R.id.detailDist);
        detailPressure = view.findViewById(R.id.detailPressure);
        outButton = view.findViewById(R.id.outButton);
        XfText = view.findViewById(R.id.XfText);
        YfText = view.findViewById(R.id.YfText);
        ZfText = view.findViewById(R.id.ZfText);
        xf0Text = view.findViewById(R.id.xf0Text);
        yf0Text = view.findViewById(R.id.yf0Text);
        zf0Text = view.findViewById(R.id.zf0Text);

        leftArrow = view.findViewById(R.id.leftArrow);
        leftArrowImage = view.findViewById(R.id.leftArrowImage);
        leftArrowText = view.findViewById(R.id.leftArrowText);
        rightArrow = view.findViewById(R.id.rightArrow);
        rightArrowImage = view.findViewById(R.id.rightArrowImage);
        rightArrowText = view.findViewById(R.id.rightArrowText);
        goStraight = view.findViewById(R.id.goStraight);
        arrive = view.findViewById(R.id.arrive);

        mSensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        userSensorListener = new UserSensorListener();
        mGyroscopeSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        window.setVisibility(View.INVISIBLE);
        image.setVisibility(View.INVISIBLE);
        fireExtinguisher.setVisibility(View.INVISIBLE);
        detailWindow.setVisibility(View.INVISIBLE);
        leftArrow.setVisibility(View.INVISIBLE);
        leftArrowImage.setVisibility(View.INVISIBLE);
        leftArrowText.setVisibility(View.INVISIBLE);
        rightArrow.setVisibility(View.INVISIBLE);
        rightArrowImage.setVisibility(View.INVISIBLE);
        rightArrowText.setVisibility(View.INVISIBLE);
        goStraight.setVisibility(View.INVISIBLE);
        arrive.setVisibility(View.INVISIBLE);

        controlLines = new ControlLines(view);

        try {
            init();
        }catch(Exception e) {
            Toast.makeText(getContext(), "카메라 실행 실패", Toast.LENGTH_SHORT).show();
        }

        window.setOnClickListener(this);
        outButton.setOnClickListener(this);

        try {
            mSensorManager.registerListener(userSensorListener, mGyroscopeSensor, SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener(userSensorListener, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        }catch(Exception e) {
            Toast.makeText(getContext(), "기울기 센서 실패", Toast.LENGTH_SHORT).show();
        }

        return view;
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.window: {
                detailWindow.setVisibility(View.VISIBLE);
                break;
            }
            case R.id.outButton: {
                detailWindow.setVisibility(View.INVISIBLE);
                break;
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if( id == R.id.send_break) {
            if(!connected) {
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    usbSerialPort.setBreak(true);
                    Thread.sleep(100); // should show progress bar instead of blocking UI thread
                    usbSerialPort.setBreak(false);
                    SpannableStringBuilder spn = new SpannableStringBuilder();
                    spn.append("send <break>\n");
                    spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    receiveText.append(spn);
                } catch(UnsupportedOperationException ignored) {
                    Toast.makeText(getActivity(), "BREAK not supported", Toast.LENGTH_SHORT).show();
                } catch(Exception e) {
                    Toast.makeText(getActivity(), "BREAK failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRunError(Exception e) {
        mainLooper.post(() -> {
            status("connection lost: " + e.getMessage());
            disconnect();
        });
    }

    private void connect() {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) requireActivity().getSystemService(Context.USB_SERVICE);
        for(UsbDevice v : usbManager.getDeviceList().values())
            if(v.getDeviceId() == deviceId)
                device = v;
        if(device == null) {
            status("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if(driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if(driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
            if(withIoManager) {
                usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
                usbIoManager.start();
            }
            status("connected");
            connected = true;
            controlLines.start();
        } catch (Exception e) {
            status("connection failed: " + e.getMessage());
            disconnect();
        }
    }

    private void disconnect() {
        connected = false;
        controlLines.stop();
        mSensorManager.unregisterListener(userSensorListener);
        if(usbIoManager != null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
        }
        usbIoManager = null;
        try {
            usbSerialPort.close();
        } catch (IOException ignored) {}
        usbSerialPort = null;
    }

    void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str);
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.setText(spn);
    }

    @Override
    public void onNewData(byte[] data) {
        mainLooper.post(() -> {
            receive(data);
        });
    }

    private void receive(byte[] data) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        if(data.length > 0)
            spn.append(dumpHexString(data));
        if(!spn.toString().equals(".")) {
            receiveText.setText(spn);
            char[] charD = spn.toString().toCharArray();
            char[] charD1 = new char[spn.toString().length()];
            char[] charD2 = new char[spn.toString().length()];
            char[] charD3 = new char[spn.toString().length()];
            for(int i=0; i<spn.toString().length(); i++) {
                if(charD[i] == 'a') {
                    for(int j=0; j<spn.toString().length(); j++) {
                        if(charD[i+j+1] == 'b') break;
                        charD1[j] = charD[i+j+1];
                    }
                }
                else if(charD[i] == 'b') {
                    for(int j=0; j<spn.toString().length(); j++) {
                        if(charD[i+j+1] == 'c') break;
                        charD2[j] = charD[i+j+1];
                    }
                }
                else if(charD[i] == 'c') {
                    for(int j=0; j<spn.toString().length(); j++) {
                        if(charD[i+j+1] == 'd') break;
                        charD3[j] = charD[i+j+1];
                    }
                }
            }
            String strD1 = new String(charD1);
            String strD2 = new String(charD2);
            String strD3 = new String(charD3);

            moveWindow(strD1, strD2, strD3);

        }
    }



//---------------------------------------------- 카메라 ----------------------------------------------------



    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        try{
            if (mCamera == null) {
                assert false;
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            }
        } catch (IOException e) {
            Toast.makeText(getContext(), "카메라 실행 실패" + e, Toast.LENGTH_SHORT).show();
        }
    }

    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        //View가 존재하지 않을 때
        if (mCameraHolder.getSurface()==null) {
            return;
        }
        //작업을 위해 잠시 멈춘다
        try {
            mCamera.stopPreview();
        } catch(Exception e) {
            //에러가 나더라도 무시한다.
        }
        //카메라 설정을 다시 한다.
        Camera.Parameters parameters = mCamera.getParameters();
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        mCamera.setParameters(parameters);
        //View를 재생성한다.
        try {
            mCamera.setPreviewDisplay(mCameraHolder);
            mCamera.startPreview();
        } catch (Exception e) {
        }
    }

    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }


//---------------------------------------------- 함수 ------------------------------------------------------


    //16진법으로 받은 데이터를 String 으로 변환
    public String dumpHexString(byte[] array) {
        return dumpHexString(array, 0, array.length);
    }
    public String dumpHexString(byte[] array, int offset, int length) {
        StringBuilder result = new StringBuilder();

        byte[] line = new byte[length]; //String 길이 설정
        if (offset + length - offset >= 0)
            System.arraycopy(array, offset, line, offset, offset + length - offset);
        for(int i = 0; i<length; i++) {
            if(line[i] > ' ' && line[i] < '~') {
                result.append(new String(line, i, 1));
            } else {
                result.append(" ");
            }
        }
        if(result.toString().toCharArray()[0] == 'a' && result.toString().toCharArray()[length-1] == 'd') {
            return result.toString();
        }
        else if(result.toString().toCharArray()[0] == 'a' && result.toString().toCharArray()[length-1] != 'd') {
            temp1 = result.toString();
            return ".";
        }
        else if(result.toString().toCharArray()[0] != 'a' && result.toString().toCharArray()[length-1] == 'd') {
            temp2 = result.toString();
            temp3 = temp1.concat(temp2);
            return temp3;
        }
        else return ".";
    }



    class ControlLines {
        private static final int refreshInterval = 200; // msec
        private final Runnable runnable;
        ControlLines(View view) {
            runnable = this::run;
        }
        private void run() {
            if (!connected)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getControlLines();
                mainLooper.postDelayed(runnable, refreshInterval);
            } catch (IOException e) {
                status("getControlLines() failed: " + e.getMessage() + " -> stopped control line refresh");
            }
        }
        void start() {
            if (!connected)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getSupportedControlLines();
                run();
            } catch (IOException e) {
                Toast.makeText(getActivity(), "getSupportedControlLines() failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        void stop() {
            mainLooper.removeCallbacks(runnable);
        }
    }



    private void init() {
        mCamera = Camera.open();
        mCamera.setDisplayOrientation(0);
        //surfaceview 세팅
        mCameraHolder = mCameraView.getHolder();
        mCameraHolder.addCallback(this);
        mCameraHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }


    private void moveWindow(String D1, String D2, String D3) {
        //D1, D2, D3 중 하나라도 0이면 함수 종료(window 나타나지 않음)
        if(D1.equals("0.0000") || D2.equals("0.0000") || D3.equals("0.0000")) {
            window.setVisibility(View.INVISIBLE);
            image.setVisibility(View.INVISIBLE);
            fireExtinguisher.setVisibility(View.INVISIBLE);
            return;
        }

        //거리를 이용해 스마트폰 좌표 계산
        DecimalFormat form3 = new DecimalFormat("#.#########");
        double xf0 = Double.parseDouble(form3.format((Math.pow(4.5, 2) + Math.pow(Double.parseDouble(D1), 2) - Math.pow(Double.parseDouble(D2), 2))/(2.0*4.5)));
        double yf0 = Double.parseDouble(form3.format((Math.pow(2.385, 2) + Math.pow(Double.parseDouble(D1), 2) - Math.pow(Double.parseDouble(D3), 2))/(2.0*2.385)));
        double zf0 = Math.sqrt(Math.pow(Double.parseDouble(D1), 2) - Math.pow(xf0, 2) - Math.pow(yf0, 2));
        if(Double.isNaN(zf0)) {
            zf0 = zf0_1;
        }
        else if(!Double.isNaN(zf0)) {
            zf0_1 = zf0;
        }

        if(AX[0] == null) AX[0] = Double.toString(xf0);
        else if(AX[1] == null) AX[1] = Double.toString(xf0);
        else if(AX[2] == null) AX[2] = Double.toString(xf0);
        else AX[3] = Double.toString(xf0);

        if(AY[0] == null) AY[0] = Double.toString(yf0);
        else if(AY[1] == null) AY[1] = Double.toString(yf0);
        else if(AY[2] == null) AY[2] = Double.toString(yf0);
        else AY[3] = Double.toString(yf0);

        if(AX[2] == null || AY[2] == null) {
            Toast.makeText(getContext(), "스마트폰 위치 계산 중", Toast.LENGTH_SHORT).show();
            return;
        }

        //소화기 좌표를 서버로부터 받아옴
        try {
            HashMap<String, Object> input = new HashMap<>();
            input.put("x", AX[2]);
            input.put("y", AY[2]);
            input.put("z", zf0);
            JSONObject p = new JSONObject(input);
            retrofitAPI.putData(p).enqueue(new Callback<Post>() {
                @Override
                public void onResponse(@NonNull Call<Post> call, @NonNull Response<Post> response) {
                    if (response.isSuccessful()) {
                        Post body = response.body();
                        if (body != null) {
                            xf1 = body.getPos_x();
                            yf1 = body.getPos_y();
                            zf1 = body.getPos_z();
                            detailPressure.setText("소화기 압력 : " + body.getPressure());
                            //Toast.makeText(getContext(), xf1 + ", " + yf1 + ", " + zf1, Toast.LENGTH_SHORT).show();
                        } else {Toast.makeText(getContext(), "실패(body=null)", Toast.LENGTH_SHORT).show();}
                    }
                }
                @Override
                public void onFailure(@NonNull Call<Post> call, @NonNull Throwable t) {
                    Toast.makeText(getContext(), "onFailure : "+t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(getContext(), "예외" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }


        //스마트폰과 IoT 기기 사이의 벡터 값
        double Xf = xf1 - xf0;
        double Yf = yf1 - yf0;
        double Zf = zf1 - zf0;

        XfText.setText("Xf = " + Double.toString(Math.round(Xf * 10)/10.0));
        YfText.setText("Yf = " + Double.toString(Math.round(Yf*10)/10.0));
        ZfText.setText("Zf = " + Double.toString(Math.round(Zf*10)/10.0));
        xf0Text.setText("xf0 = " + Double.toString(Math.round(xf0 * 10)/10.0));
        yf0Text.setText("yf0 = " + Double.toString(Math.round(yf0*10)/10.0));
        zf0Text.setText("zf0 = " + Double.toString(Math.round(zf0*10)/10.0));

        if(Xf == (-1) * xf0 && Yf == (-1) * yf0) {
            Toast.makeText(getContext(), "소화기 위치를 받아오는 중", Toast.LENGTH_SHORT).show();
            return;
        }

        double D = Math.sqrt(Math.pow(Xf, 2) + Math.pow(Yf, 2) + Math.pow(Zf, 2));
        double thetaV = Math.toDegrees(Math.acos(Math.sqrt(Math.pow(Xf, 2) + Math.pow(Yf, 2)) / D));
        double thetaH = Math.toDegrees(Math.acos(Math.sqrt(Math.pow(Yf, 2) + Math.pow(Zf, 2)) / D));

        //텍스트뷰 텍스트 설정
        detailDist.setText("거리 : " + String.valueOf(Math.round(D*10)/10.0) + "m");

        //window 크기 설정(거리에 따라)
        if (D > 10.0) {
            window.getLayoutParams().height = 300;
            window.getLayoutParams().width = 300;
            image.getLayoutParams().height = 300;
            image.getLayoutParams().width = 300;
            fireExtinguisher.getLayoutParams().height = 210;
            fireExtinguisher.getLayoutParams().width = 120;
        } else if(D < 3.0) {
            window.getLayoutParams().height = 600;
            window.getLayoutParams().width = 600;
            image.getLayoutParams().height = 600;
            image.getLayoutParams().width = 600;
            fireExtinguisher.getLayoutParams().height = 420;
            fireExtinguisher.getLayoutParams().width = 240;
        } else if(D <= 10.0 && D >= 3.0) {
            window.getLayoutParams().height = (int)Math.round(600.0 + (600.0 - 300.0) / (10.0 - 3.0) * 3.0 - (600.0 - 300.0) / (10.0 - 3.0) * D);
            window.getLayoutParams().width = (int)Math.round(600.0 + (600.0 - 300.0) / (10.0 - 3.0) * 3.0 - (600.0 - 300.0) / (10.0 - 3.0) * D);
            image.getLayoutParams().height = (int)Math.round(600.0 + (600.0 - 300.0) / (10.0 - 3.0) * 3.0 - (600.0 - 300.0) / (10.0 - 3.0) * D);
            image.getLayoutParams().width = (int)Math.round(600.0 + (600.0 - 300.0) / (10.0 - 3.0) * 3.0 - (600.0 - 300.0) / (10.0 - 3.0) * D);
            fireExtinguisher.getLayoutParams().height = (int)Math.round(420.0 + (420.0 - 210.0) / (10.0 - 3.0) * 3.0 - (420.0 - 210.0) / (10.0 - 3.0) * D);
            fireExtinguisher.getLayoutParams().width = (int)Math.round(420.0 + (420.0 - 210.0) / (10.0 - 3.0) * 3.0 - (420.0 - 210.0) / (10.0 - 3.0) * D);
        }

        //window 안보이면 보이도록 처리
        if (window.getVisibility() == View.INVISIBLE || image.getVisibility() == View.INVISIBLE || fireExtinguisher.getVisibility() == View.INVISIBLE) {
            window.setVisibility(View.VISIBLE);
            image.setVisibility(View.VISIBLE);
            fireExtinguisher.setVisibility(View.VISIBLE);
        }

        //화면 상의 window 위치 설정(태블릿이 정면을 바라본 채로 똑바로 서 있을 때)
        FrameLayout.LayoutParams mLayoutParams = (FrameLayout.LayoutParams) window.getLayoutParams();
        if(Zf >= 0 && Xf < 0 ) {
            mLayoutParams.topMargin = (int)(Math.round(565.0 - (565.0/28.8)*thetaV));
            mLayoutParams.leftMargin = 2540 - (int)(Math.round(1270.0-(1270.0/43.4)*thetaH));
        } else if(Zf >= 0 && Xf >= 0) {
            mLayoutParams.topMargin = (int)(Math.round(565.0 - (565.0/28.8)*thetaV));
            mLayoutParams.leftMargin = (int)(Math.round(1270.0-(1270.0/43.4)*thetaH));
        } else if(Zf < 0 && Xf >= 0) {
            mLayoutParams.topMargin = 1130 - (int)(Math.round(565.0 - (565.0/28.8)*thetaV));
            mLayoutParams.leftMargin = (int)(Math.round(1270.0-(1270.0/43.4)*thetaH));
        } else if(Zf < 0 && Xf < 0) {
            mLayoutParams.topMargin = 1130 - (int)(Math.round(565.0 - (565.0/28.8)*thetaV));
            mLayoutParams.leftMargin = 2540 - (int)(Math.round(1270.0-(1270.0/43.4)*thetaH));
        }
        window.setLayoutParams(mLayoutParams);

        //네비게이션 시작

        String[][] navigation = navigation(Double.parseDouble(AX[2]), Double.parseDouble(AY[2]), xf1, yf1); //진행해야 하는 노드와 노드의 좌표를 순서대로 배열형태로 저장
        int[] direction = {
                determinDirection(navigation[0][1], navigation[0][2], navigation[1][1], navigation[1][2], navigation[2][1], navigation[2][2]),
                determinDirection(navigation[1][1], navigation[1][2], navigation[2][1], navigation[2][2], navigation[3][1], navigation[3][2])
        };

        if(passedNodesCount == 1) {
            //다른 노드를 거치지 않고 바로 소화기 노드로 향해야 할 때
            if(navigation[1][0].equals("D")) {
                leftArrow.setVisibility(View.INVISIBLE);
                leftArrowImage.setVisibility(View.INVISIBLE);
                leftArrowText.setVisibility(View.INVISIBLE);
                rightArrow.setVisibility(View.INVISIBLE);
                rightArrowImage.setVisibility(View.INVISIBLE);
                rightArrowText.setVisibility(View.INVISIBLE);
                goStraight.setVisibility(View.VISIBLE);
            }
            //다른 노드를 거쳐서 소화기 노드로 향할 때
            else {
                if(direction[0] == 1) {
                    //다음 노드에서 좌회전이면
                    double toNodeX = Double.parseDouble(navigation[1][1]) - xf0;
                    double toNodeY = Double.parseDouble(navigation[1][2]) - yf0;
                    double toNodeZ = 1.0 - zf0;
                    double toNodeD = Math.sqrt(Math.pow(toNodeX, 2) + Math.pow(toNodeY, 2) + Math.pow(toNodeZ, 2));
                    double thetaVToNode = Math.toDegrees(Math.acos(Math.sqrt(Math.pow(toNodeX, 2) + Math.pow(toNodeY, 2)) / toNodeD));
                    double thetaHToNode = Math.toDegrees(Math.acos(Math.sqrt(Math.pow(toNodeY, 2) + Math.pow(toNodeZ, 2)) / toNodeD));
                    leftArrow.setVisibility(View.VISIBLE);
                    leftArrowImage.setVisibility(View.VISIBLE);
                    leftArrowText.setVisibility(View.VISIBLE);
                    rightArrow.setVisibility(View.INVISIBLE);
                    rightArrowImage.setVisibility(View.INVISIBLE);
                    rightArrowText.setVisibility(View.INVISIBLE);
                    goStraight.setVisibility(View.VISIBLE);
                    if (toNodeD > 10.0) {
                        int height = 225;
                        int width = 450;
                        leftArrow.getLayoutParams().height = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        leftArrow.getLayoutParams().width = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        leftArrowImage.getLayoutParams().height = height;
                        leftArrowImage.getLayoutParams().width = width;
                        leftArrowText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 25);
                    } else if(toNodeD < 3.0) {
                        int height = 450;
                        int width = 900;
                        leftArrow.getLayoutParams().height = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        leftArrow.getLayoutParams().width = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        leftArrowImage.getLayoutParams().height = height;
                        leftArrowImage.getLayoutParams().width = width;
                        leftArrowText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 50);
                    } else if(toNodeD >= 3.0 && toNodeD <= 10.0){
                        int height = (int)Math.round(450.0 + (450.0 - 225.0) / (10.0 - 3.0) * 3.0 - (450.0 - 225.0) / (10.0 - 3.0) * toNodeD);
                        int width = (int)Math.round(900.0 + (900.0 - 450.0) / (10.0 - 3.0) * 3.0 - (900.0 - 450.0) / (10.0 - 3.0) * toNodeD);
                        leftArrow.getLayoutParams().height = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        leftArrow.getLayoutParams().width = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        leftArrowImage.getLayoutParams().height = height;
                        leftArrowImage.getLayoutParams().width = width;
                        leftArrowText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (int)Math.round(50.0 + (50.0 - 25.0) / (10.0 - 3.0) * 3.0 - (50.0 - 25.0) / (10.0 - 3.0) * toNodeD));
                    }
                    FrameLayout.LayoutParams mLayoutParams2 = (FrameLayout.LayoutParams) leftArrow.getLayoutParams();
                    if(toNodeZ >= 0 && toNodeX < 0 ) {
                        mLayoutParams2.topMargin = (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams2.leftMargin = 2540 - (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    } else if(toNodeZ >= 0 && toNodeX >= 0) {
                        mLayoutParams2.topMargin = (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams2.leftMargin = (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    } else if(toNodeZ < 0 && toNodeX >= 0) {
                        mLayoutParams2.topMargin = 1130 - (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams2.leftMargin = (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    } else if(toNodeZ < 0 && toNodeX < 0) {
                        mLayoutParams2.topMargin = 1130 - (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams2.leftMargin = 2540 - (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    }
                    leftArrow.setLayoutParams(mLayoutParams2);
                    leftArrowText.setText(String.valueOf(Math.round(toNodeD*10)/10.0) +"m 앞");
                    if(toNodeD <= 3.5) {
                        passedNodesCount++;
                        //Toast.makeText(getContext(), "next node has been changed", Toast.LENGTH_SHORT).show();
                    }
                }
                else if(direction[0] == 2) {
                    //다음 노드에서 우회전이면
                    double toNodeX = Double.parseDouble(navigation[1][1]) - xf0;
                    double toNodeY = Double.parseDouble(navigation[1][2]) - yf0;
                    double toNodeZ = 1.0 - zf0;
                    double toNodeD = Math.sqrt(Math.pow(toNodeX, 2) + Math.pow(toNodeY, 2) + Math.pow(toNodeZ, 2));
                    double thetaVToNode = Math.toDegrees(Math.acos(Math.sqrt(Math.pow(toNodeX, 2) + Math.pow(toNodeY, 2)) / toNodeD));
                    double thetaHToNode = Math.toDegrees(Math.acos(Math.sqrt(Math.pow(toNodeY, 2) + Math.pow(toNodeZ, 2)) / toNodeD));
                    leftArrow.setVisibility(View.INVISIBLE);
                    leftArrowImage.setVisibility(View.INVISIBLE);
                    leftArrowText.setVisibility(View.INVISIBLE);
                    rightArrow.setVisibility(View.VISIBLE);
                    rightArrowImage.setVisibility(View.VISIBLE);
                    rightArrowText.setVisibility(View.VISIBLE);
                    goStraight.setVisibility(View.VISIBLE);
                    if (toNodeD > 10.0) {
                        int height = 225;
                        int width = 450;
                        rightArrow.getLayoutParams().height = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        rightArrow.getLayoutParams().width = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        rightArrowImage.getLayoutParams().height = height;
                        rightArrowImage.getLayoutParams().width = width;
                        rightArrowText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 25);
                    } else if(toNodeD < 3.0) {
                        int height = 450;
                        int width = 900;
                        rightArrow.getLayoutParams().height = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        rightArrow.getLayoutParams().width = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        rightArrowImage.getLayoutParams().height = height;
                        rightArrowImage.getLayoutParams().width = width;
                        rightArrowText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 50);
                    } else if(toNodeD >= 3.0 && toNodeD <= 10.0){
                        int height = (int)Math.round(450.0 + (450.0 - 225.0) / (10.0 - 3.0) * 3.0 - (450.0 - 225.0) / (10.0 - 3.0) * toNodeD);
                        int width = (int)Math.round(900.0 + (900.0 - 450.0) / (10.0 - 3.0) * 3.0 - (900.0 - 450.0) / (10.0 - 3.0) * toNodeD);
                        rightArrow.getLayoutParams().height = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        rightArrow.getLayoutParams().width = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        rightArrowImage.getLayoutParams().height = height;
                        rightArrowImage.getLayoutParams().width = width;
                        rightArrowText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (int)Math.round(50.0 + (50.0 - 25.0) / (10.0 - 3.0) * 3.0 - (50.0 - 25.0) / (10.0 - 3.0) * toNodeD));
                    }
                    FrameLayout.LayoutParams mLayoutParams3 = (FrameLayout.LayoutParams) rightArrow.getLayoutParams();
                    if(toNodeZ >= 0 && toNodeX < 0 ) {
                        mLayoutParams3.topMargin = (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams3.leftMargin = 2540 - (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    } else if(toNodeZ >= 0 && toNodeX >= 0) {
                        mLayoutParams3.topMargin = (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams3.leftMargin = (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    } else if(toNodeZ < 0 && toNodeX >= 0) {
                        mLayoutParams3.topMargin = 1130 - (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams3.leftMargin = (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    } else if(toNodeZ < 0 && toNodeX < 0) {
                        mLayoutParams3.topMargin = 1130 - (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams3.leftMargin = 2540 - (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    }
                    rightArrow.setLayoutParams(mLayoutParams3);
                    rightArrowText.setText(String.valueOf(Math.round(toNodeD*10)/10.0)+"m 앞");
                    if(toNodeD <= 3.5) {
                        passedNodesCount++;
                        //Toast.makeText(getContext(), "next node has been changed", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
        else if(passedNodesCount == 2) {
            //다른 노드를 거치지 않고 바로 소화기 노드로 향해야 할 때
            if(navigation[2][0].equals("D")) {
                leftArrow.setVisibility(View.INVISIBLE);
                leftArrowImage.setVisibility(View.INVISIBLE);
                leftArrowText.setVisibility(View.INVISIBLE);
                rightArrow.setVisibility(View.INVISIBLE);
                rightArrowImage.setVisibility(View.INVISIBLE);
                rightArrowText.setVisibility(View.INVISIBLE);
                goStraight.setVisibility(View.VISIBLE);
            }
            //다른 노드를 거쳐서 소화기 노드로 향할 때
            else {
                if(direction[1] == 1) {
                    //다음 노드에서 좌회전이면
                    double toNodeX = Double.parseDouble(navigation[2][1]) - xf0;
                    double toNodeY = Double.parseDouble(navigation[2][2]) - yf0;
                    double toNodeZ = 1.0 - zf0;
                    double toNodeD = Math.sqrt(Math.pow(toNodeX, 2) + Math.pow(toNodeY, 2) + Math.pow(toNodeZ, 2));
                    double thetaVToNode = Math.toDegrees(Math.acos(Math.sqrt(Math.pow(toNodeX, 2) + Math.pow(toNodeY, 2)) / toNodeD));
                    double thetaHToNode = Math.toDegrees(Math.acos(Math.sqrt(Math.pow(toNodeY, 2) + Math.pow(toNodeZ, 2)) / toNodeD));
                    leftArrow.setVisibility(View.VISIBLE);
                    leftArrowImage.setVisibility(View.VISIBLE);
                    leftArrowText.setVisibility(View.VISIBLE);
                    rightArrow.setVisibility(View.INVISIBLE);
                    rightArrowImage.setVisibility(View.INVISIBLE);
                    rightArrowText.setVisibility(View.INVISIBLE);
                    goStraight.setVisibility(View.VISIBLE);
                    if (toNodeD > 10.0) {
                        int height = 225;
                        int width = 450;
                        leftArrow.getLayoutParams().height = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        leftArrow.getLayoutParams().width = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        leftArrowImage.getLayoutParams().height = height;
                        leftArrowImage.getLayoutParams().width = width;
                        leftArrowText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 25);
                    } else if(toNodeD < 3.0) {
                        int height = 450;
                        int width = 900;
                        leftArrow.getLayoutParams().height = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        leftArrow.getLayoutParams().width = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        leftArrowImage.getLayoutParams().height = height;
                        leftArrowImage.getLayoutParams().width = width;
                        leftArrowText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 50);
                    } else if(toNodeD >= 3.0 && toNodeD <= 10.0){
                        int height = (int)Math.round(450.0 + (450.0 - 225.0) / (10.0 - 3.0) * 3.0 - (450.0 - 225.0) / (10.0 - 3.0) * toNodeD);
                        int width = (int)Math.round(900.0 + (900.0 - 450.0) / (10.0 - 3.0) * 3.0 - (900.0 - 450.0) / (10.0 - 3.0) * toNodeD);
                        leftArrow.getLayoutParams().height = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        leftArrow.getLayoutParams().width = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        leftArrowImage.getLayoutParams().height = height;
                        leftArrowImage.getLayoutParams().width = width;
                        leftArrowText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (int)Math.round(50.0 + (50.0 - 25.0) / (10.0 - 3.0) * 3.0 - (50.0 - 25.0) / (10.0 - 3.0) * toNodeD));
                    }
                    FrameLayout.LayoutParams mLayoutParams2 = (FrameLayout.LayoutParams) leftArrow.getLayoutParams();
                    if(toNodeZ >= 0 && toNodeX < 0 ) {
                        mLayoutParams2.topMargin = (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams2.leftMargin = 2540 - (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    } else if(toNodeZ >= 0 && toNodeX >= 0) {
                        mLayoutParams2.topMargin = (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams2.leftMargin = (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    } else if(toNodeZ < 0 && toNodeX >= 0) {
                        mLayoutParams2.topMargin = 1130 - (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams2.leftMargin = (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    } else if(toNodeZ < 0 && toNodeX < 0) {
                        mLayoutParams2.topMargin = 1130 - (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams2.leftMargin = 2540 - (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    }
                    leftArrow.setLayoutParams(mLayoutParams2);
                    leftArrowText.setText(String.valueOf(Math.round(toNodeD*10)/10.0) +"m 앞");
                    if(toNodeD <= 3.5) {
                        passedNodesCount++;
                        //Toast.makeText(getContext(), "next node has been changed", Toast.LENGTH_SHORT).show();
                    }
                }
                else if(direction[1] == 2) {
                    //다음 노드에서 우회전이면
                    double toNodeX = Double.parseDouble(navigation[2][1]) - xf0;
                    double toNodeY = Double.parseDouble(navigation[2][2]) - yf0;
                    double toNodeZ = 1.0 - zf0;
                    double toNodeD = Math.sqrt(Math.pow(toNodeX, 2) + Math.pow(toNodeY, 2) + Math.pow(toNodeZ, 2));
                    double thetaVToNode = Math.toDegrees(Math.acos(Math.sqrt(Math.pow(toNodeX, 2) + Math.pow(toNodeY, 2)) / toNodeD));
                    double thetaHToNode = Math.toDegrees(Math.acos(Math.sqrt(Math.pow(toNodeY, 2) + Math.pow(toNodeZ, 2)) / toNodeD));
                    leftArrow.setVisibility(View.INVISIBLE);
                    leftArrowImage.setVisibility(View.INVISIBLE);
                    leftArrowText.setVisibility(View.INVISIBLE);
                    rightArrow.setVisibility(View.VISIBLE);
                    rightArrowImage.setVisibility(View.VISIBLE);
                    rightArrowText.setVisibility(View.VISIBLE);
                    goStraight.setVisibility(View.VISIBLE);
                    if (toNodeD > 10.0) {
                        int height = 225;
                        int width = 450;
                        rightArrow.getLayoutParams().height = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        rightArrow.getLayoutParams().width = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        rightArrowImage.getLayoutParams().height = height;
                        rightArrowImage.getLayoutParams().width = width;
                        rightArrowText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 25);
                    } else if(toNodeD < 3.0) {
                        int height = 450;
                        int width = 900;
                        rightArrow.getLayoutParams().height = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        rightArrow.getLayoutParams().width = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        rightArrowImage.getLayoutParams().height = height;
                        rightArrowImage.getLayoutParams().width = width;
                        rightArrowText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 50);
                    } else if(toNodeD >= 3.0 && toNodeD <= 10.0){
                        int height = (int)Math.round(450.0 + (450.0 - 225.0) / (10.0 - 3.0) * 3.0 - (450.0 - 225.0) / (10.0 - 3.0) * toNodeD);
                        int width = (int)Math.round(900.0 + (900.0 - 450.0) / (10.0 - 3.0) * 3.0 - (900.0 - 450.0) / (10.0 - 3.0) * toNodeD);
                        rightArrow.getLayoutParams().height = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        rightArrow.getLayoutParams().width = (int)Math.round(Math.sqrt(Math.pow(height, 2) + Math.pow(width, 2)));
                        rightArrowImage.getLayoutParams().height = height;
                        rightArrowImage.getLayoutParams().width = width;
                        rightArrowText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (int)Math.round(50.0 + (50.0 - 25.0) / (10.0 - 3.0) * 3.0 - (50.0 - 25.0) / (10.0 - 3.0) * toNodeD));
                    }
                    FrameLayout.LayoutParams mLayoutParams3 = (FrameLayout.LayoutParams) rightArrow.getLayoutParams();
                    if(toNodeZ >= 0 && toNodeX < 0 ) {
                        mLayoutParams3.topMargin = (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams3.leftMargin = 2540 - (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    } else if(toNodeZ >= 0 && toNodeX >= 0) {
                        mLayoutParams3.topMargin = (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams3.leftMargin = (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    } else if(toNodeZ < 0 && toNodeX >= 0) {
                        mLayoutParams3.topMargin = 1130 - (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams3.leftMargin = (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    } else if(toNodeZ < 0 && toNodeX < 0) {
                        mLayoutParams3.topMargin = 1130 - (int)(Math.round(565.0 - (565.0/28.8)*thetaVToNode)) - 200;
                        mLayoutParams3.leftMargin = 2540 - (int)(Math.round(1270.0-(1270.0/43.4)*thetaHToNode)) - 1000;
                    }
                    rightArrow.setLayoutParams(mLayoutParams3);
                    rightArrowText.setText(String.valueOf(Math.round(toNodeD*10)/10.0)+"m 앞");
                    if(toNodeD <= 3.5) {
                        passedNodesCount++;
                        //Toast.makeText(getContext(), "next node has been changed", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
        else if(passedNodesCount == 3) {
            leftArrow.setVisibility(View.INVISIBLE);
            leftArrowImage.setVisibility(View.INVISIBLE);
            leftArrowText.setVisibility(View.INVISIBLE);
            rightArrow.setVisibility(View.INVISIBLE);
            rightArrowImage.setVisibility(View.INVISIBLE);
            rightArrowText.setVisibility(View.INVISIBLE);
            goStraight.setVisibility(View.VISIBLE);
            arrive.setVisibility(View.VISIBLE);
        }

        if(D <= 5.0) {
            window.setVisibility(View.VISIBLE);
            leftArrowImage.setVisibility(View.INVISIBLE);
            leftArrowText.setVisibility(View.INVISIBLE);
            rightArrowImage.setVisibility(View.INVISIBLE);
            rightArrowText.setVisibility(View.INVISIBLE);
            goStraight.setVisibility(View.INVISIBLE);
            arrive.setVisibility(View.VISIBLE);
        }

    }

    private void rotate(View view, double fromDegrees, double toDegrees) {
        final RotateAnimation rotate = new RotateAnimation((float) fromDegrees, (float) toDegrees,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(0);
        rotate.setFillAfter(true);

        view.startAnimation(rotate);
    }

    private void complementary(double new_ts) {
        gyroRunning = false;
        accRunning = false;

        if(timestamp == 0) {
            timestamp = new_ts;
            return;
        }
        dt = (new_ts - timestamp) * NS2S;
        timestamp = new_ts;

        mAccPitch = -Math.atan2(mAccValues[0], mAccValues[2]) * 180.0 / Math.PI;
        mAccYaw = Math.atan2(mAccValues[0], mAccValues[1]) * 180.0 / Math.PI;

        temp = (1/a) * (mAccPitch - pitch) + mGyroValues[1];
        pitch = pitch + (temp*dt);

        temp = (1/a) * (mAccYaw - yaw) + mGyroValues[2];
        yaw = yaw + (temp*dt);

        if (dt - timestamp*NS2S != 0) {
            roll = roll + mGyroValues[0]*dt;
        }

        yawDegree = Math.toDegrees(Math.asin(Math.sin(Math.toRadians(yaw) + Math.PI/2)));
        rollDegree = roll * (180.0 / Math.PI);
        pitchDegree = Math.toDegrees(Math.asin(Math.sin(Math.toRadians(pitch) + Math.PI/2)));

        //window 회전(태블릿 회전에 따라)
        rotate(image, (-1)*yawDegree, (-1)*yawDegree);
        rotate(fireExtinguisher, (-1)*yawDegree, (-1)*yawDegree);
        rotate(rightArrowImage, (-1)*yawDegree, (-1)*yawDegree);
        rotate(rightArrowText, (-1)*yawDegree, (-1)*yawDegree);
        rotate(leftArrowImage, (-1)*yawDegree, (-1)*yawDegree);
        rotate(leftArrowText, (-1)*yawDegree, (-1)*yawDegree);

        //태블릿의 기울어짐에 따라 window 이동
        ObjectAnimator animation1 = ObjectAnimator.ofFloat(window, "translationY", (int)Math.round((565.0/18.8)*(-1)*pitchDegree));
        ObjectAnimator animation2 = ObjectAnimator.ofFloat(window, "translationX", (int)Math.round((1270.0/33.4)*rollDegree));
        ObjectAnimator animation3 = ObjectAnimator.ofFloat(leftArrow, "translationY", (int)Math.round((565.0/18.8)*(-1)*pitchDegree));
        ObjectAnimator animation4 = ObjectAnimator.ofFloat(leftArrow, "translationX", (int)Math.round((1270.0/33.4)*rollDegree));
        ObjectAnimator animation5 = ObjectAnimator.ofFloat(rightArrow, "translationY", (int)Math.round((565.0/18.8)*(-1)*pitchDegree));
        ObjectAnimator animation6 = ObjectAnimator.ofFloat(rightArrow, "translationX", (int)Math.round((1270.0/33.4)*rollDegree));
        animation1.setDuration(80);
        animation2.setDuration(80);
        animation3.setDuration(80);
        animation4.setDuration(80);
        animation5.setDuration(80);
        animation6.setDuration(80);
        animation1.start();
        animation2.start();
        animation3.start();
        animation4.start();
        animation5.start();
        animation6.start();
    }

    public class UserSensorListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_GYROSCOPE:
                    mGyroValues = event.values;
                    if(!gyroRunning) gyroRunning = true;
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    mAccValues = event.values;
                    if(!accRunning) accRunning = true;
                    break;
            }
            if(gyroRunning && accRunning) {
                complementary(event.timestamp);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    }

    //---------------------------------------------내비게이션 함수------------------------------------------------------

    static String[][] navigation(double Ax, double Ay, double Dx, double Dy) {
        double[] posA = {Ax, Ay};
        double[] posB = {2.25, 1.1925};
        double[] posC = {2.25, 11.9925};
        double[] posD = {Dx, Dy};

        String[] node = {"A", "B", "C", "D"};
        String[][] navigationNodes = new String[4][3];
        navigationNodes[0][0] = node[0];
        navigationNodes[0][1] = Double.toString(posA[0]);
        navigationNodes[0][2] = Double.toString(posA[1]);

        double AtoB = distance(posA, posB);
        double AtoC = distance(posA, posC);
        double AtoD = distance(posA, posD);
        double[] distanceA = {AtoB, AtoC, AtoD};
        Arrays.sort(distanceA);
        if(distanceA[0] == AtoB) {
            navigationNodes[1][0] = node[1];
            navigationNodes[1][1] = Double.toString(posB[0]);
            navigationNodes[1][2] = Double.toString(posB[1]);
        } else if(distanceA[0] == AtoC) {
            navigationNodes[1][0] = node[2];
            navigationNodes[1][1] = Double.toString(posC[0]);
            navigationNodes[1][2] = Double.toString(posC[1]);
        } else if(distanceA[0] == AtoD) {
            navigationNodes[1][0] = node[3];
            navigationNodes[1][1] = Double.toString(posD[0]);
            navigationNodes[1][2] = Double.toString(posD[1]);
        }

        if(navigationNodes[1][0].equals(node[1])) {
            double BtoC = distance(posB, posC);
            double BtoD = distance(posB, posD);
            double[] distanceB = {BtoC, BtoD};
            Arrays.sort(distanceB);
            if(distanceB[0] == BtoC) {
                navigationNodes[2][0] = node[2];
                navigationNodes[2][1] = Double.toString(posC[0]);
                navigationNodes[2][2] = Double.toString(posC[1]);
            } else if(distanceB[0] == BtoD) {
                navigationNodes[2][0] = node[3];
                navigationNodes[2][1] = Double.toString(posD[0]);
                navigationNodes[2][2] = Double.toString(posD[1]);
            }
        }else if(navigationNodes[1][0].equals(node[2])) {
            double CtoB = distance(posC, posB);
            double CtoD = distance(posC, posD);
            double[] distanceC = {CtoB, CtoD};
            Arrays.sort(distanceC);
            if(distanceC[0] == CtoB) {
                navigationNodes[2][0] = node[1];
                navigationNodes[2][1] = Double.toString(posB[0]);
                navigationNodes[2][2] = Double.toString(posB[1]);
            } else if(distanceC[0] == CtoD) {
                navigationNodes[2][0] = node[3];
                navigationNodes[2][1] = Double.toString(posD[0]);
                navigationNodes[2][2] = Double.toString(posD[1]);
            }
        }

        if(navigationNodes[2][0].equals(node[1])) {
            double BtoC = distance(posB, posC);
            double BtoD = distance(posB, posD);
            double[] distanceB = {BtoC, BtoD};
            Arrays.sort(distanceB);
            if(distanceB[0] == BtoC) {
                navigationNodes[3][0] = node[2];
                navigationNodes[3][1] = Double.toString(posC[0]);
                navigationNodes[3][2] = Double.toString(posC[1]);
            } else if(distanceB[0] == BtoD) {
                navigationNodes[3][0] = node[3];
                navigationNodes[3][1] = Double.toString(posD[0]);
                navigationNodes[3][2] = Double.toString(posD[1]);
            }
        }else if(navigationNodes[2][0].equals(node[2])) {
            double CtoB = distance(posC, posB);
            double CtoD = distance(posC, posD);
            double[] distanceC = {CtoB, CtoD};
            Arrays.sort(distanceC);
            if(distanceC[0] == CtoB) {
                navigationNodes[3][0] = node[1];
                navigationNodes[3][1] = Double.toString(posB[0]);
                navigationNodes[3][2] = Double.toString(posB[1]);
            } else if(distanceC[0] == CtoD) {
                navigationNodes[3][0] = node[3];
                navigationNodes[3][1] = Double.toString(posD[0]);
                navigationNodes[3][2] = Double.toString(posD[1]);
            }
        }
        return navigationNodes;
    }

    static double distance(double[] A, double[] B) {
        return Math.sqrt(Math.pow((A[0] - B[0]), 2) + Math.pow((A[1] - B[1]), 2));
    }

    static int determinDirection(String nowX, String nowY, String headX, String headY, String nextX, String nextY) {
        int result = 0;
        if(nextX != null && nextY != null) {
            double firVectorX = Double.parseDouble(headX) - Double.parseDouble(nowX);
            double firVectorY = Double.parseDouble(headY) - Double.parseDouble(nowY);
            double secVectorX = Double.parseDouble(nextX) - Double.parseDouble(headX);
            double secVectorY = Double.parseDouble(nextY) - Double.parseDouble(headY);
            double crossProductZ = firVectorX*secVectorY - firVectorY*secVectorX;
            if(crossProductZ > 0 ) result = 1; //좌회전
            else if(crossProductZ < 0 ) result = 2; //우회전
            else result = 0;
        }
        return result;
    }

}

