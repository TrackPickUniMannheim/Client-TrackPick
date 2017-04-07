package de.unima.ar.collector.sensors;

import android.content.ContentValues;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.util.Log;

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
        MagneticFieldSensorCollector.writeDBStorage(deviceID, newValues);
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
        // connect to the server
        Log.i("Magnetic Field","createDBStorage");
        ConnectTask task = new ConnectTask();

        //task.execute("");
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }


    public static void writeDBStorage(String deviceID, ContentValues newValues)
    {
        if(Settings.DATABASE_DIRECT_INSERT && mTcpClient!=null) {
            mTcpClient.sendMessage(deviceID + " Magnetic Field: " + newValues.toString());
            return;
        }

        List<String[]> clone = DBUtils.manageCache(deviceID, cache, newValues, (Settings.DATABASE_CACHE_SIZE + type * 200));
        if(clone != null) {
            //SQLDBController.getInstance().bulkInsert(tableName, clone);
        }
    }


    public static void flushDBCache(String deviceID)
    {
        mTcpClient.sendMessage(deviceID + " Magnetic Field: flushDBCache: " + cache.toString());


        //DBUtils.flushCache(SQLTableName.MAGNETIC, cache, deviceID);
    }

    private static class ConnectTask extends AsyncTask<String,String,TCPClient> {

        @Override
        protected TCPClient doInBackground(String... message) {

            mTcpClient = new TCPClient();
            mTcpClient.run();

            return null;
        }

    }
}