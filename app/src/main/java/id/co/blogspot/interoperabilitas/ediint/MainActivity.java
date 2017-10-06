package id.co.blogspot.interoperabilitas.ediint;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.JsonReader;
import android.util.Xml;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.nononsenseapps.filepicker.FilePickerActivity;
import com.sun.mail.dsn.DispositionNotification;
import com.sun.mail.dsn.MultipartReport;

import org.apache.commons.csv.CSVFormat;
import org.spongycastle.asn1.ASN1ObjectIdentifier;
import org.spongycastle.asn1.nist.NISTObjectIdentifiers;
import org.spongycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.spongycastle.cms.SignerInfoGenerator;
import org.spongycastle.cms.SignerInformation;
import org.spongycastle.cms.SignerInformationStore;
import org.spongycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.spongycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.spongycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.spongycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.spongycastle.mail.smime.SMIMESigned;
import org.spongycastle.mail.smime.SMIMESignedGenerator;
import org.spongycastle.operator.OutputEncryptor;
import org.spongycastle.util.encoders.Base64;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;

import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePartDataSource;
import javax.mail.internet.MimeUtility;
import javax.mail.util.ByteArrayDataSource;

import id.co.blogspot.interoperabilitas.ediint.utility.MyPickerActivity;
import id.co.blogspot.interoperabilitas.ediint.utility.NoDefaultSpinner;
import id.co.blogspot.interoperabilitas.ediint.utility.NullOutputStream;
import id.co.blogspot.interoperabilitas.ediint.utility.PemUtils;
import keystore.KeyStoreManager;

/**
 * Created by dawud_tan on 11/11/16.
 */
public class MainActivity extends AppCompatActivity {
    static final int FILE_CODE = 1;
    public static HashMap<String, String> SIGNING_ALGORITHM = new HashMap<>();
    private static HashMap<String, ASN1ObjectIdentifier> ENCRYPTION_ALGORITHM = new HashMap<>();

    static {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        //https://tools.ietf.org/html/rfc5751#section-2.7
        //ContentEncryptionAlgorithmIdentifier
        //openssl rc2 68 bit tidak ada
        ENCRYPTION_ALGORITHM.put("DES_EDE3_CBC", PKCSObjectIdentifiers.des_EDE3_CBC);
        ENCRYPTION_ALGORITHM.put("AES128_CBC", NISTObjectIdentifiers.id_aes128_CBC);//default
        ENCRYPTION_ALGORITHM.put("AES192_CBC", NISTObjectIdentifiers.id_aes192_CBC);
        ENCRYPTION_ALGORITHM.put("AES256_CBC", NISTObjectIdentifiers.id_aes256_CBC);

        //https://tools.ietf.org/html/rfc5751#section-2.2
        //SignatureAlgorithmIdentifier
        //openssl syntax-nya masih pakai RFC3851, blm rfc 5751
        SIGNING_ALGORITHM.put("sha224withRSA", "sha224");
        SIGNING_ALGORITHM.put("sha224withRSAandMGF1", "sha224");
        SIGNING_ALGORITHM.put("sha256withRSA", "sha256");
        SIGNING_ALGORITHM.put("sha256withRSAandMGF1", "sha256");
        SIGNING_ALGORITHM.put("sha384withRSA", "sha384");
        SIGNING_ALGORITHM.put("sha384withRSAandMGF1", "sha384");
        SIGNING_ALGORITHM.put("sha512withRSA", "sha512");
    }

    private CoordinatorLayout mCoordinatorLayout;
    private NoDefaultSpinner encAlgo, signAlgo, contentTypePesanImportirField;
    private EditText storePasswordField, storeFileField, fromField, as2FromField, as2ToField, subjectField, pesanImportir, alamatKepabeanan;
    private Button storeFileButton;
    private PrivateKey senderPrivateKey;
    private X509Certificate senderPublicKey;
    private X509Certificate recipientPublicKey;
    private String signatureAlgorithm;
    private ASN1ObjectIdentifier encryptionOID;
    private Handler uiThread = new Handler();

    private static byte[] _getAsciiBytes(final String sString) {
        final char[] aChars = sString.toCharArray();
        final int nLength = aChars.length;
        final byte[] ret = new byte[nLength];
        for (int i = 0; i < nLength; i++)
            ret[i] = (byte) aChars[i];
        return ret;
    }

