package org.spongycastle.mail.smime;

import org.spongycastle.asn1.ASN1EncodableVector;
import org.spongycastle.asn1.ASN1ObjectIdentifier;
import org.spongycastle.cms.CMSEnvelopedDataStreamGenerator;
import org.spongycastle.cms.CMSException;
import org.spongycastle.cms.RecipientInfoGenerator;
import org.spongycastle.operator.OutputEncryptor;

import java.io.IOException;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

/**
 * General class for generating a pkcs7-mime message.
 * <p>
 * A simple example of usage.
 * <p>
 * <pre>
 *      SMIMEEnvelopedGenerator  fact = new SMIMEEnvelopedGenerator();
 *
 *      fact.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(recipientCert).setProvider("SC"));
 *
 *      MimeBodyPart mp = fact.generate(content, new JceCMSContentEncryptorBuilder(CMSAlgorithm.RC2_CBC, 40).setProvider("SC").build());
 * </pre>
 * <p>
 * <b>Note:<b> Most clients expect the MimeBodyPart to be in a MimeMultipart
 * when it's sent.
 */
public class SMIMEEnvelopedGenerator
        extends SMIMEGenerator {

    private static final String ENCRYPTED_CONTENT_TYPE = "application/pkcs7-mime; name=\"smime.p7m\"; smime-type=enveloped-data";

    static {
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                CommandMap commandMap = CommandMap.getDefaultCommandMap();

                if (commandMap instanceof MailcapCommandMap) {
                    CommandMap.setDefaultCommandMap(addCommands((MailcapCommandMap) commandMap));
                }

                return null;
            }
        });
    }

    private EnvelopedGenerator fact;

    /**
     * base constructor
     */
    public SMIMEEnvelopedGenerator() {
        fact = new EnvelopedGenerator();
    }

    private static MailcapCommandMap addCommands(MailcapCommandMap mc) {
        mc.addMailcap("application/pkcs7-signature;; x-java-content-handler=org.spongycastle.mail.smime.handlers.pkcs7_signature");
        mc.addMailcap("application/pkcs7-mime;; x-java-content-handler=org.spongycastle.mail.smime.handlers.pkcs7_mime");
        mc.addMailcap("application/x-pkcs7-signature;; x-java-content-handler=org.spongycastle.mail.smime.handlers.x_pkcs7_signature");
        mc.addMailcap("application/x-pkcs7-mime;; x-java-content-handler=org.spongycastle.mail.smime.handlers.x_pkcs7_mime");
        mc.addMailcap("multipart/signed;; x-java-content-handler=org.spongycastle.mail.smime.handlers.multipart_signed");

        return mc;
    }

    /**
     * add a recipientInfoGenerator.
     */
    public void addRecipientInfoGenerator(
            RecipientInfoGenerator recipientInfoGen)
            throws IllegalArgumentException {
        fact.addRecipientInfoGenerator(recipientInfoGen);
    }

    /**
     * if we get here we expect the Mime body part to be well defined.
     */
    private MimeBodyPart make(
            MimeBodyPart content,
            OutputEncryptor encryptor)
            throws SMIMEException {
        try {
            MimeBodyPart data = new MimeBodyPart();

            data.setContent(new ContentEncryptor(content, encryptor), ENCRYPTED_CONTENT_TYPE);
            data.addHeader("Content-Type", ENCRYPTED_CONTENT_TYPE);
            data.addHeader("Content-Disposition", "attachment; filename=\"smime.p7m\"");
            data.addHeader("Content-Description", "S/MIME Encrypted Message");
            data.addHeader("Content-Transfer-Encoding", encoding);

            return data;
        } catch (MessagingException e) {
            throw new SMIMEException("exception putting multi-part together.", e);
        }
    }

    /**
     * generate an enveloped object that contains an SMIME Enveloped
     * object using the given content encryptor
     */
    public MimeBodyPart generate(
            MimeBodyPart content,
            OutputEncryptor encryptor)
            throws SMIMEException {
        return make(makeContentBodyPart(content), encryptor);
    }

    private static class WrappingIOException
            extends IOException {
        private Throwable cause;

        WrappingIOException(String msg, Throwable cause) {
            super(msg);

            this.cause = cause;
        }

        public Throwable getCause() {
            return cause;
        }
    }

    private class ContentEncryptor
            implements SMIMEStreamingProcessor {
        private final MimeBodyPart _content;
        private OutputEncryptor _encryptor;

        private boolean _firstTime = true;

        ContentEncryptor(
                MimeBodyPart content,
                OutputEncryptor encryptor) {
            _content = content;
            _encryptor = encryptor;
        }

        public void write(OutputStream out)
                throws IOException {
            OutputStream encrypted;

            try {
                if (_firstTime) {
                    encrypted = fact.open(out, _encryptor);

                    _firstTime = false;
                } else {
                    encrypted = fact.regenerate(out, _encryptor);
                }

                CommandMap commandMap = CommandMap.getDefaultCommandMap();

                if (commandMap instanceof MailcapCommandMap) {
                    _content.getDataHandler().setCommandMap(addCommands((MailcapCommandMap) commandMap));
                }

                _content.writeTo(encrypted);

                encrypted.close();
            } catch (MessagingException e) {
                throw new WrappingIOException(e.toString(), e);
            } catch (CMSException e) {
                throw new WrappingIOException(e.toString(), e);
            }
        }
    }

    private class EnvelopedGenerator
            extends CMSEnvelopedDataStreamGenerator {
        private ASN1ObjectIdentifier dataType;
        private ASN1EncodableVector recipientInfos;

        protected OutputStream open(
                ASN1ObjectIdentifier dataType,
                OutputStream out,
                ASN1EncodableVector recipientInfos,
                OutputEncryptor encryptor)
                throws IOException {
            this.dataType = dataType;
            this.recipientInfos = recipientInfos;

            return super.open(dataType, out, recipientInfos, encryptor);
        }

        OutputStream regenerate(
                OutputStream out,
                OutputEncryptor encryptor)
                throws IOException {
            return super.open(dataType, out, recipientInfos, encryptor);
        }
    }
}