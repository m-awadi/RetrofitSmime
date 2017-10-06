package keystore;

import org.spongycastle.openssl.jcajce.JcaPEMWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Created by dawud_tan on 11/11/16.
 */
public class KeyStoreManager {

    KeyStore keyStore;

    public KeyStoreManager(String tipe) throws LoginException {
        try {
            this.keyStore = KeyStore.getInstance(tipe);
        } catch (KeyStoreException ex) {
            throw new LoginException(ex.getMessage());
        }
    }

    public String getKey(Object ojek)
            throws LoginException {
        StringWriter stringOutStream = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(stringOutStream);
        try {
            pemWriter.writeObject(ojek);
            stringOutStream.flush();
            pemWriter.flush();
            return stringOutStream.toString();
        } catch (IOException ex) {
            throw new LoginException(ex.getMessage());
        } finally {
            try {
                pemWriter.close();
                stringOutStream.close();
            } catch (IOException ex) {
                throw new LoginException(ex.getMessage());
            }
        }
    }

    public int size() throws LoginException {
        try {
            return keyStore.size();
        } catch (KeyStoreException ex) {
            throw new LoginException(ex.getMessage());
        }
    }

    public void loadKeyStore(InputStream is, String storePassword) throws LoginException {
        try {
            keyStore.load(is, storePassword.toCharArray());
        } catch (IOException | NoSuchAlgorithmException | CertificateException ex) {
            if (ex.getMessage().startsWith("PKCS12 key store mac invalid")
                    || ex.getMessage().startsWith("Keystore was tampered")) {
                throw new LoginException("Store Password salah");
            } else {
                throw new LoginException(ex.getMessage());
            }
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                throw new LoginException(ex.getMessage());
            }
        }
    }

    public void loadKeyStore(InputStream is, char[] storePassword) throws LoginException {
        try {
            keyStore.load(is, storePassword);
        } catch (IOException | NoSuchAlgorithmException | CertificateException ex) {
            if (ex.getMessage().startsWith("PKCS12 key store mac invalid")
                    || ex.getMessage().startsWith("Keystore was tampered")) {
                throw new LoginException("Store Password salah");
            } else {
                throw new LoginException(ex.getMessage());
            }
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                throw new LoginException(ex.getMessage());
            }
        }
    }

    public void loadKeyStore(File file, String storePassword) throws LoginException {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            this.keyStore.load(is, storePassword.toCharArray());
        } catch (IOException | NoSuchAlgorithmException | CertificateException ex) {
            if (ex.getMessage().startsWith("PKCS12 key store mac invalid")
                    || ex.getMessage().startsWith("Keystore was tampered")) {
                throw new LoginException("Store Password salah");
            } else {
                throw new LoginException(ex.getMessage());
            }
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                throw new LoginException(ex.getMessage());
            }
        }
    }

    public void loadKeyStore(File file, char[] storePassword) throws LoginException {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            this.keyStore.load(is, storePassword);
        } catch (IOException | NoSuchAlgorithmException | CertificateException ex) {
            if (ex.getMessage().startsWith("PKCS12 key store mac invalid")
                    || ex.getMessage().startsWith("Keystore was tampered")) {
                throw new LoginException("Store Password salah");
            } else {
                throw new LoginException(ex.getMessage());
            }
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                throw new LoginException(ex.getMessage());
            }
        }
    }

    public void saveKeyStore(
            File storePath,
            String storePassword,
            String username,
            PrivateKey privateKey,
            String keyPassword,
            X509Certificate[] chain) throws LoginException {

        OutputStream fout = null;
        try {
            this.keyStore.load(null, storePassword.toCharArray());
            this.keyStore.setKeyEntry(username, privateKey, keyPassword.toCharArray(), chain);
            fout = new FileOutputStream(storePath);
            this.keyStore.store(fout, storePassword.toCharArray());
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException ex) {
            throw new LoginException(ex.getMessage());
        } finally {
            try {
                fout.flush();
                fout.close();
            } catch (IOException ex) {
                throw new LoginException(ex.getMessage());
            }
        }

    }

    public PrivateKey getPrivateKey(String keyPassword) throws LoginException {
        try {
            return (PrivateKey) this.keyStore.getKey(this.getUsername(), keyPassword.toCharArray());
        } catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException ex) {
            throw new LoginException(ex.getMessage());
        }
    }

    public PrivateKey getPrivateKey(char[] keyPassword) throws LoginException {
        try {
            return (PrivateKey) this.keyStore.getKey(this.getUsername(), keyPassword);
        } catch (UnrecoverableKeyException ex) {
            throw new LoginException("Key Password Salah");
        } catch (KeyStoreException | NoSuchAlgorithmException ex) {
            throw new LoginException(ex.getMessage());
        }
    }

    public String getUsername() throws LoginException {
        try {
            return keyStore.aliases().nextElement();
        } catch (KeyStoreException e) {
            throw new LoginException(e.getMessage());
        }
    }

    public X509Certificate getCertificate() throws LoginException {
        try {
            return (X509Certificate) this.keyStore.getCertificate(this.getUsername());
        } catch (KeyStoreException e) {
            throw new LoginException(e.getMessage());
        }
    }
}
