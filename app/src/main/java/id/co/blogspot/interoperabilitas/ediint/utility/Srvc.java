package id.co.blogspot.interoperabilitas.ediint.utility;

import android.net.Uri;

import org.spongycastle.asn1.ASN1ObjectIdentifier;
import org.spongycastle.asn1.nist.NISTObjectIdentifiers;
import org.spongycastle.asn1.oiw.OIWObjectIdentifiers;
import org.spongycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.spongycastle.cert.jcajce.JcaCertStore;
import org.spongycastle.cms.RecipientInfoGenerator;
import org.spongycastle.cms.SignerInfoGenerator;
import org.spongycastle.cms.SignerInformation;
import org.spongycastle.cms.SignerInformationStore;
import org.spongycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.spongycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.spongycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.spongycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.spongycastle.mail.smime.SMIMESigned;
import org.spongycastle.mail.smime.SMIMESignedGenerator;
import org.spongycastle.operator.OutputEncryptor;
import org.spongycastle.util.encoders.Base64;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.mail.BodyPart;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

/**
 * Created by dawud_tan on 10/13/17.
 */

public class Srvc {
    private static ExecutorService es = Executors.newSingleThreadExecutor();

    private static byte[] _getAsciiBytes(final String sString) {
        final char[] aChars = sString.toCharArray();
        final int nLength = aChars.length;
        final byte[] ret = new byte[nLength];
        for (int i = 0; i < nLength; i++)
            ret[i] = (byte) aChars[i];
        return ret;
    }

    private static String calculateAndStoreMIC(MimeBodyPart part, String micAlg) throws Exception {
        //https://tools.ietf.org/html/rfc5751#section-2.1
        //DigestAlgorithmIdentifier
        HashMap<String, ASN1ObjectIdentifier> algoritmaDigest = new HashMap<>();
        algoritmaDigest.put("md5", PKCSObjectIdentifiers.md5);
        algoritmaDigest.put("sha1", OIWObjectIdentifiers.idSHA1);
        algoritmaDigest.put("sha256", NISTObjectIdentifiers.id_sha256);
        algoritmaDigest.put("sha384", NISTObjectIdentifiers.id_sha384);
        MessageDigest md = MessageDigest.getInstance(algoritmaDigest.get(micAlg).getId(), "SC");//perlu canonicalize
        // Start hashing the header
        final byte[] aCRLF = new byte[]{'\r', '\n'};
        final Enumeration<String> aHeaderLines = part.getAllHeaderLines();
        while (aHeaderLines.hasMoreElements()) {
            String h = aHeaderLines.nextElement();
            md.update(_getAsciiBytes(h));
            md.update(aCRLF);
        }
        // The CRLF separator between header and content
        md.update(aCRLF);
        // No need to canonicalize here - see issue https://github.com/phax/as2-lib/issues/12
        try (final DigestOutputStream aDOS = new DigestOutputStream(new NullOutputStream(), md);
             final OutputStream aOS = MimeUtility.encode(aDOS, part.getEncoding())) {
            part.getDataHandler().writeTo(aOS);
        }
        // Build result digest array
        final byte[] aMIC = md.digest();
        // Perform Base64 encoding and append algorithm ID
        StringBuilder micResult = new StringBuilder(new String(Base64.encode(aMIC)));
        micResult.append(", ").append(micAlg);
        return micResult.toString();

    }

