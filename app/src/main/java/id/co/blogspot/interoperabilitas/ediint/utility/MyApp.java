package id.co.blogspot.interoperabilitas.ediint.utility;

/**
 * Created by dawud_tan on 10/8/17.
 */

import android.support.multidex.MultiDexApplication;

import java.net.ContentHandler;
import java.net.ContentHandlerFactory;
import java.net.URLConnection;

public class MyApp extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        URLConnection.setContentHandlerFactory(new ContentHandlerFactory() {
            @Override
            public ContentHandler createContentHandler(String mimetype) {
                if (mimetype.startsWith("multipart/signed")) {
                    return new MultipartSigned();
                }
                return null;
            }
        });
    }
}