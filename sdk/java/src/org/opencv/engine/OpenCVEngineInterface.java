package org.opencv.engine;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

/**
 * Interface for OpenCV Engine service.
 */
public interface OpenCVEngineInterface extends IInterface {
    
    /**
     * @return Returns service version.
     */
    int getEngineVersion() throws RemoteException;

    /**
     * Finds an installed OpenCV library.
     * @param version OpenCV version.
     * @return Returns path to OpenCV native libs or empty string if OpenCV can not be found.
     */
    String getLibPathByVersion(String version) throws RemoteException;

    /**
     * Tries to install defined version of OpenCV from Google Play Market.
     * @param version OpenCV version.
     * @return Returns true if installation was successful or OpenCV package has been already installed.
     */
    boolean installVersion(String version) throws RemoteException;

    /**
     * Returns list of libraries in loading order, separated by semicolon.
     * @param version OpenCV version.
     * @return Returns names of OpenCV libraries, separated by semicolon.
     */
    String getLibraryList(String version) throws RemoteException;

    /**
     * Local-side IPC implementation stub class.
     */
    abstract class Stub extends Binder implements OpenCVEngineInterface {
        private static final String DESCRIPTOR = "org.opencv.engine.OpenCVEngineInterface";

        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        public static OpenCVEngineInterface asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof OpenCVEngineInterface) {
                return (OpenCVEngineInterface) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        private static class Proxy implements OpenCVEngineInterface {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            @Override
            public int getEngineVersion() throws RemoteException {
                return 0;
            }

            @Override
            public String getLibPathByVersion(String version) throws RemoteException {
                return "";
            }

            @Override
            public boolean installVersion(String version) throws RemoteException {
                return false;
            }

            @Override
            public String getLibraryList(String version) throws RemoteException {
                return "";
            }
        }
    }
}
