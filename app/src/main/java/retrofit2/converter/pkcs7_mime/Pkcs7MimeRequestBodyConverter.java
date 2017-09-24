package retrofit2.converter.pkcs7_mime;

import org.spongycastle.asn1.pkcs.PKCSObjectIdentifiers;
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

import retrofit2.converter.BouncyIntegration;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class Pkcs7MimeRequestBodyConverter implements Interceptor {

    static {
        BouncyIntegration.init();
    }

    private X509Certificate recipientPublicKey;
    private byte[] str;

    public Pkcs7MimeRequestBodyConverter(X509Certificate recipientPublicKey) {
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
            OutputEncryptor encryptor = new JceCMSContentEncryptorBuilder(PKCSObjectIdentifiers.des_EDE3_CBC)
                    .setProvider("SC")
                    .build();
            JceKeyTransRecipientInfoGenerator infoGenerator = new JceKeyTransRecipientInfoGenerator(this.recipientPublicKey);
            infoGenerator.setProvider("SC");
            SMIMEEnvelopedGenerator gen = new SMIMEEnvelopedGenerator();
            gen.addRecipientInfoGenerator(infoGenerator);
            MimeBodyPart _msg = createBodyPart(rb.contentType(), konten.toByteArray());
            MimeBodyPart output = gen.generate(_msg, encryptor);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            output.writeTo(baos);
            str = baos.toByteArray();

            Request envelopedRequest = new Request.Builder()
                    .url(originalRequest.url())
                    .header("AS2-From", originalRequest.header("AS2-From"))
                    .header("Content-Disposition", "attachment; filename=\"smime.p7m\"")
                    .post(new RequestBody() {
                        @Override
                        public MediaType contentType() {
                            return MediaType.parse("application/pkcs7-mime; smime-type=enveloped-data; name=\"smime.p7m\"");
                        }

                        @Override
                        public long contentLength() {
                            return str.length;
                        }

                        @Override
                        public void writeTo(BufferedSink sink) throws IOException {
                            sink.write(str);
                            sink.flush();
                        }
                    }).build();
            return chain.proceed(envelopedRequest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}