    private String calculateAndStoreMIC(MimeBodyPart part) {
        //https://tools.ietf.org/html/rfc5751#section-2.1
        //DigestAlgorithmIdentifier
        HashMap<String, ASN1ObjectIdentifier> algoritmaDigest = new HashMap<>();
        algoritmaDigest.put("sha224", NISTObjectIdentifiers.id_sha224);
        algoritmaDigest.put("sha256", NISTObjectIdentifiers.id_sha256);
        algoritmaDigest.put("sha384", NISTObjectIdentifiers.id_sha384);
        algoritmaDigest.put("sha512", NISTObjectIdentifiers.id_sha512);
        try {
            String micAlg = SIGNING_ALGORITHM.get(signatureAlgorithm);
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
            StringBuffer micResult = new StringBuffer(new String(Base64.encode(aMIC)));
            micResult.append(", ").append(micAlg);
            return micResult.toString();
        } catch (Exception ex) {
            Snackbar.make(mCoordinatorLayout, ex.getMessage(), Snackbar.LENGTH_LONG).show();
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CODE && resultCode == RESULT_OK) {
            try {
                KeyStoreManager mKeyStoreManager = new KeyStoreManager("PKCS12");
                String storePassword = storePasswordField.getText().toString();
                if (storePassword.trim().length() < 1) {
                    storePasswordField.requestFocus();
                    throw new Exception("Store Password Kosong");
                }
                Uri uri = data.getData();
                mKeyStoreManager.loadKeyStore(getContentResolver().openInputStream(uri), storePasswordField.getText().toString().toCharArray());
                storeFileField.setText(uri.toString());
                this.senderPublicKey = mKeyStoreManager.getCertificate();
                this.senderPublicKey.checkValidity();
                this.senderPrivateKey = mKeyStoreManager.getPrivateKey("".toCharArray());
            } catch (Exception e) {
                Snackbar.make(mCoordinatorLayout, e.getMessage(), Snackbar.LENGTH_LONG).show();
            }
        } else {
            Snackbar.make(mCoordinatorLayout, "Tidak Jadi", Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //iklan
        MobileAds.initialize(this, "ca-app-pub-9974637005790818~8733239689");
        AdView mAdView = findViewById(R.id.adView);
        mAdView.loadAd(new AdRequest.Builder().build());
        //iklan

        //inisialisasi
        mCoordinatorLayout = findViewById(R.id.koordinatorLayout);
        storePasswordField = findViewById(R.id.storePasswordField);
        storeFileField = findViewById(R.id.storeFileField);
        alamatKepabeanan = findViewById(R.id.alamatKepabeanan);
        pesanImportir = findViewById(R.id.pesanImportir);
        storeFileButton = findViewById(R.id.storeFileButton);
        storeFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, MyPickerActivity.class);
                i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath() + "/Download");
                startActivityForResult(i, FILE_CODE);
            }
        });
        fromField = findViewById(R.id.fromField);
        as2ToField = findViewById(R.id.as2ToField);
        as2FromField = findViewById(R.id.as2FromField);
        subjectField = findViewById(R.id.subjectField);

        String[] contentTypes = new String[]{
                "text/plain",
                "text/csv",
                "application/json",
                "application/xml",
                "application/edifact",
                "application/edi-x12"
        };

        ArrayAdapter<String> ctAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, contentTypes);
        contentTypePesanImportirField = findViewById(R.id.contentTypePesanImportirField);
        contentTypePesanImportirField.setAdapter(ctAdapter);
        contentTypePesanImportirField.setSelection(4);//application/edifact default

        ArrayAdapter<String> encAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, ENCRYPTION_ALGORITHM.keySet().toArray(new String[ENCRYPTION_ALGORITHM.keySet().size()]));
        encAlgo = findViewById(R.id.contentEncryptionAlgorithmIdentifierField);
        encAlgo.setAdapter(encAdapter);

        ArrayAdapter<String> signAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, SIGNING_ALGORITHM.keySet().toArray(new String[SIGNING_ALGORITHM.keySet().size()]));
        signAlgo = findViewById(R.id.signatureAlgorithmIdentifierField);
        signAlgo.setAdapter(signAdapter);

        InputStream certIs = MainActivity.class.getResourceAsStream("/kepabeanan-pub.pem");
        try {
            this.recipientPublicKey = PemUtils.decodeCertificate(certIs);
        } catch (Exception ex) {
        } finally {
            try {
                certIs.close();
                certIs = null;
            } catch (IOException ex) {
            }
        }
        //inisialisasi
        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    //validasi form
                    if (storeFileField.getText().toString().trim().length() < 1) {
                        storeFileButton.requestFocus();
                        throw new Exception("Sertifikat belum dimuat");
                    }
                    if (encAlgo.getSelectedItem() == null) {
                        encAlgo.requestFocus();
                        throw new Exception("Algoritma Enkripsi belum dipilih");
                    }
                    if (signAlgo.getSelectedItem() == null) {
                        signAlgo.requestFocus();
                        throw new Exception("Algoritma Tanda Tangan belum dipilih");
                    }
                    if (contentTypePesanImportirField.getSelectedItem() == null) {
                        contentTypePesanImportirField.requestFocus();
                        throw new Exception("Content-Type: belum dipilih");
                    }
                    String tipeKonten = contentTypePesanImportirField.getSelectedItem().toString();
                    StringReader stringReader = new StringReader(pesanImportir.getText().toString());
                    if (tipeKonten.equals("application/xml")) {
                        try {
                            XmlPullParser parser = Xml.newPullParser();
                            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                            parser.setInput(stringReader);
                            parser.nextTag();
                            stringReader.close();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            throw new RuntimeException("bukan dokumen XML Valid");
                        }
                    }
                    if (tipeKonten.equals("application/json")) {
                        try {
                            JsonReader jsonReader = new JsonReader(stringReader);
                            jsonReader.hasNext();
                            stringReader.close();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            throw new RuntimeException("bukan dokumen JSON Valid");
                        }
                    }
                    if (tipeKonten.equals("text/csv")) {
                        try {
                            CSVFormat.RFC4180.parse(stringReader);
                            stringReader.close();
                        } catch (Exception ex) {
                            throw new RuntimeException("bukan dokumen CSV Valid");
                        }
                    }
                    //if (tipeKonten.equals("application/edifact")), smooks tdk bisa di android
                    //if (tipeKonten.equals("application/edi-x12")), tidak <em>aware</em> ttg ini
                    //validasi form

                    signatureAlgorithm = signAlgo.getSelectedItem().toString();
                    encryptionOID = ENCRYPTION_ALGORITHM.get(encAlgo.getSelectedItem());
                    new CallSynchronouslyTask().execute(
                            pesanImportir.getText().toString(),
                            tipeKonten,
                            alamatKepabeanan.getText().toString(),
                            signAlgo.getSelectedItem().toString(),
                            fromField.getText().toString(),
                            as2ToField.getText().toString(),
                            as2FromField.getText().toString(),
                            subjectField.getText().toString());
                } catch (Exception ex) {
                    Snackbar.make(mCoordinatorLayout, ex.getMessage(), Snackbar.LENGTH_LONG).show();
                    ex.printStackTrace();
                }
            }
        });
    }

    private class CallSynchronouslyTask extends AsyncTask<String, Void, String[]> {
        private String calcMIC;

        @Override
        protected String[] doInBackground(String... args) {
            try {
                String pesanImpor = args[0];
                InternetHeaders ih = new InternetHeaders();
                ih.addHeader("Content-Type", args[1].concat("; charset=UTF-8"));
                SMIMESignedGenerator gen = new SMIMESignedGenerator();
                SignerInfoGenerator signer = new JcaSimpleSignerInfoGeneratorBuilder()
                        .setProvider("SC")
                        .build(signatureAlgorithm, senderPrivateKey, senderPublicKey);
                gen.addSignerInfoGenerator(signer);
                //secara default, content-transfer-encoding base64
                //gen.setContentTransferEncoding("base64");
                MimeMultipart aSignedData = gen.generate(new MimeBodyPart(ih, pesanImpor.getBytes(StandardCharsets.UTF_8)));
                // Calculate MIC after sign was handled, because the
                // message data might change if compression before signing is active.
                calcMIC = calculateAndStoreMIC(new SMIMESigned(aSignedData).getContent());
                MimeBodyPart aTmpBody = new MimeBodyPart();
                aTmpBody.setContent(aSignedData);
                aTmpBody.setHeader("Content-Type", aSignedData.getContentType());
                OutputEncryptor encryptor = new JceCMSContentEncryptorBuilder(encryptionOID).setProvider("SC").build();
                JceKeyTransRecipientInfoGenerator infoGenerator = new JceKeyTransRecipientInfoGenerator(recipientPublicKey)
                        .setProvider("SC");
                SMIMEEnvelopedGenerator encgen = new SMIMEEnvelopedGenerator();
                encgen.addRecipientInfoGenerator(infoGenerator);
                //secara default, content-transfer-encoding base64
                //encgen.setContentTransferEncoding("base64");
                MimeBodyPart output = encgen.generate(aTmpBody, encryptor);
                String domain = args[2];
                Uri recipientAddress = Uri.parse(domain);
                HttpURLConnection con = (HttpURLConnection) new URL(domain).openConnection();
                con.setRequestMethod("POST");
                con.setDoInput(true);
                con.setDoOutput(true);
                con.setRequestProperty("Connection", "close");
                con.setRequestProperty("Mime-Version", "1.0");
                con.setRequestProperty("AS2-Version", "1.1");
                //Meminta Balasan secara Asinkron
                //con.setRequestProperty("Receipt-Delivery-Option", "http://balasan-MDN-asinkron.com:8080");
                con.setRequestProperty("Disposition-Notification-Options",
                        "signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, " + SIGNING_ALGORITHM.get(args[3]));
                con.setRequestProperty("Disposition-Notification-To",
                        args[4]);//ask receiving UA, to issue an MDN
                con.setRequestProperty("From",
                        args[4]);
                con.setRequestProperty("AS2-To",
                        args[5]);
                con.setRequestProperty("AS2-From",
                        args[6]);
                con.setRequestProperty("Subject",
                        args[7]);
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
                int postDataLength = dataToPost.length;
                con.setRequestProperty("Content-Length", Integer.toString(postDataLength));
                OutputStream os = con.getOutputStream();
                os.write(dataToPost);
                os.close();
                return new String[]{new Scanner(con.getInputStream()).useDelimiter("\\A").next(), con.getContentType()};
            } catch (final Exception ex) {
                uiThread.post(new Runnable() {
                    @Override
                    public void run() {
                        Snackbar.make(mCoordinatorLayout, ex.getMessage(), Snackbar.LENGTH_LONG).show();
                    }
                });
                ex.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(final String[] data) {
            try {
                ByteArrayDataSource ds = new ByteArrayDataSource(data[0], data[1]);
                MimeMultipart body = new MimeMultipart(ds);
                final MimeBodyPart mbp = (MimeBodyPart) body.getBodyPart(0);
                MultipartReport multipartReport = new MultipartReport(new MimePartDataSource(mbp));
                StringBuilder sb = new StringBuilder("<h5>Pesan</h5><p>");
                sb.append(multipartReport.getBodyPart(0).getContent());
                sb.append("</p><hr>");
                DispositionNotification dn = new DispositionNotification(multipartReport.getBodyPart(1).getInputStream());
                InternetHeaders ihir = dn.getNotifications();
                sb.append("<h5>Digest lokal</h5><p>");
                sb.append(calcMIC);
                sb.append("</p><hr>");
                sb.append("<h5>Digest remote</h5><p>");
                sb.append(ihir.getHeader("Received-Content-MIC")[0]);
                sb.append("</p><hr>");
                SMIMESigned signed = new SMIMESigned(body);
                SignerInformationStore signers = signed.getSignerInfos();
                SignerInformation signeri = signers.getSigners().iterator().next();
                boolean hasil = signeri.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider("SC").build(recipientPublicKey.getPublicKey()));
                String bls = sb.toString();
                if (!hasil)
                    throw new RuntimeException("TTD tidak valid");
                TanggapanKepabeananFragment tsf = TanggapanKepabeananFragment.newInstance(bls);
                tsf.show(getSupportFragmentManager(), "tanggapan_kepabeanan_fragment");
            } catch (Exception ex) {
                Snackbar.make(mCoordinatorLayout, ex.getMessage(), Snackbar.LENGTH_LONG).show();
                ex.printStackTrace();
            }
        }
    }
}