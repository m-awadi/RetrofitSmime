package id.co.blogspot.interoperabilitas.ediint;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.nononsenseapps.filepicker.FilePickerActivity;

import org.jboss.resteasy.security.PemUtils;
import org.jboss.resteasy.security.smime.EnvelopedConverter;
import org.jboss.resteasy.security.smime.MultipartSignedConverter;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import id.co.blogspot.interoperabilitas.ediint.antarmuka.ServiceContract;
import id.co.blogspot.interoperabilitas.ediint.domain.LineItem;
import id.co.blogspot.interoperabilitas.ediint.utility.MyPickerActivity;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Action;
import io.reactivex.schedulers.Schedulers;
import keystore.KeyStoreManager;
import keystore.LoginException;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by dawud_tan on 11/11/16.
 */
public class MainActivity extends AppCompatActivity {
    static final int FILE_CODE = 1;

    static {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    private CoordinatorLayout mCoordinatorLayout;
    private ProgressDialog mProgressDialog;
    private KeyStoreManager mKeyStoreManager;
    private EditText storePasswordField, storeFileField, keyAliasField, namaProduk, alamatDomain;
    private Button storeFileButton;
    private X509Certificate serverPublicKey;
    private Retrofit.Builder builder;
    private String username;
    private List<LineItem> produks;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CODE && resultCode == RESULT_OK) {
            try {
                this.mKeyStoreManager = new KeyStoreManager("PKCS12");
                String storePassword = storePasswordField.getText().toString();
                if (storePassword.trim().length() < 1) {
                    storePasswordField.requestFocus();
                    throw new Exception("Store Password Kosong");
                }
                Uri uri = data.getData();
                this.mKeyStoreManager.loadKeyStore(getContentResolver().openInputStream(uri), storePasswordField.getText().toString().toCharArray());
                storeFileField.setText(uri.toString());
                keyAliasField.setText(this.mKeyStoreManager.getUsername());
                try {
                    this.username = keyAliasField.getText().toString();
                    OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
                    httpClient.addInterceptor(new MultipartSignedConverter(mKeyStoreManager.getPublicKey(), mKeyStoreManager.getPrivateKey("".toCharArray())));
                    InputStream certIs = MainActivity.class.getResourceAsStream("/penjual.pub");
                    try {
                        serverPublicKey = PemUtils.decodeCertificate(certIs);
                    } catch (Exception ex) {
                    } finally {
                        try {
                            certIs.close();
                            certIs = null;
                        } catch (IOException ex) {
                        }
                    }
                    httpClient.addInterceptor(new EnvelopedConverter(serverPublicKey));
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
        AdView mAdView = (AdView) findViewById(R.id.adView);
        mAdView.loadAd(new AdRequest.Builder().build());
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mCoordinatorLayout = (CoordinatorLayout) findViewById(R.id.koordinator);
        storePasswordField = (EditText) findViewById(R.id.storePasswordField);
        storeFileField = (EditText) findViewById(R.id.storeFileField);
        alamatDomain = (EditText) findViewById(R.id.alamatDomain);
        namaProduk = (EditText) findViewById(R.id.namaProduk);
        storeFileButton = (Button) findViewById(R.id.storeFileButton);
        storeFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, MyPickerActivity.class);
                i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());
                startActivityForResult(i, FILE_CODE);
            }
        });

        keyAliasField = (EditText) findViewById(R.id.keyAliasField);
        mProgressDialog = new ProgressDialog(MainActivity.this);
        mProgressDialog.setMessage("Mengirim Orderean!");
        mProgressDialog.setCancelable(false);
        mProgressDialog.setIndeterminate(true);

        produks = Arrays.asList(
                new LineItem("IDR", "Gillette Venus Razors (P&G)", "3", "12000", "4000"),
                new LineItem("IDR", "Listerine (Warner-Lambert)", "5", "5000", "1000"),
                new LineItem("IDR", "Oil of Olay ColorMoist Hazelnut No. 650 (P&G)", "1", "3000", "3000"));

        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mProgressDialog.show();
                produks.add(new LineItem("IDR", namaProduk.getText().toString(), "4", "8000", "2000"));
                String domain = alamatDomain.getText().toString();
                builder.baseUrl(domain.endsWith("/") ? domain : domain + "/")
                        .build()
                        .create(ServiceContract.class)
                        .send(username,
                                "http://as2.amazonsedi.com/999US_AS2_20150715190948",
                                "signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional,sha1",
                                "http://as2.amazonsedi.com/999US_AS2_20150715190948", produks)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.io())
                        .subscribe(new Action() {
                            @Override
                            public void run() throws Exception {
                                mProgressDialog.hide();
                                Snackbar.make(mCoordinatorLayout, "purchase order tersampaikan", Snackbar.LENGTH_LONG).show();
                            }
                        });
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
