package de.unima.ar.collector; /**
 * Created by Nancy Kunath on 29.03.2017.
 */
import android.util.Log;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

public class TCPClient {

    public static final String SERVER_IP = "10.0.2.2"; //later Server's IP Adress
    //public static final String SERVER_IP = "127.0.0.1"; //later Server's IP Adress
    public static final int SERVER_PORT = 4444;
    private String mServerMessage;
    private boolean mRun = false;
    private PrintWriter mBufferOut;
    private BufferedReader mBufferIn;

    public TCPClient() { }

    public void sendMessage(String message) {
        if (mBufferOut != null && !mBufferOut.checkError()) {
            mBufferOut.println(message);
            mBufferOut.flush();
        }
    }

    public void stopClient() {

        sendMessage("Close");

        mRun = false;

        if (mBufferOut != null) {
            mBufferOut.flush();
            mBufferOut.close();
        }

        mBufferIn = null;
        mBufferOut = null;
        mServerMessage = null;
    }

    public void run() {

        mRun = true;

        try {
            InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
            Log.e("TCP Client",serverAddr.toString());
            Log.e("TCP Client", "C: Connecting...");

            //create a socket to make the connection with the server
            Socket socket = new Socket(serverAddr, SERVER_PORT);

            Log.e("TCP Client",socket.toString());

            try {

                mBufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                mBufferIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // send first message
                sendMessage("Connected!");

                //in this while the client listens for the messages sent by the server
                while (mRun) {

                    mServerMessage = mBufferIn.readLine();

                    if (mServerMessage != null ) {
                        // here a received message should be handled
                    }
                }

            } catch (Exception e) {

                Log.i("TCP","Socket closed! (2)");

            } finally {
                socket.close();
            }

        } catch (Exception e) {

            Log.i("TCP","Socket closed! (1)");

        }

    }
}