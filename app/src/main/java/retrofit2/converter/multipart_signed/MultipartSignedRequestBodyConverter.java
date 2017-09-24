package retrofit2.converter.multipart_signed;

import org.spongycastle.cms.SignerInfoGenerator;
import org.spongycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.spongycastle.mail.smime.SMIMESignedGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.mail.internet.MimeMultipart;

import retrofit2.converter.BouncyIntegration;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import retrofit2.converter.pkcs7_mime.Pkcs7MimeRequestBodyConverter;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class MultipartSignedRequestBodyConverter implements Interceptor {
    static {
        BouncyIntegration.init();
    }

    private X509Certificate senderPublicKey;
    private PrivateKey senderPrivateKey;
    private byte[] signedContent;

    public MultipartSignedRequestBodyConverter(X509Certificate senderPublicKey, PrivateKey senderPrivateKey) {
        this.senderPublicKey = senderPublicKey;
        this.senderPrivateKey = senderPrivateKey;
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
            SMIMESignedGenerator gen = new SMIMESignedGenerator();
            SignerInfoGenerator signer = new JcaSimpleSignerInfoGeneratorBuilder()
                    .setProvider("SC")
                    .build("SHA384WITHRSA", this.senderPrivateKey, this.senderPublicKey);
            gen.addSignerInfoGenerator(signer);
            final MimeMultipart mp = gen.generate(Pkcs7MimeRequestBodyConverter.createBodyPart(rb.contentType(), konten.toByteArray()));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            mp.writeTo(baos);
            signedContent = baos.toByteArray();
            Request signedRequest = new Request.Builder()
                    .url(originalRequest.url())
                    .header("AS2-From", originalRequest.header("AS2-From"))
                    .post(new RequestBody() {
                        @Override
                        public MediaType contentType() {
                            return MediaType.parse(mp.getContentType().replace("\r\n", "").replace("\t", " "));
                        }

                        @Override
                        public long contentLength() {
                            return signedContent.length;
                        }

                        @Override
                        public void writeTo(BufferedSink sink) throws IOException {
                            sink.write(signedContent);
                            sink.flush();
                        }
                    }).build();
            return chain.proceed(signedRequest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}