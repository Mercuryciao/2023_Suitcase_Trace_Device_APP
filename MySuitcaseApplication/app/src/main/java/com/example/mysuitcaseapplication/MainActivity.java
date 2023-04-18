package com.example.mysuitcaseapplication;
//package com.google.firebase.analytics.FirebaseAnalytics;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class MainActivity extends AppCompatActivity {
    TextView myLabel;
    TextView open_status;
    TextView damage_status;
    TextView damage_time;
    TextView suitcase_info_title;
    String current_date = String.valueOf(new Date());
    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    //get current date time with Calendar()
    Calendar cal = Calendar.getInstance();

    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    int open_count = 0;
    int hit_count = 0;
    int light_thre = 50;
    int move_thre = 10;
    int hit_count_thre = 4;
    int open_count_thre = 3;
    volatile boolean stopWorker;
    private FirebaseAnalytics mFirebaseAnalytics;
    List<String> list = new ArrayList<>();
    List<String> result = new ArrayList<>();
    ImageView suitcase_imageView;
    int[] images = { R.drawable.break_image, R.drawable.open_image, R.drawable.break_and_open };



    //    @SuppressLint("MissingInflatedId")
    @SuppressLint("MissingInflatedId")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        counter = 0;
        Button openButton = (Button) findViewById(R.id.open);
        myLabel = (TextView) findViewById(R.id.condition);
        damage_status = (TextView) findViewById(R.id.damage_status);
        open_status = (TextView) findViewById(R.id.open_status);
        damage_time = (TextView) findViewById(R.id.damage_time);
        suitcase_info_title = (TextView) findViewById(R.id.suitcase_info_title);
        suitcase_imageView = (ImageView) findViewById(R.id.suitcase_imageView);
//        myTextbox = (EditText) findViewById(R.id.entry);

        // Write a message to the database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference();

        //Open Button
        openButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    findBT();
                    openBT();
                } catch (IOException ex) {
                }
            }
        });

    }



    void findBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            myLabel.setText("No bluetooth adapter available");
        }

        if (!mBluetoothAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 2);

                return;
            }
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {

                Log.d("bluetooth", String.valueOf(device));
                if (device.getName().equals("Bag-Rescuer")) {

                    mmDevice = device;
                    break;
                }
            }
        }
        if (mmDevice == null) {
            myLabel.setText("Bluetooth Device Not Found");
        }
        else {
            myLabel.setText("Device Connected");
        }
    }

    void openBT() throws IOException {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 2);
            return;
        }
        Log.d("mmDevice", String.valueOf(mmDevice));
        if (mmDevice != null) {
            ParcelUuid[] uuids = mmDevice.getUuids();
            BluetoothSocket socket = mmDevice.createRfcommSocketToServiceRecord(uuids[0].getUuid());
            for (ParcelUuid item : uuids) {
                Log.d("Hi", String.valueOf(item.getUuid()));
            }

            socket.connect();
            Log.d("Hi2", String.valueOf(mmDevice));
            mmOutputStream = socket.getOutputStream();
            mmInputStream = socket.getInputStream();

            beginListenForData();

            myLabel.setText("Bluetooth Opened");
        }
    }

    void beginListenForData()
    {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        String message;

        FirebaseDatabase db = FirebaseDatabase.getInstance();
        DatabaseReference myRef = db.getReference();

        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;
                                    counter+=1;

                                    // deal with data
                                    result = Arrays.asList(data.split(", "));

                                    List<String> move_data = result.subList(0, 3);
                                    int light_data = Integer.parseInt(result.get(3));
                                    String GPS_data = result.get(4);

                                    myRef.child(String.valueOf(counter)).child("move_data").setValue(move_data);
                                    myRef.child(String.valueOf(counter)).child("light_data").setValue(light_data);
                                    myRef.child(String.valueOf(counter)).child("GPS_data").setValue(GPS_data);

                                    Log.d("TAG", data);


                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            myLabel.setText(data);
                                            int move_sum = 0;
                                            for (String element : move_data) {
//                                                overall_acc = math.sqrt(float(string[0])**2 + float(string[1])**2 + float(string[2])**2)

                                                move_sum += Math.pow(Double.parseDouble(element), 2);;
                                            }
                                            move_sum = (int) Math.sqrt(move_sum);
                                            if (light_data > light_thre){
                                                open_count += 1;
                                                System.out.println(open_count);
                                                open_status.setText(String.valueOf(open_count));
                                            }
                                            if (move_sum > move_thre) {
                                                hit_count += 1;
                                                System.out.println(hit_count);
                                                damage_status.setText(String.valueOf(hit_count));
                                                current_date = dateFormat.format(cal.getTime());
                                                current_date = current_date.concat(" Seattle, WA");
                                                damage_time.setText(String.valueOf(current_date));
                                                suitcase_info_title.setText(String.valueOf("Damage Detected!"));
                                            }
                                            if (hit_count > hit_count_thre && open_count > open_count_thre){
                                                suitcase_imageView.setImageResource(images[2]);
                                            }
                                            else if (open_count > open_count_thre){
                                                suitcase_imageView.setImageResource(images[1]);
                                            }
                                            else if (hit_count > hit_count_thre){
                                                suitcase_imageView.setImageResource(images[0]);
                                            }


                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });
        workerThread.start();
    }




}
