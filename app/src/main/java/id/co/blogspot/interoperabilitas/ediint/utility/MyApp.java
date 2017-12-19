package id.co.blogspot.interoperabilitas.ediint.utility;

/**
 * Created by dawud_tan on 10/8/17.
 */

import android.app.Application;

import java.net.URLConnection;

public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        URLConnection.setContentHandlerFactory(mimetype -> {
            if (mimetype.startsWith("multipart/signed")) {
                return new MultipartSigned();
            }
            return null;
        });
    }
}