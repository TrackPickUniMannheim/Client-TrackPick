package de.unima.ar.collector.sensors;

import android.content.ContentValues;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.unima.ar.collector.SensorDataCollectorService;
import de.unima.ar.collector.TCPClient;
import de.unima.ar.collector.controller.SQLDBController;
import de.unima.ar.collector.database.DatabaseHelper;
import de.unima.ar.collector.extended.Plotter;
import de.unima.ar.collector.shared.Settings;
import de.unima.ar.collector.shared.database.SQLTableName;
import de.unima.ar.collector.shared.util.DeviceID;
import de.unima.ar.collector.util.DBUtils;
import de.unima.ar.collector.util.PlotConfiguration;


/**
 * @author Fabian Kramm, Timo Sztyler, Nancy Kunath
 */
public class MagneticFieldSensorCollector extends SensorCollector
{
    private static final int      type       = 2;
    private static final String[] valueNames = new String[]{ "attr_x", "attr_y", "attr_z", "attr_time" };

    private static Map<String, Plotter>        plotters = new HashMap<>();
    private static Map<String, List<String[]>> cache    = new HashMap<>();

    private static TCPClient mTcpClient;
    public static String currentJson;


    public MagneticFieldSensorCollector(Sensor sensor)
    {
        super(sensor);

        // create new plotter
        List<String> devices = DatabaseHelper.getStringResultSet("SELECT device FROM " + SQLTableName.DEVICES, null);
        for(String device : devices) {
            MagneticFieldSensorCollector.createNewPlotter(device);
        }
    }


    @Override
    public void onAccuracyChanged(Sensor s, int i)
    {
    }


    @Override
    public void SensorChanged(float[] values, long time)
    {
        ContentValues newValues = new ContentValues();
        newValues.put(valueNames[0], values[0]);
        newValues.put(valueNames[1], values[1]);
        newValues.put(valueNames[2], values[2]);
        newValues.put(valueNames[3], time);

        String deviceID = DeviceID.get(SensorDataCollectorService.getInstance());
        if(Settings.STREAMING){
            MagneticFieldSensorCollector.writeSensorData(deviceID, newValues);
        } else{
            MagneticFieldSensorCollector.writeDBStorage(deviceID, newValues);
        }
        MagneticFieldSensorCollector.updateLivePlotter(deviceID, values);
    }


    @Override
    public Plotter getPlotter(String deviceID)
    {
        if(!plotters.containsKey(deviceID)) {
            MagneticFieldSensorCollector.createNewPlotter(deviceID);
        }

        return plotters.get(deviceID);
    }


    @Override
    public int getType()
    {
        return type;
    }


    public static void createNewPlotter(String deviceID)
    {
        PlotConfiguration levelPlot = new PlotConfiguration();
        levelPlot.plotName = "LevelPlot";
        levelPlot.rangeMin = -50;
        levelPlot.rangeMax = 50;
        levelPlot.rangeName = "microTesla";
        levelPlot.SeriesName = "Tesla";
        levelPlot.domainName = "Axis";
        levelPlot.domainValueNames = Arrays.copyOfRange(valueNames, 0, 3);
        levelPlot.tableName = SQLTableName.MAGNETIC;
        levelPlot.sensorName = "Magnetic Field";


        PlotConfiguration historyPlot = new PlotConfiguration();
        historyPlot.plotName = "HistoryPlot";
        historyPlot.rangeMin = -50;
        historyPlot.rangeMax = 50;
        historyPlot.domainMin = 0;
        historyPlot.domainMax = 50;
        historyPlot.rangeName = "microTesla";
        historyPlot.SeriesName = "Tesla";
        historyPlot.domainName = "Time";
        historyPlot.seriesValueNames = Arrays.copyOfRange(valueNames, 0, 3);

        Plotter plotter = new Plotter(deviceID, levelPlot, historyPlot);
        plotters.put(deviceID, plotter);
    }


    public static void updateLivePlotter(String deviceID, float[] values)
    {
        Plotter plotter = plotters.get(deviceID);
        if(plotter == null) {
            MagneticFieldSensorCollector.createNewPlotter(deviceID);
            plotter = plotters.get(deviceID);
        }

        plotter.setDynamicPlotData(values);
    }

