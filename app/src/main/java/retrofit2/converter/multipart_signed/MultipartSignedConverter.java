package retrofit2.converter.multipart_signed;

import com.sun.mail.dsn.DispositionNotification;
import com.sun.mail.dsn.MultipartReport;

import org.spongycastle.asn1.ASN1ObjectIdentifier;
import org.spongycastle.asn1.nist.NISTObjectIdentifiers;
import org.spongycastle.asn1.oiw.OIWObjectIdentifiers;
import org.spongycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.spongycastle.cms.SignerInfoGenerator;
import org.spongycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.spongycastle.mail.smime.SMIMESigned;
import org.spongycastle.mail.smime.SMIMESignedGenerator;
import org.spongycastle.mail.smime.util.CRLFOutputStream;
import org.spongycastle.util.encoders.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.StringTokenizer;

import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePartDataSource;
import javax.mail.util.ByteArrayDataSource;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import retrofit2.converter.BouncyIntegration;
import retrofit2.converter.pkcs7_mime.Pkcs7MimeConverter;

//Content-Type: multipart/signed
public class MultipartSignedConverter implements Interceptor {
    public static HashMap<String, String> SIGNING_ALGORITHM = new HashMap<>();

    static {
        BouncyIntegration.init();
        //https://tools.ietf.org/html/rfc5751#section-2.2
        //SignatureAlgorithmIdentifier
        SIGNING_ALGORITHM.put("md5withRSA", "md5");
        SIGNING_ALGORITHM.put("sha1withDSA", "sha1");
        SIGNING_ALGORITHM.put("sha1withRSA", "sha1");
        SIGNING_ALGORITHM.put("sha256withDSA", "sha256");
        SIGNING_ALGORITHM.put("sha256withRSA", "sha256");
        SIGNING_ALGORITHM.put("sha256withRSAandMGF1", "sha256");
    }

    private X509Certificate senderPublicKey;
    private PrivateKey senderPrivateKey;
    private byte[] signedContent;
    private String signatureAlgorithm;

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public void setSenderPublicKey(X509Certificate senderPublicKey) {
        this.senderPublicKey = senderPublicKey;
    }

    public void setSenderPrivateKey(PrivateKey senderPrivateKey) {
        this.senderPrivateKey = senderPrivateKey;
    }

    protected InputStream trimCRLFPrefix(byte[] data) {
        ByteArrayInputStream bIn = new ByteArrayInputStream(data);

        int scanPos = 0;
        int len = data.length;

        while (scanPos < (len - 1)) {
            if (new String(data, scanPos, 2).equals("\r\n")) {
                bIn.read();
                bIn.read();
                scanPos += 2;
            } else {
                return bIn;
            }
        }

        return bIn;
    }

    private String calculateMIC(MimeBodyPart part) {
        //https://tools.ietf.org/html/rfc5751#section-2.1
        //DigestAlgorithmIdentifier
        HashMap<String, ASN1ObjectIdentifier> algoritmaDigest = new HashMap<>();
        algoritmaDigest.put("md5", PKCSObjectIdentifiers.md5);
        algoritmaDigest.put("sha1", OIWObjectIdentifiers.idSHA1);
        algoritmaDigest.put("sha256", NISTObjectIdentifiers.id_sha256);
        try {
            String micAlg = SIGNING_ALGORITHM.get(signatureAlgorithm);
            MessageDigest md = MessageDigest.getInstance(algoritmaDigest.get(micAlg).getId(), "SC");
            // convert the Mime data to a byte array, then to an InputStream
            ByteArrayOutputStream bOut = new ByteArrayOutputStream();
            // Canonicalize the data if not binary content transfer encoding
            OutputStream os = new CRLFOutputStream(bOut);
            //include header= part.writeTo
            //exclude header= part.getInputStream()
            part.writeTo(os);
            byte[] data = bOut.toByteArray();
            InputStream bIn = trimCRLFPrefix(data);
            // calculate the hash of the data and mime header
            DigestInputStream digIn = new DigestInputStream(bIn, md);
            byte[] buf = new byte[4096];
            while (digIn.read(buf) >= 0) {
            }
            bOut.close();
            byte[] mic = digIn.getMessageDigest().digest();
            StringBuffer micResult = new StringBuffer(new String(Base64.encode(mic)));
            micResult.append(", ").append(micAlg);
            return micResult.toString();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
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
                    .build(this.signatureAlgorithm, this.senderPrivateKey, this.senderPublicKey);//hardcoded digest algo
            gen.addSignerInfoGenerator(signer);
            gen.setContentTransferEncoding("binary");
            final MimeMultipart mp = gen.generate(Pkcs7MimeConverter.createBodyPart(rb.contentType(), konten.toByteArray()));
            String calcMIC = calculateMIC(new SMIMESigned(mp).getContent());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            mp.writeTo(baos);
            signedContent = baos.toByteArray();
            Request signedRequest = originalRequest.newBuilder()
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
            Response response = chain.proceed(signedRequest);
            ResponseBody responseBody = response.body();
            ByteArrayDataSource ds = new ByteArrayDataSource(responseBody.bytes(), responseBody.contentType().toString());
            MimeMultipart body = new MimeMultipart(ds);
            MimeBodyPart mbp = (MimeBodyPart) body.getBodyPart(0);
            MultipartReport multipartReport = new MultipartReport(new MimePartDataSource(mbp));

            StringBuilder sb = new StringBuilder("{\"text\":\"");
            sb.append(multipartReport.getBodyPart(0).getContent());
            sb.append("\",\"action\":\"");
            DispositionNotification dn = new DispositionNotification(multipartReport.getBodyPart(1).getInputStream());
            InternetHeaders ih = dn.getNotifications();
            StringTokenizer dispTokens = new StringTokenizer(ih.getHeader("Disposition")[0], "/;:", false);
            sb.append(dispTokens.nextToken());
            sb.append("\",\"mdnAction\":\"");
            sb.append(dispTokens.nextToken());
            sb.append("\",\"status\":{\"status\":\"");
            sb.append(dispTokens.nextToken());
            if (dispTokens.hasMoreTokens()) {
                sb.append("\",\"statusModifier\":\"");
                sb.append(dispTokens.nextToken());
                if (dispTokens.hasMoreTokens()) {
                    sb.append("\",\"statusDescription\":\"");
                    sb.append(dispTokens.nextToken());
                    sb.append("\"}");
                } else {
                    sb.append("\"}");
                }
            } else {
                sb.append("\"}");
            }
            sb.append(",\"returnMIC\":\"");
            sb.append(ih.getHeader("Received-Content-MIC")[0]);
            sb.append("\",\"calcMIC\":\"");
            sb.append(calcMIC);
            sb.append("\"}");
            final byte[] balasan = sb.toString().getBytes(StandardCharsets.UTF_8);
            return response.newBuilder().body(new ResponseBody() {
                @Override
                public MediaType contentType() {
                    return MediaType.parse("application/json; charset=UTF-8");
                }

                @Override
                public long contentLength() {
                    return balasan.length;
                }

                @Override
                public BufferedSource source() {
                    return Okio.buffer(Okio.source(new ByteArrayInputStream(balasan)));
                }
            }).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}