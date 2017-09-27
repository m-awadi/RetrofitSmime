package retrofit2.converter.pkcs7_mime;

import org.spongycastle.asn1.smime.SMIMECapabilities;
import org.spongycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.spongycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.spongycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.spongycastle.operator.OutputEncryptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.X509Certificate;

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

    static {
        BouncyIntegration.init();
    }

    private X509Certificate recipientPublicKey;
    private MimeBodyPart output;

    public Pkcs7MimeConverter(X509Certificate recipientPublicKey) {
        this.recipientPublicKey = recipientPublicKey;
    }

    public static MimeBodyPart createBodyPart(MediaType contentType, byte[] content) throws IOException, MessagingException {
        InternetHeaders ih = new InternetHeaders();
        ih.addHeader("Content-Type", contentType.toString());
        return new MimeBodyPart(ih, content);
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
            OutputEncryptor encryptor = new JceCMSContentEncryptorBuilder(SMIMECapabilities.dES_EDE3_CBC)
                    .setProvider("SC")
                    .build();
            JceKeyTransRecipientInfoGenerator infoGenerator = new JceKeyTransRecipientInfoGenerator(this.recipientPublicKey)
                    .setProvider("SC");
            SMIMEEnvelopedGenerator gen = new SMIMEEnvelopedGenerator();
            gen.addRecipientInfoGenerator(infoGenerator);
            gen.setContentTransferEncoding("binary");
            MimeBodyPart _msg = createBodyPart(rb.contentType(), konten.toByteArray());
            output = gen.generate(_msg, encryptor);

            Request envelopedRequest = originalRequest.newBuilder()
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