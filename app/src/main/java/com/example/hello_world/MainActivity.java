package com.example.hello_world;

import android.app.Activity;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.os.AsyncTask;
import java.io.IOException;
import android.os.Handler;

import java.io.OutputStreamWriter;
import java.util.Arrays;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import java.lang.*;
import java.text.NumberFormat;
import java.util.*;
import java.io.File;
import java.io.FileOutputStream;
import android.content.Context;
import android.widget.Toast;
import android.os.Environment;

import static android.R.attr.delay;
import static android.provider.AlarmClock.EXTRA_MESSAGE;

public class MainActivity extends AppCompatActivity {
    TextView Value,Byte_text,text_view,adc0,adc1,adc2,temperature;
    Button increase, decrease,dig_out;
    private NfcAdapter nfc;
    float ADC0=0,ADC1=0,ADC2=0, tempConv=0;
    private PendingIntent mpendingIntent;
    //graph var
    private final Handler mHandler = new Handler();
    private Runnable mTimer;
    private LineGraphSeries<DataPoint> mSeries;
    private LineGraphSeries<DataPoint> nSeries;
    private double graphLastXValue = 5d;

    //display variables
    String f_val="00 00 00 00 00 00 00 00 00",text_val="Place phone on Tag";
    byte b_val=0x01,dig_op=0x00;
    public final static String EXTRA_MESSAGE = "com.example.hello.MESSAGE";

    //my initialization
    final double B_Value = 4330.0;
    final double R0_Value = 100000.0;
    final double T0_Value = 298.15;
    final double K0_Temp = 273.15;
    final int CLEARNUM = 10;
    final String DIRNAME = "/NFC_record/";
    final String FILENAME = "temperature_history.txt";
    final String FILENAME_2 = "ADC0_history.txt";
    final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        text_view= (TextView) findViewById(R.id.textView);
        Value = (TextView) findViewById(R.id.Value_text);
        //Byte_text = (TextView) findViewById(R.id.Byte_text);
        adc0 = (TextView) findViewById(R.id.ADC0_text);
        adc1 = (TextView) findViewById(R.id.ADC1_text);
        adc2 = (TextView) findViewById(R.id.ADC2_text);
        temperature = (TextView) findViewById(R.id.temperature_text);
        b_val=0;
        init_display(f_val, b_val,ADC0,ADC1,ADC2,tempConv);
        increase = (Button) findViewById(R.id.increase);
        decrease = (Button) findViewById(R.id.decrease);
        //dig_out = (Button) findViewById(R.id.checkBox);

        // initialize file status: delete existed files
        File file = new File(path, FILENAME);
        if (file.exists()) {
            file.delete();
        }
        file = new File(path, FILENAME_2);
        if (file.exists()) {
            file.delete();
        }

