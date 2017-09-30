package id.co.blogspot.interoperabilitas.ediint;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.nononsenseapps.filepicker.FilePickerActivity;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import id.co.blogspot.interoperabilitas.ediint.antarmuka.ServiceContract;
import id.co.blogspot.interoperabilitas.ediint.domain.AS2MDN;
import id.co.blogspot.interoperabilitas.ediint.domain.LineItem;
import id.co.blogspot.interoperabilitas.ediint.utility.MyPickerActivity;
import id.co.blogspot.interoperabilitas.ediint.utility.NoDefaultSpinner;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import keystore.KeyStoreManager;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.PemUtils;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.multipart_signed.MultipartSignedConverter;
import retrofit2.converter.pkcs7_mime.Pkcs7MimeConverter;

/**
 * Created by dawud_tan on 11/11/16.
 */
public class MainActivity extends AppCompatActivity {
    static final int FILE_CODE = 1;

    static {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    private CoordinatorLayout mCoordinatorLayout;
    private NoDefaultSpinner encAlgo, signAlgo;
    private EditText storePasswordField, storeFileField, keyAliasField, namaProduk, alamatManufaktur;
    private Button storeFileButton;
    private Retrofit.Builder builder;
    private List<LineItem> produks;
    private MultipartSignedConverter multipartSignedConverter;
    private Pkcs7MimeConverter pkcs7MimeConverter;

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
                keyAliasField.setText(mKeyStoreManager.getUsername());
                multipartSignedConverter.setSenderPublicKey(mKeyStoreManager.getPublicKey());
                multipartSignedConverter.setSenderPrivateKey(mKeyStoreManager.getPrivateKey("".toCharArray()));
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
        MobileAds.initialize(this, "ca-app-pub-9974637005790818~8733239689");
        AdView mAdView = findViewById(R.id.adView);
        mAdView.loadAd(new AdRequest.Builder().build());
        mCoordinatorLayout = findViewById(R.id.koordinator);
        storePasswordField = findViewById(R.id.storePasswordField);
        storeFileField = findViewById(R.id.storeFileField);
        alamatManufaktur = findViewById(R.id.alamatManufaktur);
        namaProduk = findViewById(R.id.namaProduk);
        storeFileButton = findViewById(R.id.storeFileButton);
        storeFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, MyPickerActivity.class);
                i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath() + "/Download");
                startActivityForResult(i, FILE_CODE);
            }
        });
        keyAliasField = findViewById(R.id.keyAliasField);

        ArrayAdapter<String> encAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, Pkcs7MimeConverter.ENCRYPTION_ALGORITHM.keySet().toArray(new String[Pkcs7MimeConverter.ENCRYPTION_ALGORITHM.keySet().size()]));
        encAlgo = findViewById(R.id.contentEncryptionAlgorithmIdentifierField);
        encAlgo.setAdapter(encAdapter);

        ArrayAdapter<String> signAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1, MultipartSignedConverter.SIGNING_ALGORITHM.keySet().toArray(new String[MultipartSignedConverter.SIGNING_ALGORITHM.keySet().size()]));
        signAlgo = findViewById(R.id.signatureAlgorithmIdentifierField);
        signAlgo.setAdapter(signAdapter);

        multipartSignedConverter = new MultipartSignedConverter();
        pkcs7MimeConverter = new Pkcs7MimeConverter();

        InputStream certIs = MainActivity.class.getResourceAsStream("/manufaktur.pub");
        try {
            pkcs7MimeConverter.setRecipientPublicKey(PemUtils.decodeCertificate(certIs));
        } catch (Exception ex) {
        } finally {
            try {
                certIs.close();
                certIs = null;
            } catch (IOException ex) {
            }
        }
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        //Purchase Order is signed using sender Priv&Pub
        httpClient.addInterceptor(multipartSignedConverter);

        //the signed PO then encrypted using recipient Pub
        httpClient.addInterceptor(pkcs7MimeConverter);
        httpClient.addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY));

        builder = new Retrofit.Builder()
                .client(httpClient.build())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create());
        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
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
                    pkcs7MimeConverter.setEncryptionOID(encAlgo.getSelectedItem());
                    multipartSignedConverter.setSignatureAlgorithm(signAlgo.getSelectedItem().toString());

                    produks = new ArrayList<>();
                    produks.add(new LineItem("IDR", namaProduk.getText().toString() + " (Kainos Capital)", "4", "8000", "2000"));
                    produks.add(new LineItem("IDR", "Gillette Venus Razors (P&G)", "3", "12000", "4000"));
                    produks.add(new LineItem("IDR", "Listerine (Warner-Lambert)", "5", "5000", "1000"));
                    produks.add(new LineItem("IDR", "Oil of Olay ColorMoist Hazelnut No. 650 (P&G)", "1", "3000", "3000"));
                    String domain = alamatManufaktur.getText().toString();
                    Uri recipientAddress = Uri.parse(domain);
                    builder.baseUrl(domain.endsWith("/") ? domain : domain + "/")
                            .build()
                            .create(ServiceContract.class)
                            .callSynchronously(
                                    "<github-dawud-tan-RetrofitSmime-" + new SimpleDateFormat("ddMMyyyyHHmmssZ").format(new Date()) + "-" + new Random().nextLong() + "@mycompanyAS2_mendelsontestAS2>",
                                    "http://s.id/aKo",
                                    recipientAddress.getScheme() + "://" + recipientAddress.getAuthority(),
                                    "mycompanyAS2",
                                    "mendelsontestAS2",
                                    "muhammad.dawud91@gmail.com",
                                    "muhammad.dawud91@gmail.com",
                                    "signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, " + MultipartSignedConverter.SIGNING_ALGORITHM.get(signAlgo.getSelectedItem()),
                                    produks)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeOn(Schedulers.io())
                            .subscribe(new Consumer<AS2MDN>() {
                                @Override
                                public void accept(AS2MDN as2MDN) throws Exception {
                                    try {
                                        as2MDN.validateMIC();
                                        Snackbar.make(mCoordinatorLayout, "MIC is matched, received MIC:  " + as2MDN.returnMIC, Snackbar.LENGTH_LONG).show();
                                    } catch (Exception ex) {
                                        Snackbar.make(mCoordinatorLayout, ex.getMessage(), Snackbar.LENGTH_LONG).show();
                                    }
                                }
                            }, new Consumer<Throwable>() {
                                @Override
                                public void accept(Throwable throwable) throws Exception {
                                    Snackbar.make(mCoordinatorLayout, throwable.getMessage(), Snackbar.LENGTH_LONG).show();
                                }
                            });
                } catch (Exception ex) {
                    Snackbar.make(mCoordinatorLayout, ex.getMessage(), Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }
}