    public static Future<String> CallSynchronous(final String signatureAlg,
                                                 final PrivateKey senderPrivateKey,
                                                 final X509Certificate senderPublicKey,
                                                 final X509Certificate recipientPublicKey,
                                                 final byte[] content,
                                                 final String contentType,
                                                 final String micAlg,
                                                 final ASN1ObjectIdentifier contentEncryptionOID,
                                                 final RecipientInfoGenerator recipientInfoGen,
                                                 final String alamatKepabeanan,
                                                 final String from,
                                                 final String as2to,
                                                 final String as2from,
                                                 final String subject) {

        return es.submit(() -> {
            InternetHeaders ih = new InternetHeaders();
            ih.addHeader("Content-Type", contentType.concat("; charset=UTF-8"));
            SMIMESignedGenerator gen = new SMIMESignedGenerator();
            SignerInfoGenerator signer = new JcaSimpleSignerInfoGeneratorBuilder()
                    .setProvider("SC")
                    .build(signatureAlg, senderPrivateKey, senderPublicKey);
            gen.addSignerInfoGenerator(signer);
            //secara default, content-transfer-encoding base64
            //gen.setContentTransferEncoding("base64");
            MimeBodyPart aTmpBody = null;
            SMIMESigned signedData = null;
            if (alamatKepabeanan.endsWith("as2-asp.net-core-2.0-web-api")) {
                ArrayList<X509Certificate> certList = new ArrayList<>();
                certList.add(senderPublicKey);
                JcaCertStore jcaCertStore = new JcaCertStore(certList);
                gen.addCertificates(jcaCertStore);
                aTmpBody = gen.generateEncapsulated(new MimeBodyPart(ih, content));
                signedData = new SMIMESigned(aTmpBody);
            } else {
                MimeMultipart aSignedData = gen.generate(new MimeBodyPart(ih, content));
                signedData = new SMIMESigned(aSignedData);
                aTmpBody = new MimeBodyPart();
                aTmpBody.setContent(aSignedData);
                aTmpBody.setHeader("Content-Type", aSignedData.getContentType());
            }
            // Calculate MIC after sign was handled, because the
            // message data might change if compression before signing is active.
            String calcMIC = calculateAndStoreMIC(signedData.getContent(), micAlg);
            OutputEncryptor encryptor = new JceCMSContentEncryptorBuilder(contentEncryptionOID).setProvider("SC").build();

            SMIMEEnvelopedGenerator encgen = new SMIMEEnvelopedGenerator();
            encgen.addRecipientInfoGenerator(recipientInfoGen);
            //secara default, content-transfer-encoding base64
            //encgen.setContentTransferEncoding("base64");
            MimeBodyPart output = encgen.generate(aTmpBody, encryptor);
            Uri recipientAddress = Uri.parse(alamatKepabeanan);
            HttpURLConnection con = (HttpURLConnection) new URL(alamatKepabeanan).openConnection();
            con.setRequestMethod("POST");
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setRequestProperty("Connection", "close");
            con.setRequestProperty("Mime-Version", "1.0");
            con.setRequestProperty("AS2-Version", "1.1");
            //Meminta Balasan secara Asinkron
            //con.setRequestProperty("Receipt-Delivery-Option", "http://balasan-MDN-asinkron.com:8080");

            con.setRequestProperty("Disposition-Notification-Options",
                    "signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, " + micAlg);

            con.setRequestProperty("Disposition-Notification-To",
                    from);//ask receiving UA, to issue an MDN receipt
            con.setRequestProperty("From",
                    from);
            con.setRequestProperty("AS2-To",
                    as2to);
            con.setRequestProperty("AS2-From",
                    as2from);
            con.setRequestProperty("Subject",
                    subject);
            con.setRequestProperty("Recipient-Address",
                    recipientAddress.getScheme() + "://" + recipientAddress.getAuthority());
            con.setRequestProperty("Message-Id",
                    "<github-dawud-tan-RetrofitSmime-" + new SimpleDateFormat("ddMMyyyyHHmmssZ").format(new Date()) + "-" + new Random().nextLong() + "@mycompanyAS2_mendelsontestAS2>");
            con.setRequestProperty("Content-Type",
                    output.getContentType());
            //https://tools.ietf.org/html/rfc3851#section-3.2.1
            //3.2.1.  The name and filename Parameters
            con.setRequestProperty("Content-Disposition",
                    "attachment; filename=\"smime.p7m\"");
            //https://tools.ietf.org/html/rfc4130#section-5.2.1
            //5.2.1.  Content-Transfer-Encoding Not Used in HTTP Transport
            //tidak tahu cara menangani Content-Transfer-Encoding = binary di php
            //bila fungsi openssl_pkcs7_* bisa diberi clue kalau inputan berbentuk DER, pasti bisa
            con.setRequestProperty("Content-Transfer-Encoding",
                    "base64");
            ByteArrayOutputStream temp = new ByteArrayOutputStream();
            output.writeTo(temp);//include header
            String postData = new String(temp.toByteArray()).split("\\r\\n?\\r\\n")[1];
            byte[] dataToPost = postData.getBytes(StandardCharsets.UTF_8);
            OutputStream os = con.getOutputStream();
            os.write(dataToPost);
            os.flush();
            os.close();
            MimeMultipart body = (MimeMultipart) con.getContent();
            MimeMultipart aReportParts = new MimeMultipart(body.getBodyPart(0).getDataHandler().getDataSource());
            StringBuilder sb = new StringBuilder("<h5>Pesan</h5><p>");
            BodyPart ksg = aReportParts.getBodyPart(0);
            sb.append(ksg.getContent());
            sb.append("</p><hr>");
            BodyPart headers = aReportParts.getBodyPart(1);
            String mdnEncoding = headers.getHeader("Content-Transfer-Encoding")[0];
            InternetHeaders ihir = new InternetHeaders(MimeUtility.decode(headers.getInputStream(), mdnEncoding));
            sb.append("<h5>Digest lokal</h5><p>");
            sb.append(calcMIC);
            sb.append("</p><hr>");
            sb.append("<h5>Digest remote</h5><p>");
            sb.append(ihir.getHeader("Received-Content-MIC")[0]);
            sb.append("</p><hr>");
            SignerInformationStore signers = new SMIMESigned(body).getSignerInfos();
            SignerInformation signeri = signers.getSigners().iterator().next();
            boolean hasil = signeri.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider("SC").build(recipientPublicKey.getPublicKey()));
            if (!hasil && !alamatKepabeanan.endsWith("as2-asp.net-core-2.0-web-api"))
                throw new RuntimeException("TTD tidak valid");
            return sb.toString();
        });
    }
}