        //graph initialization
        GraphView graph = (GraphView) findViewById(R.id.graph);
        GridLabelRenderer glr = graph.getGridLabelRenderer();
        glr.setPadding(80); // should allow for 3 digits to fit on screen
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);
        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter(nf, nf));

        mSeries = new LineGraphSeries<>();
        mSeries.setColor(Color.BLUE);
        nSeries = new LineGraphSeries<>();
        nSeries.setColor(Color.RED);
        graph.addSeries(mSeries);
        graph.addSeries(nSeries);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(40);

        nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc == null) {
            text_view.setText("No NFC!!");
            text_val="No NFC!!";}
        if (!nfc.isEnabled()) {
            text_view.setText("NFC is disabled.");
            text_val="NFC is disabled.";
        }
        mpendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        /**
         * This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
         resolveIntent(intent);
    }


    @Override
    protected void onResume() {

        super.onResume();
        //Log.i("life cycle", "Called onResume");

        final ArrayList<String> temp_history=new ArrayList<String>();
        final ArrayList<String> ADC0_history=new ArrayList<String>();

        if (nfc != null) {
            //Declare intent filters to handle the intents that you want to intercept.
            IntentFilter tech_intent = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
            IntentFilter[] intentFiltersArray = new IntentFilter[] {tech_intent, };
            //Set up an array of tag technologies that your application wants to handle
            String[][] techListsArray = new String[][] { new String[] { NfcV.class.getName() } };
            //Enable foreground dispatch to stop restart of app on detection
            nfc.enableForegroundDispatch(this, mpendingIntent, intentFiltersArray, techListsArray);
        }

        mTimer = new Runnable() {
            @Override
            public void run() {
                //Log.i("life cycle","Update display");
                text_view.setText(text_val);

                int tmp2 = (Integer.parseInt(f_val.substring(3,4), 16)<<4)+ (Integer.parseInt(f_val.substring(4,5), 16))+ (Integer.parseInt(f_val.substring(6,7), 16) << 12) + (Integer.parseInt(f_val.substring(7,8), 16) << 8);
                ADC2 = (float)(0.45*tmp2)/(float)16384; //Thermistor
                int tmp1 = (Integer.parseInt(f_val.substring(9,10), 16)<<4)+ (Integer.parseInt(f_val.substring(10,11), 16))+ (Integer.parseInt(f_val.substring(12,13), 16) << 12) + (Integer.parseInt(f_val.substring(13,14), 16) << 8);
                ADC1 = (float)(0.45*tmp1)/(float)16384; //Reference
                int tmp = (Integer.parseInt(f_val.substring(15,16), 16)<<4)+ (Integer.parseInt(f_val.substring(16,17), 16))+ (Integer.parseInt(f_val.substring(18,19), 16) << 12) + (Integer.parseInt(f_val.substring(19,20), 16) << 8);
                ADC0 = (float)(0.9*tmp)/(float)16384; //Light Sensor

                tempConv = 0;
                if (tmp2!=0){
                    //tempConv = (float) ((((((tmp1 * 0.9) / 16384.0) / 2.0) / 0.0000024) * 8738.13) / tmp2); // equals below
                    tempConv = (float) (ADC1 / ADC2 * R0_Value); // ADC1 and ADC2 is upside down, don't know why
                    //tempConv = (float) ((B_Value / (Math.log10(tempConv / (R0_Value * Math.exp((-B_Value) /T0_Value))) / Math.log10(2.718))) - K0_Temp); // equals below
                    tempConv = (float) ((1.0 / ((((Math.log10(tempConv / R0_Value) / Math.log10(2.718))) / B_Value) + (1.0 /T0_Value))) - K0_Temp);
                }

                //RTD Resistance (we can count temperature by this value though, instead of using the B-value formula, it may contains exponantial )
                //y = 0.0018(x-30) + 1.134 (x-temp, y-r) //x = (y-1.134) / 0.0018 + 30
                /*float tempConv;
                if ( tmp1!=0 ){
                    tempConv = (float)((tmp2 / tmp1) * R0_Value); //(Maybe should change to: ADC1/ADC2*R0_Value)
                    tempConv = (float)((tempConv-1.134) / 0.0018 + 30);}
                else
                    tempConv = 0;*/

                // Renew the value and the GraphView
                init_display(f_val,b_val,ADC0,ADC1,ADC2,tempConv);
                if(b_val>0) {
                    graphLastXValue += 1d;
                    //if ( !Float.isNaN(tempConv) )
                    mSeries.appendData(new DataPoint(graphLastXValue, tempConv), true, 40);
                    nSeries.appendData(new DataPoint(graphLastXValue, ADC1*100), true, 40);
                }

                // Store and Dump Temperature Record (remember to give the permission to the app on phone)
                if(graphLastXValue%10 == 0) {
                    ADC0_history.add(Double.toString(ADC0));
                    temp_history.add(Double.toString(tempConv));
                    if (temp_history.size() >= CLEARNUM) {
                        try {
                            // dump temperature to file (should use for loop if there are too many files to be dumped)
                            File file = new File(path, FILENAME);
                            FileOutputStream fOut = new FileOutputStream(file, true); // true is for append instead of open a new one
                            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
                            for (int i = 0; i < temp_history.size(); i++) {
                                myOutWriter.append((graphLastXValue/10-(9-i)) + "s ");
                                myOutWriter.append(temp_history.get(i));
                                myOutWriter.append("\n");
                            }
                            myOutWriter.close(); // must close and flush and then is able to write another,,don't know why
                            fOut.flush();
                            fOut.close();
                            // dump ADC0 to file
                            file = new File(path, FILENAME_2);
                            fOut = new FileOutputStream(file, true);
                            myOutWriter = new OutputStreamWriter(fOut);
                            for (int i = 0; i < ADC0_history.size(); i++) {
                                myOutWriter.append((graphLastXValue/10-(9-i)) + "s ");
                                myOutWriter.append(ADC0_history.get(i));
                                myOutWriter.append("\n");
                            }
                            myOutWriter.close();
                            fOut.flush();
                            fOut.close();
                        } catch (IOException e) {
                            Log.e("Exception", "File write failed: " + e.toString());
                        }
                        temp_history.clear();
                        ADC0_history.clear();
                    }
                }
                mHandler.postDelayed(this, 100);
            }
        };
        mHandler.postDelayed(mTimer, 300);
    }

    @Override
    protected void onPause() {
        mHandler.removeCallbacks(mTimer);
        super.onPause();
        //text_view.setText("tag disconnected!");
        //text_val="tag disconnected!";
        //Log.i("life cycle", "Called onPause");

        if (nfc != null) {
            nfc.disableForegroundDispatch(this);
        }

    }

    //communication and tag methods
    private Tag currentTag;
    private void resolveIntent(Intent intent) {
        String action = intent.getAction();
        //check if the tag is ISO15693 and display message
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            text_view.setText("Tag discovered!");
            text_val="Tag discovered!";
            //Log.i("life cycle", "NfcAdapter.ACTION_TECH_DISCOVERED");
            currentTag = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            new NfcVReaderTask().execute(currentTag);// read ADC data in background
         }
    }
    //parsing function
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }
    /**
     *
     * Background task for reading the data. Do not block the UI thread while reading.
     *
     */
    private class NfcVReaderTask extends AsyncTask<Tag, Void, String> {
        @Override
        protected void onPostExecute(String result) {
            //Log.i("Life cycle", "NFC thread start");
        }
        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];
            readTagData(tag);
            return null;
        }

    }
        //read data
    private void readTagData(Tag tag) {

        byte[] id = tag.getId();
        boolean techFound = false;
        for (String tech : tag.getTechList()) {

            // checking for NfcV
            if (tech.equals(NfcV.class.getName())) {
                techFound = true;


                // Get an instance of NfcV for the given tag:
                NfcV nfcv_senseTag = NfcV.get(tag);

                try {
                    nfcv_senseTag.connect();
                    text_val="Tag connected";
                }catch (IOException e) {
                    text_val="Tag connection lost";
                    return;
                }



                //read register test
                byte[] cmd = new byte[] {
                     //   (byte)0x18, // Always needed, everything after this 18 is sent over the air, response is given in the text box below
                        (byte)0x02, // Flags (always use same)
                        (byte)0x20, // ISO15693 command code, in this case it is Read Single Block
                        (byte)b_val, // Block number
                };

                byte[] systeminfo;
                try {
                systeminfo = nfcv_senseTag.transceive(cmd);
                }catch (IOException e) {
                    text_val="Tag transfer failed";
                    //Log.i("Tag data", "transceive failed");
                    return;
                }


                //Log.i("Tag data", "result= " + bytesToHex(systeminfo));

                //write  to block 2
                cmd = new byte[] {
                        //   (byte)0x18, // Always needed, everything after this 18 is sent over the air, response is given in the text box below
                        (byte)0x02, // Flags (always use same)
                        (byte)0x21, // ISO15693 command code, in this case it is Write Single Block
                        (byte)0x02, //block number
                        (byte)0x11, //reg1 Reference-ADC1 Configuration Register DECIMATION 12 BIT
                        (byte)0x11, //reg2 ADC2 Sensor Configuration Register
                        (byte)0x10, //reg3 ADC0 Sensor Configuration Register
                        (byte)0x00, //reg4 Internal Sensor Configuration Register
                        (byte)0x00, //reg5 Initial Delay Period Setup Register
                        (byte)0x00, //reg6  JTAG Enable Password Register
                        (byte)0x00, //reg7 Initial Delay Period Register
                        (byte)0x00, //reg8 Initial Delay Period Register
                };
                byte[] ack;
                try {
                    ack = nfcv_senseTag.transceive(cmd);
                }catch (IOException e) {
                    text_val="Tag transfer failed";
                    Log.i("Tag data", "transceive failed");
                    return;
                }
                //Log.i("Tag data", "ack= " + bytesToHex(ack));
                while(b_val>0) {
                    //write 01 00 04 00 01 01 00 40 to block 0
                    cmd = new byte[]{
                            //   (byte)0x18, // Always needed, everything after this 18 is sent over the air, response is given in the text box below
                            (byte) 0x02, // Flags (always use same)
                            (byte) 0x21, // ISO15693 command code, in this case it is Write Single Block
                            (byte) 0x00, //block number
                            (byte) 0x21, //Start bit is set, after this is written this starts the sampling process, interrupt enabled for On/Off
                            (byte) 0x00, //Status byte
                            (byte) 0x07, //Reference resisitor, thermistor, ADC0 sensor selected selected
                            (byte) 0x00, //Frequency register, this is do not care since only one sample or pass is done
                            (byte) 0x01, //only one pass is needed
                            (byte) 0x01, //No averaging selected
                            (byte) 0x0E, //Interrupt enabled, push pull active high options selected
                            (byte) 0x40, //Selected using thermistor
                    };
                    if(dig_op==1)
                        cmd[3]|=0x40;
                    ack = new byte[]{0x01};

                    try {
                        ack = nfcv_senseTag.transceive(cmd);
                    } catch (IOException e) {
                        text_val="Tag transfer failed";
                        //Log.i("Tag data", "transceive failed");
                        return;
                    }
                    //Log.i("Tag data", "ack= " + bytesToHex(ack));

                    //poll status byte 00
                    cmd = new byte[]{
                            //   (byte)0x18, // Always needed, everything after this 18 is sent over the air, response is given in the text box below
                            (byte) 0x02, // Flags (always use same)
                            (byte) 0x20, // ISO15693 command code, in this case it is Read Single Block
                            (byte) 0x00, // Block number
                    };
                    byte[] new_info;
                    do {
                        try {
                            new_info = nfcv_senseTag.transceive(cmd);
                        } catch (IOException e) {
                            text_val="Tag transfer failed"; // new version requires exception handling on each step
                            //Log.i("Tag data", "transceive failed");
                            return;
                        }

                    } while (new_info[2] != 0x02);

                    //Read data on 09
                    cmd = new byte[]{
                            //   (byte)0x18, // Always needed, everything after this 18 is sent over the air, response is given in the text box below
                            (byte) 0x02, // Flags (always use same)
                            (byte) 0x20, // ISO15693 command code, in this case it is Read Single Block
                            (byte) 0x09, // Block number
                    };

                    byte[] reading;
                    try {
                        reading = nfcv_senseTag.transceive(cmd);
                       //Value.setText("Value: " + bytesToHex(reading) );
                    } catch (IOException e) {
                        text_val="Tag transfer failed"; // new version requires exception handling on each step
                        //Log.i("Tag data", "transceive failed");
                        return;
                    }
                    f_val = bytesToHex(reading);

                    //Log.i("Tag data", "ADC result= " + f_val);
                    //Read data confirmation on 0A
                    cmd = new byte[]{
                            //   (byte)0x18, // Always needed, everything after this 18 is sent over the air, response is given in the text box below
                            (byte) 0x02, // Flags (always use same)
                            (byte) 0x20, // ISO15693 command code, in this case it is Read Single Block
                            (byte) 0x0A, // Block number
                    };


                    try {
                        new_info = nfcv_senseTag.transceive(cmd);
                    } catch (IOException e) {
                        text_val="Tag transfer failed"; // new version requires exception handling on each step
                        //Log.i("Tag data", "transceive failed");
                        return;
                    }
                    //Log.i("Tag data", "ADC confirmation= " + bytesToHex(new_info));
                }
                try {
                    nfcv_senseTag.close();
                } catch (IOException e) {
                    //Log.i("Tag data", "transceive failed and stopped");
                    text_val="Tag disconnection failed";
                    return;
                }
                text_val="Tag disconnected";
            }
        }
    }

    //API calls
    public void decrease_func(View view)
    {
        b_val=0;
        init_display(f_val, b_val,ADC0,ADC1,ADC2,tempConv);
        //Log.i("decrease", "pressed button global");
    }

    public void increase_func(View view)
    {
        b_val=1;
        init_display(f_val, b_val,ADC0,ADC1,ADC2,tempConv);
        //Log.i("increase ", "pressed button global");
    }

    /*public void Digital_toggle(View view)
    {
     if(dig_op==0)
        dig_op=1;
     else
         dig_op=0;

    }*/

    /*public void intent_function(View view)
    {
        Intent intent = new Intent(this, DisplayMessageActivity.class);
        EditText editText = (EditText) findViewById(R.id.edit_message);
        String message = editText.getText().toString();
        intent.putExtra(EXTRA_MESSAGE, message);
        //Log.i("intent ", "received change screen intent");
        startActivity(intent);
    }*/

    protected void init_display(String disp_value,byte disp_byte,float disp_ADC0,float disp_ADC1,float disp_ADC2, float disp_temperature)  {
        //display float
        Value.setText("Value: " + disp_value);
        //Display byte
        //Byte_text.setText("Status : " + ((disp_byte > 0) ? "running" : "stopped"));
        //display ADC0
        adc0.setText("ADC0: "+String.valueOf(disp_ADC0)+ " V");
        adc1.setText("ADC1: "+String.valueOf(disp_ADC1)+ " V");
        adc2.setText("ADC2: "+String.valueOf(disp_ADC2)+ " V");
        temperature.setText("Temperature: "+String.valueOf(disp_temperature)+ " °C");

    }



}
