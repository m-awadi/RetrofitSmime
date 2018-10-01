package id.co.blogspot.interoperabilitas.ediint;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;

import android.util.JsonReader;
import android.util.Xml;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;


import com.google.android.material.snackbar.Snackbar;
import com.nononsenseapps.filepicker.FilePickerActivity;
import com.startapp.android.publish.adsCommon.StartAppAd;
import com.startapp.android.publish.adsCommon.StartAppSDK;

import org.apache.commons.csv.CSVFormat;
import org.spongycastle.asn1.ASN1ObjectIdentifier;
import org.spongycastle.asn1.DERNull;
import org.spongycastle.asn1.DEROctetString;
import org.spongycastle.asn1.nist.NISTObjectIdentifiers;
import org.spongycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.spongycastle.asn1.pkcs.RSAESOAEPparams;
import org.spongycastle.asn1.sec.SECObjectIdentifiers;
import org.spongycastle.asn1.x509.AlgorithmIdentifier;
import org.spongycastle.cms.jcajce.JceKeyAgreeRecipientInfoGenerator;
import org.spongycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.concurrent.Future;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import id.co.blogspot.interoperabilitas.ediint.utility.MyPickerActivity;
import id.co.blogspot.interoperabilitas.ediint.utility.NoDefaultSpinner;
import id.co.blogspot.interoperabilitas.ediint.utility.PemUtils;
import id.co.blogspot.interoperabilitas.ediint.utility.Srvc;
import keystore.KeyStoreManager;

/**
 * Created by dawud_tan on 11/11/16.
 */
public class MainActivity extends AppCompatActivity {
    private static final int FILE_CODE = 1;
    private static HashMap<String, String> RSA_SIGNING_ALGORITHM = new HashMap<>();
    private static HashMap<String, String> EC_SIGNING_ALGORITHM = new HashMap<>();
    private static HashMap<String, ASN1ObjectIdentifier> RSA_CONTENT_ENCRYPTION_ALGORITHM = new HashMap<>();
    private static HashMap<String, ASN1ObjectIdentifier> EC_CONTENT_ENCRYPTION_ALGORITHM = new HashMap<>();
    private static HashMap<String, AlgorithmIdentifier> RSA_KEY_ENCRYPTION_ALGORITHM = new HashMap<>();
    private static HashMap<String, ASN1ObjectIdentifier> EC_KEY_ENCRYPTION_ALGORITHM = new HashMap<>();
    private static HashMap<ASN1ObjectIdentifier, ASN1ObjectIdentifier> KEY_WRAP_ALGORITHM = new HashMap<>();

    static {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        //https://tools.ietf.org/html/rfc5751#section-2.3
        //KeyEncryptionAlgorithmIdentifier
//UNTUK rsa
        RSA_KEY_ENCRYPTION_ALGORITHM.put("RSA Encryption", new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption));
        AlgorithmIdentifier hashFunc = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE);
        AlgorithmIdentifier maskGenFunc = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, hashFunc);
        AlgorithmIdentifier pSourceFunc = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_pSpecified, new DEROctetString(new byte[0]));
        RSAESOAEPparams parameters = new RSAESOAEPparams(hashFunc, maskGenFunc, pSourceFunc);
        RSA_KEY_ENCRYPTION_ALGORITHM.put("RSAES-OAEP", new AlgorithmIdentifier(PKCSObjectIdentifiers.id_RSAES_OAEP, parameters));
//UNTUK rsa

