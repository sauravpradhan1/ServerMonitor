package hem.server.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;



public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NotificationModule";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private String senderID ="419910362746";
    private MjpegView mv;
    private TextView tempValue;
    private Handler mHandler = new Handler();
    private CheckBox cb;
    private Switch sw;

    Socket socket = null;
    DataOutputStream dataOutputStream = null;
    DataInputStream dataInputStream = null;

    private int prevTemp=0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String ip = getIntent().getStringExtra("IP");
        System.out.println("Connecting to " + ip);

        mv = new MjpegView(this);
        FrameLayout frame = (FrameLayout) findViewById(R.id.frame);
        frame.addView(mv, 0);

        new DoRead().execute("http://" + ip + ":8080/stream/video.mjpeg");

        tempValue = (TextView) findViewById(R.id.tempValue);
        cb = (CheckBox)findViewById(R.id.checkBox);
        cb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(((CheckBox)v).isChecked()){
                    SendData("a");
                    sw.setEnabled(false);
                }else{
                    SendData("m");
                    sw.setEnabled(true);
                }
            }
        });
        sw = (Switch) findViewById(R.id.switch1);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    SendData("F");
                }else{
                    SendData("f");
                }
            }
        });

        SocketClientTask sc = new SocketClientTask(ip,8089);
        sc.execute();

        register(senderID);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });
    }

    public void register(String senderID){
        if (checkPlayServices()){
            Intent intent = new Intent(this,GCMRegistrationService.class);
            intent.putExtra("senderID", senderID);
            this.startService(intent);
        }
    }

    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            } else {
                Log.i(TAG, "This device is not supported.");
                this.finish();
            }
            return false;
        }
        return true;
    }
    private void SendData(String dat){
        try{
            dataOutputStream.writeChars(dat);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public class DoRead extends AsyncTask<String, Void, MjpegInputStream> {
        protected MjpegInputStream doInBackground(String... url) {
            //TODO: if camera has authentication deal with it and don't just not work
            HttpResponse res = null;
            DefaultHttpClient httpclient = new DefaultHttpClient();

            // Log.d(TAG, "1. Sending http request");
            try {
                res = httpclient.execute(new HttpGet(URI.create(url[0])));
                //  Log.d(TAG, "2. Request finished, status = " + res.getStatusLine().getStatusCode());
                if(res.getStatusLine().getStatusCode()==401){
                    //You must turn off camera User Access Control before this will work
                    return null;
                }
                return new MjpegInputStream(res.getEntity().getContent());
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                //  Log.d(TAG, "Request failed-ClientProtocolException", e);
                //Error connecting to camera
            } catch (IOException e) {
                e.printStackTrace();
                //  Log.d(TAG, "Request failed-IOException", e);
                //Error connecting to camera
            }

            return null;
        }

        protected void onPostExecute(MjpegInputStream result) {
            mv.setSource(result);
            mv.setDisplayMode(MjpegView.SIZE_BEST_FIT);
            mv.showFps(true);
        }
    }

    @Override
    public void onBackPressed() {
        try{
            socket.close();
        }catch (IOException e){
            e.printStackTrace();
        }
        mv.stopPlayback();
        Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }


    public class SocketClientTask extends AsyncTask<Void, Void, Void> {

        String dstAddress;
        int dstPort;
        String response = "";

        SocketClientTask(String addr, int port){
            dstAddress = addr;
            dstPort = port;
        }

        @Override
        protected Void doInBackground(Void... arg0) {

            //Socket socket = null;
            //DataOutputStream dataOutputStream = null;
            //DataInputStream dataInputStream = null;
            try {
                socket = new Socket(dstAddress, dstPort);
                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                dataInputStream = new DataInputStream(socket.getInputStream());
                String receivedMessage;
                Log.w("Debugging", "Entering While Loop");


                while(true){
                    dataOutputStream.writeChars("t");
                    if(dataInputStream.available()>1){
                        receivedMessage=dataInputStream.readLine();
                        if(receivedMessage.length()>1){
                            Log.i("Socket Thread", receivedMessage);
                            final String data[] = receivedMessage.split(",");

                            if(prevTemp != Integer.parseInt(data[0])){
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        tempValue.setText(data[0] + " C ");
                                        cb.setChecked(Boolean.parseBoolean(data[3]));
                                        if(Integer.parseInt(data[0])>25){
                                            tempValue.setTextColor(Color.RED);
                                            if(Boolean.parseBoolean(data[3])) sw.setChecked(true);
                                        }else{
                                            tempValue.setTextColor(Color.BLUE);
                                            if(Boolean.parseBoolean(data[3])) sw.setChecked(false);
                                        }
                                        sw.setEnabled(!Boolean.parseBoolean(data[3]));
                                    }
                                });
                                prevTemp = Integer.parseInt(data[0]);
                            }

                            /*MainActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tempValue.setText(data[0] + " C ");
                                }
                            });*/
                        }
                    }else{
                        Log.w("Debugging", "Inside While Loop");
                    }

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        //Logger.getLogger(MainActivity.class.getName()).log(Level.SEVERE, null, ex);
                        Log.e("Thread Sleep",ex.toString());
                    }
                    //System.out.println("Loop..");
                }

            } catch (UnknownHostException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                response = "UnknownHostException: " + e.toString();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                response = "IOException: " + e.toString();
            }finally{
                if(socket != null){
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
        }

    }

}
