package org.jboss.resteasy.security;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Extract PrivateKey, PublicKey, and X509Certificate from a DER encoded byte array or file.  Usually
 * generated from openssl
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public final class DerUtils {
    static {
        BouncyIntegration.init();
    }

    private DerUtils() {
    }

    public static X509Certificate decodeCertificate(InputStream is) throws IOException, CertificateException, NoSuchProviderException {
        final CertificateFactory cf = CertificateFactory.getInstance("X.509", "SC");
        final X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
        is.close();

        return cert;
    }
}