//UNTUK ELLIPTIC CURVE
        EC_KEY_ENCRYPTION_ALGORITHM.put("dhSinglePass-stdDH-sha256kdf-scheme", SECObjectIdentifiers.dhSinglePass_stdDH_sha256kdf_scheme);
        KEY_WRAP_ALGORITHM.put(SECObjectIdentifiers.dhSinglePass_stdDH_sha256kdf_scheme, NISTObjectIdentifiers.id_aes128_wrap);
        EC_KEY_ENCRYPTION_ALGORITHM.put("dhSinglePass-stdDH-sha384kdf-scheme", SECObjectIdentifiers.dhSinglePass_stdDH_sha384kdf_scheme);
        KEY_WRAP_ALGORITHM.put(SECObjectIdentifiers.dhSinglePass_stdDH_sha384kdf_scheme, NISTObjectIdentifiers.id_aes256_wrap);
        EC_KEY_ENCRYPTION_ALGORITHM.put("dhSinglePass-cofactorDH-sha256kdf-scheme", SECObjectIdentifiers.dhSinglePass_cofactorDH_sha256kdf_scheme);
        KEY_WRAP_ALGORITHM.put(SECObjectIdentifiers.dhSinglePass_cofactorDH_sha256kdf_scheme, NISTObjectIdentifiers.id_aes128_wrap);
        EC_KEY_ENCRYPTION_ALGORITHM.put("dhSinglePass-cofactorDH-sha384kdf-scheme", SECObjectIdentifiers.dhSinglePass_cofactorDH_sha384kdf_scheme);
        KEY_WRAP_ALGORITHM.put(SECObjectIdentifiers.dhSinglePass_cofactorDH_sha384kdf_scheme, NISTObjectIdentifiers.id_aes256_wrap);
//UNTUK ELLIPTIC CURVE

        //https://tools.ietf.org/html/rfc5751#section-2.7
        //ContentEncryptionAlgorithmIdentifier
        //openssl rc2 68 bit tidak ada, simetris
        RSA_CONTENT_ENCRYPTION_ALGORITHM.put("tripleDES", PKCSObjectIdentifiers.des_EDE3_CBC);
        RSA_CONTENT_ENCRYPTION_ALGORITHM.put("AES128_CBC", NISTObjectIdentifiers.id_aes128_CBC);//default
        RSA_CONTENT_ENCRYPTION_ALGORITHM.put("AES256_CBC", NISTObjectIdentifiers.id_aes256_CBC);

//untuk elliptic curve
        EC_CONTENT_ENCRYPTION_ALGORITHM.put("AES128_CBC", NISTObjectIdentifiers.id_aes128_CBC);//default
        EC_CONTENT_ENCRYPTION_ALGORITHM.put("AES256_CBC", NISTObjectIdentifiers.id_aes256_CBC);
        EC_CONTENT_ENCRYPTION_ALGORITHM.put("AES128_GCM", NISTObjectIdentifiers.id_aes128_GCM);//default
        EC_CONTENT_ENCRYPTION_ALGORITHM.put("AES256_GCM", NISTObjectIdentifiers.id_aes256_GCM);
//untuk elliptic curve

        //https://tools.ietf.org/html/rfc5751#section-2.2
        //SignatureAlgorithmIdentifier
        //openssl syntax-nya masih pakai RFC3851, blm rfc 5751
        RSA_SIGNING_ALGORITHM.put("md5withRSA", "md5");
        RSA_SIGNING_ALGORITHM.put("sha1withRSA", "sha1");
        RSA_SIGNING_ALGORITHM.put("sha1withRSAandMGF1", "sha1");
        RSA_SIGNING_ALGORITHM.put("sha256withRSA", "sha256");
        RSA_SIGNING_ALGORITHM.put("sha384withRSA", "sha384");
        RSA_SIGNING_ALGORITHM.put("sha256withRSAandMGF1", "sha256");
        RSA_SIGNING_ALGORITHM.put("sha384withRSAandMGF1", "sha384");

//untuk elliptic curve
        EC_SIGNING_ALGORITHM.put("sha256withECDSA", "sha256");
        EC_SIGNING_ALGORITHM.put("sha384withECDSA", "sha384");