    public static void createDBStorage(String deviceID)
    {
        String sqlTable = "CREATE TABLE IF NOT EXISTS " + SQLTableName.PREFIX + deviceID + SQLTableName.MAGNETIC + " (id INTEGER PRIMARY KEY, " + valueNames[3] + " INTEGER, " + valueNames[0] + " REAL, " + valueNames[1] + " REAL, " + valueNames[2] + " REAL)";
        SQLDBController.getInstance().execSQL(sqlTable);
    }

    public static void writeSensorData(String deviceID, ContentValues newValues)
    {
        //if(Settings.DATABASE_DIRECT_INSERT) {
        if(true){
            if (mTcpClient != null && mTcpClient.getMRun() != false) {
                JSONObject ObJson = new JSONObject();
                try {
                    ObJson.put("deviceID",deviceID);
                    ObJson.put("sensorType","magneticField");
                    JSONArray array = new JSONArray();
                    JSONObject values = new JSONObject();
                    values.put("timeStamp", newValues.getAsString("attr_time"));
                    values.put("x", newValues.getAsString("attr_x"));
                    values.put("y", newValues.getAsString("attr_y"));
                    values.put("z", newValues.getAsString("attr_z"));
                    array.put(values);
                    ObJson.put("data",array);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                currentJson = ObJson.toString();
                new MagneticFieldSensorCollector.SendTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
            return;
        } else{
            List<String[]> clone = DBUtils.manageCache(deviceID, cache, newValues, (Settings.DATABASE_CACHE_SIZE + type * 2));
            if(clone != null) {
                JSONObject ObJson = new JSONObject();
                try {
                    ObJson.put("deviceID",deviceID);
                    ObJson.put("sensorType","magneticField");
                    JSONArray array = new JSONArray();
                    JSONObject values = new JSONObject();
                    for (int i=0; i<clone.size(); i++) {
                        values.put("timeStamp", clone.get(i)[0].toString());
                        values.put("x", clone.get(i)[1].toString());
                        values.put("y", clone.get(i)[2].toString());
                        values.put("z", clone.get(i)[3].toString());
                        array.put(values);
                    }
                    ObJson.put("data",array);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if(mTcpClient!=null && mTcpClient.getMRun() != false) {
                    currentJson = ObJson.toString();
                    new MagneticFieldSensorCollector.SendTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            }
        }
    }

    public static void writeDBStorage(String deviceID, ContentValues newValues)
    {
        String tableName = SQLTableName.PREFIX + deviceID + SQLTableName.MAGNETIC;

        if(Settings.DATABASE_DIRECT_INSERT) {
            SQLDBController.getInstance().insert(tableName, null, newValues);
            return;
        }

        List<String[]> clone = DBUtils.manageCache(deviceID, cache, newValues, (Settings.DATABASE_CACHE_SIZE + type * 200));
        if(clone != null) {
            SQLDBController.getInstance().bulkInsert(tableName, clone);
        }
    }

    public static void flushDBCache(String deviceID)
    {
        DBUtils.flushCache(SQLTableName.MAGNETIC, cache, deviceID);
    }

    public void clearCache(String id) {
        cache.remove(id);
    }

    public static void openSocket(String deviceID){
        // connect to the server
        MagneticFieldSensorCollector.ConnectTask task = new MagneticFieldSensorCollector.ConnectTask();
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void closeSocket(String deviceID){
        // disconnect to the server
        mTcpClient.stopClient();
        //mTcpClient.deregister();
    }

    private static class ConnectTask extends AsyncTask<String,String,TCPClient> {

        @Override
        protected TCPClient doInBackground(String... message) {

            mTcpClient = new TCPClient();
            mTcpClient.run();

            //mTcpClient = TCPClient.getInstance();
            //mTcpClient.register();

            return null;
        }

    }

    private static class SendTask extends AsyncTask<String,String,TCPClient> {

        @Override
        protected TCPClient doInBackground(String... message) {

            mTcpClient.sendMessage(MagneticFieldSensorCollector.currentJson);

            return null;
        }

    }
}