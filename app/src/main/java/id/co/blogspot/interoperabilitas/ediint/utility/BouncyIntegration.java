package id.co.blogspot.interoperabilitas.ediint.utility;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class BouncyIntegration {
    static {
        if (Security.getProvider("SC") == null) Security.addProvider(new BouncyCastleProvider());
    }

    public static void init() {
        // empty, the static class does it
    }
}