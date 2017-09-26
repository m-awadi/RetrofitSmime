package id.co.blogspot.interoperabilitas.ediint;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.nononsenseapps.filepicker.FilePickerActivity;
import com.sun.mail.dsn.DispositionNotification;
import com.sun.mail.dsn.MultipartReport;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePartDataSource;
import javax.mail.util.ByteArrayDataSource;

import id.co.blogspot.interoperabilitas.ediint.antarmuka.ServiceContract;
import id.co.blogspot.interoperabilitas.ediint.domain.LineItem;
import id.co.blogspot.interoperabilitas.ediint.utility.MyPickerActivity;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import keystore.KeyStoreManager;
import keystore.LoginException;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.PemUtils;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.multipart_signed.MultipartSignedRequestBodyConverter;
import retrofit2.converter.pkcs7_mime.Pkcs7MimeRequestBodyConverter;

/**
 * Created by dawud_tan on 11/11/16.
 */
public class MainActivity extends AppCompatActivity {
    static final int FILE_CODE = 1;

    static {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    private CoordinatorLayout mCoordinatorLayout;
    private EditText storePasswordField, storeFileField, keyAliasField, namaProduk, alamatDomain;
    private Button storeFileButton;
    private Retrofit.Builder builder;
    private String username;
    private List<LineItem> produks;

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
                try {
                    this.username = keyAliasField.getText().toString();
                    OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
                    httpClient.addInterceptor(new MultipartSignedRequestBodyConverter(mKeyStoreManager.getPublicKey(), mKeyStoreManager.getPrivateKey("".toCharArray())));
                    InputStream certIs = MainActivity.class.getResourceAsStream("/penjual.pub");
                    try {
                        httpClient.addInterceptor(new Pkcs7MimeRequestBodyConverter(PemUtils.decodeCertificate(certIs)));
                    } catch (Exception ex) {
                    } finally {
                        try {
                            certIs.close();
                            certIs = null;
                        } catch (IOException ex) {
                        }
                    }
                    builder = new Retrofit.Builder()
                            .client(httpClient.build())
                            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                            .addConverterFactory(GsonConverterFactory.create());
                } catch (LoginException ex) {
                    Snackbar.make(mCoordinatorLayout, ex.getMessage(), Snackbar.LENGTH_LONG).show();
                }
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
        alamatDomain = findViewById(R.id.alamatDomain);
        namaProduk = findViewById(R.id.namaProduk);
        storeFileButton = findViewById(R.id.storeFileButton);
        storeFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, MyPickerActivity.class);
                i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());
                startActivityForResult(i, FILE_CODE);
            }
        });
        keyAliasField = findViewById(R.id.keyAliasField);
        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                produks = new ArrayList<>();
                produks.add(new LineItem("IDR", namaProduk.getText().toString(), "4", "8000", "2000"));
                produks.add(new LineItem("IDR", "Gillette Venus Razors (P&G)", "3", "12000", "4000"));
                produks.add(new LineItem("IDR", "Listerine (Warner-Lambert)", "5", "5000", "1000"));
                produks.add(new LineItem("IDR", "Oil of Olay ColorMoist Hazelnut No. 650 (P&G)", "1", "3000", "3000"));
                String domain = alamatDomain.getText().toString();
                Uri recipientAddress = Uri.parse(domain);
                builder.baseUrl(domain.endsWith("/") ? domain : domain + "/")
                        .build()
                        .create(ServiceContract.class)
                        .callSynchronously(
                                "<github-phax-as2-lib-24092017231318+0700-1102@OpenAS2A_OID_OpenAS2B_OID>",
                                "From OpenAS2A to OpenAS2B",
                                recipientAddress.getScheme() + "://" + recipientAddress.getAuthority(),
                                "OpenAS2A_OID",
                                "OpenAS2B_OID",
                                "as2msgs@openas2a.com",
                                "as2msgs@openas2a.com",
                                "signed-receipt-protocol=required, pkcs7-signature; signed-receipt-micalg=required, sha-1",
                                produks)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.io())
                        .subscribe(new Consumer<ResponseBody>() {
                            @Override
                            public void accept(ResponseBody responseBody) throws Exception {
                                try {
                                    ByteArrayDataSource ds = new ByteArrayDataSource(responseBody.bytes(), responseBody.contentType().toString());
                                    MimeMultipart body = new MimeMultipart(ds);
                                    MimeBodyPart mbp = (MimeBodyPart) body.getBodyPart(0);
                                    MultipartReport multipartReport = new MultipartReport(new MimePartDataSource(mbp));

                                    String report = (String) multipartReport.getBodyPart(0).getContent();

                                    DispositionNotification dn = new DispositionNotification(multipartReport.getBodyPart(1).getInputStream());
                                    InternetHeaders ih = dn.getNotifications();
                                    StringTokenizer dispTokens = new StringTokenizer(ih.getHeader("Disposition")[0], "/;:", false);
                                    dispTokens.nextToken();
                                    dispTokens.nextToken();

                                    String status = dispTokens.nextToken();

                                    String rcmic = ih.getHeader("Received-Content-MIC")[0].split(", ")[0];

                                    Snackbar.make(mCoordinatorLayout, status + " " + rcmic + " " + report, Snackbar.LENGTH_LONG).show();
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }, new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) throws Exception {
                                Snackbar.make(mCoordinatorLayout, throwable.getMessage(), Snackbar.LENGTH_LONG).show();
                            }
                        });
            }
        });
    }
}