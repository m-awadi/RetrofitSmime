package id.co.blogspot.interoperabilitas.ediint.utility;

import java.io.IOException;
import java.net.ContentHandler;
import java.net.URLConnection;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

/**
 * Created by dawud_tan on 10/9/17.
 */

public class MultipartSigned extends ContentHandler {

    @Override
    public Object getContent(URLConnection urlc) throws IOException {
        try {
            return new MimeMultipart(new ByteArrayDataSource(urlc.getInputStream(), urlc.getContentType()));
        } catch (MessagingException e) {
            e.printStackTrace();
        }
        return null;
    }
}