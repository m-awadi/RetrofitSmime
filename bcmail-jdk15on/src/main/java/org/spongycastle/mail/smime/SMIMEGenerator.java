package org.spongycastle.mail.smime;

import org.spongycastle.cms.CMSEnvelopedGenerator;
import org.spongycastle.util.Strings;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;

/**
 * super class of the various generators.
 */
public class SMIMEGenerator {
    private static Map BASE_CIPHER_NAMES = new HashMap();

    static {
        BASE_CIPHER_NAMES.put(CMSEnvelopedGenerator.DES_EDE3_CBC, "DESEDE");
        BASE_CIPHER_NAMES.put(CMSEnvelopedGenerator.AES128_CBC, "AES");
        BASE_CIPHER_NAMES.put(CMSEnvelopedGenerator.AES192_CBC, "AES");
        BASE_CIPHER_NAMES.put(CMSEnvelopedGenerator.AES256_CBC, "AES");
    }

    protected boolean useBase64 = true;
    protected String encoding = "base64";  // default sets base64

    /**
     * base constructor
     */
    protected SMIMEGenerator() {
    }

    /**
     * set the content-transfer-encoding for the CMS block (enveloped data, signature, etc...)  in the message.
     *
     * @param encoding the encoding to use, default "base64", use "binary" for a binary encoding.
     */
    public void setContentTransferEncoding(
            String encoding) {
        this.encoding = encoding;
        this.useBase64 = Strings.toLowerCase(encoding).equals("base64");
    }

    /**
     * Make sure we have a valid content body part - setting the headers
     * with defaults if neccessary.
     */
    protected MimeBodyPart makeContentBodyPart(
            MimeBodyPart content)
            throws SMIMEException {
        //
        // add the headers to the body part - if they are missing, in
        // the event they have already been set the content settings override
        // any defaults that might be set.
        //
        try {
            MimeMessage msg = new MimeMessage((Session) null);

            Enumeration e = content.getAllHeaders();

            msg.setDataHandler(content.getDataHandler());

            while (e.hasMoreElements()) {
                Header hdr = (Header) e.nextElement();

                msg.setHeader(hdr.getName(), hdr.getValue());
            }

            msg.saveChanges();

            //
            // we do this to make sure at least the default headers are
            // set in the body part.
            //
            e = msg.getAllHeaders();

            while (e.hasMoreElements()) {
                Header hdr = (Header) e.nextElement();

                if (Strings.toLowerCase(hdr.getName()).startsWith("content-")) {
                    content.setHeader(hdr.getName(), hdr.getValue());
                }
            }
        } catch (MessagingException e) {
            throw new SMIMEException("exception saving message state.", e);
        }

        return content;
    }
}