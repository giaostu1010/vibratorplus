package me.xfzcc.vibratorplus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.os.Build;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import android.os.Handler;

public class MainActivity extends Activity implements View.OnClickListener {

    private Button btn_hasVibrator;
    private Button btn_short;
    private Button btn_long;
    private Button btn_rhythm;
    private Button btn_cancle;
    private Button btn_upload;
    private Button btn_start;
    private Button btn_pause;
    private Button btn_stop;

    private Vibrator myVibrator;
    private Context mContext;
    private static final int FILE_SELECT_CODE = 1;
    private static final int REQUEST_PERMISSION_CODE = 2;
    private long[] customPattern;
    private boolean isVibrating = false;
    private boolean isPaused = false;
    private Handler vibrateHandler = new Handler();
    private int currentIndex = 0;
    private long pauseTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        myVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mContext = MainActivity.this;
        bindViews();
        checkPermission();
    }

    private void bindViews() {
        btn_hasVibrator = (Button) findViewById(R.id.btn_hasVibrator);
        btn_short = (Button) findViewById(R.id.btn_short);
        btn_long = (Button) findViewById(R.id.btn_long);
        btn_rhythm = (Button) findViewById(R.id.btn_rhythm);
        btn_cancle = (Button) findViewById(R.id.btn_cancle);
        btn_upload = (Button) findViewById(R.id.btn_upload);
        btn_start = (Button) findViewById(R.id.btn_start);
        btn_pause = (Button) findViewById(R.id.btn_pause);
        btn_stop = (Button) findViewById(R.id.btn_stop);

        btn_hasVibrator.setOnClickListener(this);
        btn_short.setOnClickListener(this);
        btn_long.setOnClickListener(this);
        btn_rhythm.setOnClickListener(this);
        btn_cancle.setOnClickListener(this);
        btn_upload.setOnClickListener(this);
        btn_start.setOnClickListener(this);
        btn_pause.setOnClickListener(this);
        btn_stop.setOnClickListener(this);

        // 初始禁用控制按钮
        setControlButtonsEnabled(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_SELECT_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                try {
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(getContentResolver().openInputStream(uri)));
                    String line;
                    StringBuilder sb = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();
                    parseVibrationPattern(sb.toString());
                } catch (Exception e) {
                    Toast.makeText(mContext, "读取文件失败", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(mContext, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mContext, "需要存储权限才能上传文件", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVibration();
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION_CODE);
            }
        }
    }

    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择振动模式文件"), FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "请安装文件管理器", Toast.LENGTH_SHORT).show();
        }
    }

    private void parseVibrationPattern(String content) {
        try {
            content = content.replaceAll("[{} ]", "");
            String[] parts = content.split(",");
            List<Long> patternList = new ArrayList<>();

            for (String part : parts) {
                if (!part.isEmpty()) {
                    patternList.add(Long.parseLong(part.trim()));
                }
            }

            customPattern = new long[patternList.size()];
            for (int i = 0; i < patternList.size(); i++) {
                customPattern[i] = patternList.get(i);
            }

            Toast.makeText(mContext, "成功加载自定义振动模式，共" + customPattern.length + "个参数",
                    Toast.LENGTH_SHORT).show();
            setControlButtonsEnabled(true);
        } catch (Exception e) {
            Toast.makeText(mContext, "解析文件失败，请检查格式", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void startCustomVibration() {
        if (customPattern == null || customPattern.length == 0) {
            Toast.makeText(mContext, "请先上传振动模式文件", Toast.LENGTH_SHORT).show();
            return;
        }

        isVibrating = true;
        isPaused = false;
        currentIndex = 0;
        executeVibrationStep();
    }

    private void executeVibrationStep() {
        if (!isVibrating || isPaused) return;

        if (currentIndex >= customPattern.length) {
            stopVibration();
            return;
        }

        long duration = customPattern[currentIndex];

        if (currentIndex % 2 == 0) {
            vibrateHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    currentIndex++;
                    executeVibrationStep();
                }
            }, duration);
        } else {
            myVibrator.vibrate(duration);
            vibrateHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    currentIndex++;
                    executeVibrationStep();
                }
            }, duration);
        }
    }

    private void pauseVibration() {
        if (isVibrating && !isPaused) {
            isPaused = true;
            myVibrator.cancel();
            vibrateHandler.removeCallbacksAndMessages(null);
            pauseTime = System.currentTimeMillis();
            Toast.makeText(mContext, "振动已暂停", Toast.LENGTH_SHORT).show();
        }
    }

    private void resumeVibration() {
        if (isVibrating && isPaused) {
            isPaused = false;
            executeVibrationStep();
            Toast.makeText(mContext, "振动已恢复", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopVibration() {
        isVibrating = false;
        isPaused = false;
        myVibrator.cancel();
        vibrateHandler.removeCallbacksAndMessages(null);
        currentIndex = 0;
        Toast.makeText(mContext, "振动已停止", Toast.LENGTH_SHORT).show();
    }

    private void setControlButtonsEnabled(boolean enabled) {
        btn_start.setEnabled(enabled);
        btn_pause.setEnabled(enabled);
        btn_stop.setEnabled(enabled);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_hasVibrator:
                Toast.makeText(mContext, myVibrator.hasVibrator() ? "当前设备有振动器" : "当前设备无振动器",
                        Toast.LENGTH_SHORT).show();
                break;
            case R.id.btn_short:
                myVibrator.cancel();
                myVibrator.vibrate(new long[]{100, 200, 100, 200}, 0);
                Toast.makeText(mContext, "短振动", Toast.LENGTH_SHORT).show();
                break;
            case R.id.btn_long:
                myVibrator.cancel();
                myVibrator.vibrate(new long[]{100, 100, 100, 1000}, 0);
                Toast.makeText(mContext, "长振动", Toast.LENGTH_SHORT).show();
                break;
            case R.id.btn_rhythm:
                myVibrator.cancel();
                myVibrator.vibrate(new long[]{500, 100, 500, 100, 500, 100}, 0);
                Toast.makeText(mContext, "节奏振动", Toast.LENGTH_SHORT).show();
                break;
            case R.id.btn_cancle:
                myVibrator.cancel();
                Toast.makeText(mContext, "取消振动", Toast.LENGTH_SHORT).show();
                break;
            case R.id.btn_upload:
                showFileChooser();
                break;
            case R.id.btn_start:
                if (isPaused) {
                    resumeVibration();
                } else {
                    startCustomVibration();
                }
                break;
            case R.id.btn_pause:
                pauseVibration();
                break;
            case R.id.btn_stop:
                stopVibration();
                break;
        }
    }
}