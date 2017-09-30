package retrofit2.converter.pkcs7_mime;

import org.spongycastle.asn1.ASN1ObjectIdentifier;
import org.spongycastle.asn1.nist.NISTObjectIdentifiers;
import org.spongycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.spongycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.spongycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.spongycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.spongycastle.operator.OutputEncryptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.HashMap;

import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import retrofit2.converter.BouncyIntegration;

//Content-Type: application/pkcs7-mime
public class Pkcs7MimeConverter implements Interceptor {
    public static HashMap<String, ASN1ObjectIdentifier> ENCRYPTION_ALGORITHM = new HashMap<>();

    static {
        BouncyIntegration.init();
        //https://tools.ietf.org/html/rfc5751#section-2.7
        //ContentEncryptionAlgorithmIdentifier
        ENCRYPTION_ALGORITHM.put("RC2_CBC 40", PKCSObjectIdentifiers.RC2_CBC);
        ENCRYPTION_ALGORITHM.put("RC2_CBC 68", PKCSObjectIdentifiers.RC2_CBC);
        ENCRYPTION_ALGORITHM.put("RC2_CBC 128", PKCSObjectIdentifiers.RC2_CBC);
        ENCRYPTION_ALGORITHM.put("DES_EDE3_CBC", PKCSObjectIdentifiers.des_EDE3_CBC);
        ENCRYPTION_ALGORITHM.put("AES128_CBC", NISTObjectIdentifiers.id_aes128_CBC);//default
        ENCRYPTION_ALGORITHM.put("AES192_CBC", NISTObjectIdentifiers.id_aes192_CBC);
        ENCRYPTION_ALGORITHM.put("AES256_CBC", NISTObjectIdentifiers.id_aes256_CBC);
    }

    private X509Certificate recipientPublicKey;
    private MimeBodyPart output;
    private ASN1ObjectIdentifier encryptionOID;
    private int keySize;

    public static MimeBodyPart createBodyPart(MediaType contentType, byte[] content) throws IOException, MessagingException {
        InternetHeaders ih = new InternetHeaders();
        ih.addHeader("Content-Type", contentType.toString());
        return new MimeBodyPart(ih, content);
    }

    public void setEncryptionOID(Object encAlgo) {
        this.encryptionOID = ENCRYPTION_ALGORITHM.get(encAlgo);
        String _encAlgo = encAlgo.toString();
        if (_encAlgo.startsWith("RC2_CBC")) {
            keySize = Integer.valueOf(_encAlgo.split("\\s+")[1]);
        }
    }

    public void setRecipientPublicKey(X509Certificate recipientPublicKey) {
        this.recipientPublicKey = recipientPublicKey;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        RequestBody rb = originalRequest.body();
        ByteArrayOutputStream konten = new ByteArrayOutputStream();
        BufferedSink bs = Okio.buffer(Okio.sink(konten));
        rb.writeTo(bs);
        bs.flush();

        try {
            OutputEncryptor encryptor;
            if (this.encryptionOID.equals(PKCSObjectIdentifiers.RC2_CBC)) {
                encryptor = new JceCMSContentEncryptorBuilder(this.encryptionOID, keySize).setProvider("SC").build();
            } else {
                encryptor = new JceCMSContentEncryptorBuilder(this.encryptionOID).setProvider("SC").build();
            }
            JceKeyTransRecipientInfoGenerator infoGenerator = new JceKeyTransRecipientInfoGenerator(this.recipientPublicKey)
                    .setProvider("SC");
            SMIMEEnvelopedGenerator gen = new SMIMEEnvelopedGenerator();
            gen.addRecipientInfoGenerator(infoGenerator);
            gen.setContentTransferEncoding("binary");
            MimeBodyPart _msg = createBodyPart(rb.contentType(), konten.toByteArray());
            output = gen.generate(_msg, encryptor);

            Request envelopedRequest = originalRequest.newBuilder()
                    //https://tools.ietf.org/html/rfc3851#section-3.2.1
                    //3.2.1.  The name and filename Parameters
                    .header("Content-Disposition", "attachment; filename=\"smime.p7m\"")
                    .post(new RequestBody() {
                        @Override
                        public MediaType contentType() {
                            try {
                                return MediaType.parse(output.getContentType());
                            } catch (MessagingException e) {
                                e.printStackTrace();
                            }
                            return null;
                        }

                        @Override
                        public long contentLength() throws IOException {
                            try {
                                return output.getSize();
                            } catch (MessagingException e) {
                                e.printStackTrace();
                            }
                            return -1;
                        }

                        @Override
                        public void writeTo(BufferedSink sink) throws IOException {
                            try {
                                sink.writeAll(Okio.buffer(Okio.source(output.getInputStream())));
                            } catch (MessagingException e) {
                                e.printStackTrace();
                            }
                        }
                    }).build();
            return chain.proceed(envelopedRequest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}