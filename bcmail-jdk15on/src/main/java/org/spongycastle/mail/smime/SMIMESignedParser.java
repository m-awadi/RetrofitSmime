package org.spongycastle.mail.smime;

import org.spongycastle.cms.CMSException;
import org.spongycastle.cms.CMSSignedDataParser;
import org.spongycastle.cms.CMSTypedStream;
import org.spongycastle.operator.DigestCalculatorProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

/**
 * general class for handling a pkcs7-signature message.
 * <p>
 * A simple example of usage - note, in the example below the validity of
 * the certificate isn't verified, just the fact that one of the certs
 * matches the given signer...
 * <p>
 * <pre>
 *  CertStore               certs = s.getCertificates("Collection", "SC");
 *  SignerInformationStore  signers = s.getSignerInfos();
 *  Collection              c = signers.getSigners();
 *  Iterator                it = c.iterator();
 *
 *  while (it.hasNext())
 *  {
 *      SignerInformation   signer = (SignerInformation)it.next();
 *      Collection          certCollection = certs.getCertificates(signer.getSID());
 *
 *      Iterator        certIt = certCollection.iterator();
 *      X509Certificate cert = (X509Certificate)certIt.next();
 *
 *      if (signer.verify(cert.getPublicKey()))
 *      {
 *          verified++;
 *      }
 *  }
 * </pre>
 * <p>
 * Note: if you are using this class with AS2 or some other protocol
 * that does not use 7bit as the default content transfer encoding you
 * will need to use the constructor that allows you to specify the default
 * content transfer encoding, such as "binary".
 * </p>
 */
public class SMIMESignedParser
        extends CMSSignedDataParser {
    static {
        CommandMap commandMap = CommandMap.getDefaultCommandMap();

        if (commandMap instanceof MailcapCommandMap) {
            final MailcapCommandMap mc = (MailcapCommandMap) commandMap;

            mc.addMailcap("application/pkcs7-signature;; x-java-content-handler=org.spongycastle.mail.smime.handlers.pkcs7_signature");
            mc.addMailcap("application/pkcs7-mime;; x-java-content-handler=org.spongycastle.mail.smime.handlers.pkcs7_mime");
            mc.addMailcap("application/x-pkcs7-signature;; x-java-content-handler=org.spongycastle.mail.smime.handlers.x_pkcs7_signature");
            mc.addMailcap("application/x-pkcs7-mime;; x-java-content-handler=org.spongycastle.mail.smime.handlers.x_pkcs7_mime");
            mc.addMailcap("multipart/signed;; x-java-content-handler=org.spongycastle.mail.smime.handlers.multipart_signed");

            AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                    CommandMap.setDefaultCommandMap(mc);

                    return null;
                }
            });
        }
    }

    Object message;
    MimeBodyPart content;

    /**
     * base constructor using a defaultContentTransferEncoding of 7bit. A temporary backing file
     * will be created for the signed data.
     *
     * @param digCalcProvider provider for digest calculators.
     * @param message         signed message with signature.
     * @throws MessagingException on an error extracting the signature or
     *                            otherwise processing the message.
     * @throws CMSException       if some other problem occurs.
     */
    public SMIMESignedParser(
            DigestCalculatorProvider digCalcProvider,
            MimeMultipart message)
            throws MessagingException, CMSException {
        this(digCalcProvider, message, getTmpFile());
    }

    /**
     * base constructor using a defaultContentTransferEncoding of 7bit and a specified backing file.
     *
     * @param digCalcProvider provider for digest calculators.
     * @param message         signed message with signature.
     * @param backingFile     the temporary file to use to back the signed data.
     * @throws MessagingException on an error extracting the signature or
     *                            otherwise processing the message.
     * @throws CMSException       if some other problem occurs.
     */
    public SMIMESignedParser(
            DigestCalculatorProvider digCalcProvider,
            MimeMultipart message,
            File backingFile)
            throws MessagingException, CMSException {
        this(digCalcProvider, message, "7bit", backingFile);
    }

    /**
     * base constructor with settable contentTransferEncoding and a specified backing file.
     *
     * @param digCalcProvider                provider for digest calculators.
     * @param message                        the signed message with signature.
     * @param defaultContentTransferEncoding new default to use.
     * @param backingFile                    the temporary file to use to back the signed data.
     * @throws MessagingException on an error extracting the signature or
     *                            otherwise processing the message.
     * @throws CMSException       if some other problem occurs.
     */
    public SMIMESignedParser(
            DigestCalculatorProvider digCalcProvider,
            MimeMultipart message,
            String defaultContentTransferEncoding,
            File backingFile)
            throws MessagingException, CMSException {
        super(digCalcProvider, getSignedInputStream(message.getBodyPart(0), defaultContentTransferEncoding, backingFile), getInputStream(message.getBodyPart(1)));

        this.message = message;
        this.content = (MimeBodyPart) message.getBodyPart(0);

        drainContent();
    }

    private static InputStream getInputStream(
            Part bodyPart)
            throws MessagingException {
        try {
            if (bodyPart.isMimeType("multipart/signed")) {
                throw new MessagingException("attempt to create signed data object from multipart content - use MimeMultipart constructor.");
            }

            return bodyPart.getInputStream();
        } catch (IOException e) {
            throw new MessagingException("can't extract input stream: " + e);
        }
    }

    private static File getTmpFile()
            throws MessagingException {
        try {
            return File.createTempFile("bcMail", ".mime");
        } catch (IOException e) {
            throw new MessagingException("can't extract input stream: " + e);
        }
    }

    private static CMSTypedStream getSignedInputStream(
            BodyPart bodyPart,
            String defaultContentTransferEncoding,
            File backingFile)
            throws MessagingException {
        try {
            OutputStream out = new BufferedOutputStream(new FileOutputStream(backingFile));

            SMIMEUtil.outputBodyPart(out, true, bodyPart, defaultContentTransferEncoding);

            out.close();

            InputStream in = new TemporaryFileInputStream(backingFile);

            return new CMSTypedStream(in);
        } catch (IOException e) {
            throw new MessagingException("can't extract input stream: " + e);
        }
    }

    /**
     * return the content that was signed.
     *
     * @return the signed body part in this message.
     */
    public MimeBodyPart getContent() {
        return content;
    }


    private void drainContent()
            throws CMSException {
        try {
            this.getSignedContent().drain();
        } catch (IOException e) {
            throw new CMSException("unable to read content for verification: " + e, e);
        }
    }

    private static class TemporaryFileInputStream
            extends BufferedInputStream {
        private final File _file;

        TemporaryFileInputStream(File file)
                throws FileNotFoundException {
            super(new FileInputStream(file));

            _file = file;
        }

        public void close()
                throws IOException {
            super.close();

            _file.delete();
        }
    }
}