//untuk elliptic curve
    }

    private CoordinatorLayout mCoordinatorLayout;
    private NoDefaultSpinner
            rsaContentEncryptionAlgorithmIdentifierField,
            ecContentEncryptionAlgorithmIdentifierField,
            rsaKeyEncryptionAlgorithmIdentifierField,
            ecKeyEncryptionAlgorithmIdentifierField,
            rsaSignatureAlgorithmIdentifierField,
            ecSignatureAlgorithmIdentifierField,
            contentTypePesanImportirField,
            alamatMitraDagang;
    private EditText storePasswordField, storeFileField, fromField, as2FromField, as2ToField, subjectField, pesanImportir, algoritmaPubKey;
    private Button storeFileButton;
    private PrivateKey senderPrivateKey;
    private X509Certificate senderPublicKey;
    private X509Certificate recipientPublicKey;
    private String recipientPublicKeyAlg;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CODE && resultCode == RESULT_OK) {
            InputStream certIs;
            try {
                KeyStoreManager mKeyStoreManager = new KeyStoreManager("PKCS12");
                String storePassword = this.storePasswordField.getText().toString();
                if (storePassword.trim().length() < 1) {
                    this.storePasswordField.requestFocus();
                    throw new Exception("Store Password Kosong");
                }
                Uri uri = data.getData();
                mKeyStoreManager.loadKeyStore(getContentResolver().openInputStream(uri), this.storePasswordField.getText().toString().toCharArray());
                this.storeFileField.setText(uri.toString());
                this.senderPublicKey = mKeyStoreManager.getCertificate();
                this.recipientPublicKeyAlg = this.senderPublicKey.getPublicKey().getAlgorithm();
                if (this.recipientPublicKeyAlg.equals("RSA")) {
                    certIs = MainActivity.class.getResourceAsStream("/kepabeanan.crt");//untuk SERTIFIKAT IMPORTIR
                    this.recipientPublicKey = PemUtils.decodeCertificate(certIs);
                    this.algoritmaPubKey.setText("RSA");
                    this.rsaContentEncryptionAlgorithmIdentifierField.setVisibility(View.VISIBLE);
                    this.rsaKeyEncryptionAlgorithmIdentifierField.setVisibility(View.VISIBLE);
                    this.rsaSignatureAlgorithmIdentifierField.setVisibility(View.VISIBLE);
                    this.ecContentEncryptionAlgorithmIdentifierField.setVisibility(View.GONE);
                    this.ecKeyEncryptionAlgorithmIdentifierField.setVisibility(View.GONE);
                    this.ecSignatureAlgorithmIdentifierField.setVisibility(View.GONE);
                } else {
                    certIs = MainActivity.class.getResourceAsStream("/manufaktur.crt");//untuk SERTIFIKAT PEDAGANG
                    this.recipientPublicKey = PemUtils.decodeCertificate(certIs);
                    this.algoritmaPubKey.setText("Elliptic Curve");
                    this.rsaContentEncryptionAlgorithmIdentifierField.setVisibility(View.GONE);
                    this.rsaKeyEncryptionAlgorithmIdentifierField.setVisibility(View.GONE);
                    this.rsaSignatureAlgorithmIdentifierField.setVisibility(View.GONE);
                    this.ecContentEncryptionAlgorithmIdentifierField.setVisibility(View.VISIBLE);
                    this.ecKeyEncryptionAlgorithmIdentifierField.setVisibility(View.VISIBLE);
                    this.ecSignatureAlgorithmIdentifierField.setVisibility(View.VISIBLE);
                }
                certIs.close();
                certIs = null;
                this.senderPrivateKey = mKeyStoreManager.getPrivateKey("".toCharArray());
            } catch (Exception e) {
                e.printStackTrace();
                Snackbar.make(this.mCoordinatorLayout, e.getMessage(), Snackbar.LENGTH_LONG).show();
            }
        } else {
            Snackbar.make(this.mCoordinatorLayout, "Tidak Jadi", Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //iklan
        StartAppSDK.init(this, "209924074", false);
        StartAppSDK.setUserConsent (this,
                "pas",
                System.currentTimeMillis(),
                false);
        StartAppAd.disableSplash();
        StartAppAd.disableAutoInterstitial();
        //iklan

        //inisialisasi
        mCoordinatorLayout = findViewById(R.id.koordinatorLayout);
        storePasswordField = findViewById(R.id.storePasswordField);
        storeFileField = findViewById(R.id.storeFileField);
        pesanImportir = findViewById(R.id.pesanImportir);
        storeFileButton = findViewById(R.id.storeFileButton);
        storeFileButton.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, MyPickerActivity.class);
            i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath() + "/Download");
            startActivityForResult(i, FILE_CODE);
        });
        fromField = findViewById(R.id.fromField);
        as2ToField = findViewById(R.id.as2ToField);
        algoritmaPubKey = findViewById(R.id.algoritmaPubKey);
        as2FromField = findViewById(R.id.as2FromField);
        subjectField = findViewById(R.id.subjectField);

        String[] alamatMitra = getResources().getStringArray(R.array.alamat_server);

        ArrayAdapter<String> mdAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, alamatMitra);
        alamatMitraDagang = findViewById(R.id.alamatMitraDagang);
        alamatMitraDagang.setAdapter(mdAdapter);

        String[] contentTypes = new String[]{
                "text/plain",
                "text/csv",
                "application/json",
                "application/xml",
                "application/edifact",
                "application/edi-x12",
                "application/x-java-serialized-object"
        };

        ArrayAdapter<String> ctAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, contentTypes);
        contentTypePesanImportirField = findViewById(R.id.contentTypePesanImportirField);
        contentTypePesanImportirField.setAdapter(ctAdapter);
        contentTypePesanImportirField.setSelection(4);//application/edifact default

        ArrayAdapter<String> rsaContentEncAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, RSA_CONTENT_ENCRYPTION_ALGORITHM.keySet().toArray(new String[RSA_CONTENT_ENCRYPTION_ALGORITHM.keySet().size()]));
        rsaContentEncryptionAlgorithmIdentifierField = findViewById(R.id.rsaContentEncryptionAlgorithmIdentifierField);
        rsaContentEncryptionAlgorithmIdentifierField.setAdapter(rsaContentEncAdapter);

        ArrayAdapter<String> ecContentEncAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, EC_CONTENT_ENCRYPTION_ALGORITHM.keySet().toArray(new String[EC_CONTENT_ENCRYPTION_ALGORITHM.keySet().size()]));
        ecContentEncryptionAlgorithmIdentifierField = findViewById(R.id.ecContentEncryptionAlgorithmIdentifierField);
        ecContentEncryptionAlgorithmIdentifierField.setAdapter(ecContentEncAdapter);

        ArrayAdapter<String> rsaKeyEncAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, RSA_KEY_ENCRYPTION_ALGORITHM.keySet().toArray(new String[RSA_KEY_ENCRYPTION_ALGORITHM.keySet().size()]));
        rsaKeyEncryptionAlgorithmIdentifierField = findViewById(R.id.rsaKeyEncryptionAlgorithmIdentifierField);
        rsaKeyEncryptionAlgorithmIdentifierField.setAdapter(rsaKeyEncAdapter);

        ArrayAdapter<String> ecKeyEncAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, EC_KEY_ENCRYPTION_ALGORITHM.keySet().toArray(new String[EC_KEY_ENCRYPTION_ALGORITHM.keySet().size()]));
        ecKeyEncryptionAlgorithmIdentifierField = findViewById(R.id.ecKeyEncryptionAlgorithmIdentifierField);
        ecKeyEncryptionAlgorithmIdentifierField.setAdapter(ecKeyEncAdapter);

        ArrayAdapter<String> rsaSignAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, RSA_SIGNING_ALGORITHM.keySet().toArray(new String[RSA_SIGNING_ALGORITHM.keySet().size()]));
        rsaSignatureAlgorithmIdentifierField = findViewById(R.id.rsaSignatureAlgorithmIdentifierField);
        rsaSignatureAlgorithmIdentifierField.setAdapter(rsaSignAdapter);

        ArrayAdapter<String> ecSignAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, EC_SIGNING_ALGORITHM.keySet().toArray(new String[EC_SIGNING_ALGORITHM.keySet().size()]));
        ecSignatureAlgorithmIdentifierField = findViewById(R.id.ecSignatureAlgorithmIdentifierField);
        ecSignatureAlgorithmIdentifierField.setAdapter(ecSignAdapter);

        //inisialisasi
        findViewById(R.id.fab).setOnClickListener(view -> {
            try {
                formValidation();
                Future<String> pesan;
                if (recipientPublicKeyAlg.equals("RSA")) {
                    pesan = Srvc.CallSynchronous(
                            rsaSignatureAlgorithmIdentifierField.getSelectedItem().toString(),
                            senderPrivateKey,
                            senderPublicKey,
                            recipientPublicKey,
                            pesanImportir.getText().toString().getBytes(StandardCharsets.UTF_8),
                            contentTypePesanImportirField.getSelectedItem().toString(),
                            RSA_SIGNING_ALGORITHM.get(rsaSignatureAlgorithmIdentifierField.getSelectedItem().toString()),
                            RSA_CONTENT_ENCRYPTION_ALGORITHM.get(rsaContentEncryptionAlgorithmIdentifierField.getSelectedItem().toString()),
                            new JceKeyTransRecipientInfoGenerator(recipientPublicKey,
                                    RSA_KEY_ENCRYPTION_ALGORITHM.get(rsaKeyEncryptionAlgorithmIdentifierField.getSelectedItem().toString())).setProvider("SC"),
                            alamatMitraDagang.getSelectedItem().toString(),
                            fromField.getText().toString(),
                            as2ToField.getText().toString(),
                            as2FromField.getText().toString(),
                            subjectField.getText().toString()
                    );
                } else {
                    ASN1ObjectIdentifier aoi = EC_KEY_ENCRYPTION_ALGORITHM.get(ecKeyEncryptionAlgorithmIdentifierField.getSelectedItem().toString());
                    JceKeyAgreeRecipientInfoGenerator rio = new JceKeyAgreeRecipientInfoGenerator(
                            aoi,
                            senderPrivateKey,
                            senderPublicKey.getPublicKey(),
                            KEY_WRAP_ALGORITHM.get(aoi))
                            .setProvider("SC");
                    rio.addRecipient(recipientPublicKey);
                    pesan = Srvc.CallSynchronous(
                            ecSignatureAlgorithmIdentifierField.getSelectedItem().toString(),
                            senderPrivateKey,
                            senderPublicKey,
                            recipientPublicKey,
                            pesanImportir.getText().toString().getBytes(StandardCharsets.UTF_8),
                            contentTypePesanImportirField.getSelectedItem().toString(),
                            EC_SIGNING_ALGORITHM.get(ecSignatureAlgorithmIdentifierField.getSelectedItem().toString()),
                            EC_CONTENT_ENCRYPTION_ALGORITHM.get(ecContentEncryptionAlgorithmIdentifierField.getSelectedItem().toString()),
                            rio,
                            alamatMitraDagang.getSelectedItem().toString(),
                            fromField.getText().toString(),
                            as2ToField.getText().toString(),
                            as2FromField.getText().toString(),
                            subjectField.getText().toString()
                    );
                }
                TanggapanKepabeananFragment tsf = TanggapanKepabeananFragment.newInstance(pesan.get());
                tsf.show(getSupportFragmentManager(), "tanggapan_kepabeanan_fragment");
            } catch (Exception ex) {
                Snackbar.make(mCoordinatorLayout, ex.getMessage(), Snackbar.LENGTH_LONG).show();
                ex.printStackTrace();
            }
        });
    }

    private void formValidation() throws Exception {
        //validasi form
        if (alamatMitraDagang.getSelectedItem() == null) {
            alamatMitraDagang.requestFocus();
            throw new Exception("Alamat Mitra Dagang belum ditentukan");
        }
        if (storeFileField.getText().toString().trim().length() < 1) {
            storeFileButton.requestFocus();
            throw new Exception("Sertifikat belum dimuat");
        }
        if (recipientPublicKeyAlg.equals("RSA")) {
            if (rsaContentEncryptionAlgorithmIdentifierField.getSelectedItem() == null) {
                rsaContentEncryptionAlgorithmIdentifierField.requestFocus();
                throw new Exception("Algoritma Enkripsi Konten belum dipilih");
            }
            if (rsaKeyEncryptionAlgorithmIdentifierField.getSelectedItem() == null) {
                rsaKeyEncryptionAlgorithmIdentifierField.requestFocus();
                throw new Exception("Algoritma Enkripsi Kunci belum dipilih");
            }
            if (rsaSignatureAlgorithmIdentifierField.getSelectedItem() == null) {
                rsaSignatureAlgorithmIdentifierField.requestFocus();
                throw new Exception("Algoritma Tanda Tangan belum dipilih");
            }
        } else {
            if (ecContentEncryptionAlgorithmIdentifierField.getSelectedItem() == null) {
                ecContentEncryptionAlgorithmIdentifierField.requestFocus();
                throw new Exception("Algoritma Enkripsi Konten belum dipilih");
            }
            if (ecKeyEncryptionAlgorithmIdentifierField.getSelectedItem() == null) {
                ecKeyEncryptionAlgorithmIdentifierField.requestFocus();
                throw new Exception("Algoritma Enkripsi Kunci belum dipilih");
            }
            if (ecSignatureAlgorithmIdentifierField.getSelectedItem() == null) {
                ecSignatureAlgorithmIdentifierField.requestFocus();
                throw new Exception("Algoritma Tanda Tangan belum dipilih");
            }
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
    }
}
