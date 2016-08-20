package com.fredluce.shutterspeedtester;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private final String TAG = MainActivity.class.getSimpleName();

    private DeviceConnection connection;

    class Item {
        public float speed;

        public Item(float s) {
            speed=s;
        }

        public boolean isValid() {
            return speed!=0;
        }

        public String getFractionalSpeed() {
            if (speed < 1000)
                return String.format(Locale.getDefault(),"1/%.2f s",1000/speed);
            else
                return String.format(Locale.getDefault(),"%.1f s",speed/1000);
        }
        public String getSpeed() {
            return String.format(Locale.getDefault(),"%.2f ms",speed);
        }

        @Override
        public String toString() {
            return getSpeed()+" - "+getFractionalSpeed();
        }
    }

    ArrayList<Item> list= new ArrayList<>();
    StandardDeviation stddev=new StandardDeviation();
    Mean mean=new Mean();

    private void onSpeedReceived(float df) {

        Item item=new Item(df);

        if (item.isValid()) {

            TextView tv=(TextView)findViewById(R.id.tvDelay);
            tv.setText(item.getSpeed());

            TextView tv2=(TextView)findViewById(R.id.tvSpeed);
            tv2.setText(item.getFractionalSpeed());

            lvAdapter.add(item);
            listView.smoothScrollToPosition(list.size());
            stddev.increment(item.speed);
            mean.increment(item.speed);

            TextView tv3=(TextView)findViewById(R.id.tvAvgSpeed);
            Item avgItem=new Item((float)mean.getResult());
            tv3.setText(String.format(Locale.getDefault(),"%s : %s", getString(R.string.mean), avgItem.toString()));

            TextView tv4=(TextView)findViewById(R.id.tvStddev);
            tv4.setText(String.format(Locale.getDefault(),"%s : %.2f ms", getString(R.string.stddev), stddev.getResult()));

        }


    }

    ListView listView;
    ArrayAdapter<Item> lvAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fabadd = (FloatingActionButton) findViewById(R.id.fabadd);

        fabadd.setOnClickListener(new View.OnClickListener() {
            @Override
            public synchronized void onClick(View view) {
                connection.reset();
                clear();
            }
        });

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connection.startup();
            }
        });

        listView=(ListView)findViewById(R.id.lvMeasures);
        lvAdapter = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1, list);
        listView.setAdapter(lvAdapter);

        setupDevice(false);
    }

    private boolean isMockDevice()
    {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPref.getBoolean("use_mock_connection",false);
    }

    private void setupDevice(boolean force)
    {
        boolean useMock=isMockDevice();
        if (connection!=null) {
            Class c = connection.getClass();
            if (force || (c==Shtr10DeviceConnection.class && useMock) || (c==MockDeviceConnection.class && !useMock)) {
                connection.stop();
                connection=null;
            }
        }
        if (connection==null) {
            if (useMock)
                connection = new MockDeviceConnection();
            else
                connection = new Shtr10DeviceConnection(this);

            connection.setListener(new DeviceConnectionListener() {
                @Override
                public void onReceiveData(final float value) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onSpeedReceived(value);
                        }
                    });
                }

                @Override
                public void logMessage(final String message) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showMessage(message);
                        }
                    });
                }
            });

            connection.startup();
            clear();

        }
    }

    private void clear() {
        TextView tv = (TextView)findViewById(R.id.tvDelay);
        tv.setText(R.string.perform_measurement);
        TextView tv2 = (TextView)findViewById(R.id.tvSpeed);
        tv2.setText("");
        TextView tv3 = (TextView)findViewById(R.id.tvStddev);
        tv3.setText("");
        TextView tv4 = (TextView)findViewById(R.id.tvAvgSpeed);
        tv4.setText("");

        list.clear();
        lvAdapter.clear();
        mean.clear();
        stddev.clear();
    }

    private void showMessage(String message) {
        Snackbar.make(MainActivity.this.findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent i = new Intent(this, SettingsActivity.class);
                startActivityForResult(i,1);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==1) {
            setupDevice(false);
        } else
            super.onActivityResult(requestCode, resultCode, data);

    }
}
