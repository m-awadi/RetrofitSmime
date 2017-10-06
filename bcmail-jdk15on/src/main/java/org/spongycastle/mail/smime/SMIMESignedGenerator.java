package org.spongycastle.mail.smime;

import org.spongycastle.asn1.ASN1ObjectIdentifier;
import org.spongycastle.cms.CMSAlgorithm;
import org.spongycastle.cms.CMSException;
import org.spongycastle.cms.CMSSignedDataStreamGenerator;
import org.spongycastle.cms.SignerInfoGenerator;
import org.spongycastle.cms.SignerInformation;
import org.spongycastle.cms.SignerInformationStore;
import org.spongycastle.mail.smime.util.CRLFOutputStream;
import org.spongycastle.util.Store;

import java.io.IOException;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

/**
 * general class for generating a pkcs7-signature message.
 * <p>
 * A simple example of usage.
 * <p>
 * <pre>
 *      X509Certificate signCert = ...
 *      KeyPair         signKP = ...
 *
 *      List certList = new ArrayList();
 *
 *      certList.add(signCert);
 *
 *      Store certs = new JcaCertStore(certList);
 *
 *      SMIMESignedGenerator gen = new SMIMESignedGenerator();
 *
 *      gen.addSignerInfoGenerator(new JcaSimpleSignerInfoGeneratorBuilder().setProvider("SC").build("SHA1withRSA", signKP.getPrivate(), signCert));
 *
 *      gen.addCertificates(certs);
 *
 *      MimeMultipart       smime = fact.generate(content);
 * </pre>
 * <p>
 * Note 1: if you are using this class with AS2 or some other protocol
 * that does not use "7bit" as the default content transfer encoding you
 * will need to use the constructor that allows you to specify the default
 * content transfer encoding, such as "binary".
 * </p>
 * <p>
 * Note 2: between RFC 3851 and RFC 5751 the values used in the micalg parameter
 * for signed messages changed. We will accept both, but the default is now to use
 * RFC 5751. In the event you are dealing with an older style system you will also need
 * to use a constructor that sets the micalgs table and call it with RFC3851_MICALGS.
 * </p>
 */
public class SMIMESignedGenerator
        extends SMIMEGenerator {
    public static final Map RFC3851_MICALGS;
    public static final Map RFC5751_MICALGS;
    public static final Map STANDARD_MICALGS;
    private static final String DETACHED_SIGNATURE_TYPE = "application/pkcs7-signature; name=smime.p7s; smime-type=signed-data";

    static {
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                CommandMap commandMap = CommandMap.getDefaultCommandMap();

                if (commandMap instanceof MailcapCommandMap) {
                    CommandMap.setDefaultCommandMap(addCommands((MailcapCommandMap) commandMap));
                }

                return null;
            }
        });

        Map stdMicAlgs = new HashMap();

        stdMicAlgs.put(CMSAlgorithm.MD5, "md5");
        stdMicAlgs.put(CMSAlgorithm.SHA1, "sha-1");
        stdMicAlgs.put(CMSAlgorithm.SHA224, "sha-224");
        stdMicAlgs.put(CMSAlgorithm.SHA256, "sha-256");
        stdMicAlgs.put(CMSAlgorithm.SHA384, "sha-384");
        stdMicAlgs.put(CMSAlgorithm.SHA512, "sha-512");
        stdMicAlgs.put(CMSAlgorithm.GOST3411, "gostr3411-94");

        RFC5751_MICALGS = Collections.unmodifiableMap(stdMicAlgs);

        Map oldMicAlgs = new HashMap();

        oldMicAlgs.put(CMSAlgorithm.MD5, "md5");
        oldMicAlgs.put(CMSAlgorithm.SHA1, "sha1");
        oldMicAlgs.put(CMSAlgorithm.SHA224, "sha224");
        oldMicAlgs.put(CMSAlgorithm.SHA256, "sha256");
        oldMicAlgs.put(CMSAlgorithm.SHA384, "sha384");
        oldMicAlgs.put(CMSAlgorithm.SHA512, "sha512");
        oldMicAlgs.put(CMSAlgorithm.GOST3411, "gostr3411-94");

        RFC3851_MICALGS = Collections.unmodifiableMap(oldMicAlgs);

        STANDARD_MICALGS = RFC5751_MICALGS;
    }

    private final String defaultContentTransferEncoding;
    private final Map micAlgs;
    private List certStores = new ArrayList();
    private List crlStores = new ArrayList();
    private List attrCertStores = new ArrayList();
    private List signerInfoGens = new ArrayList();
    private List _signers = new ArrayList();
    private List _oldSigners = new ArrayList();

    /**
     * base constructor - default content transfer encoding 7bit
     */
    public SMIMESignedGenerator() {
        this("7bit", STANDARD_MICALGS);
    }


    /**
     * base constructor - default content transfer encoding explicitly set
     *
     * @param defaultContentTransferEncoding new default to use.
     * @param micAlgs                        a map of ANS1ObjectIdentifiers to strings hash algorithm names.
     */
    public SMIMESignedGenerator(
            String defaultContentTransferEncoding,
            Map micAlgs) {
        this.defaultContentTransferEncoding = defaultContentTransferEncoding;
        this.micAlgs = micAlgs;
    }

    private static MailcapCommandMap addCommands(MailcapCommandMap mc) {
        mc.addMailcap("application/pkcs7-signature;; x-java-content-handler=org.spongycastle.mail.smime.handlers.pkcs7_signature");
        mc.addMailcap("application/pkcs7-mime;; x-java-content-handler=org.spongycastle.mail.smime.handlers.pkcs7_mime");
        mc.addMailcap("application/x-pkcs7-signature;; x-java-content-handler=org.spongycastle.mail.smime.handlers.x_pkcs7_signature");
        mc.addMailcap("application/x-pkcs7-mime;; x-java-content-handler=org.spongycastle.mail.smime.handlers.x_pkcs7_mime");
        mc.addMailcap("multipart/signed;; x-java-content-handler=org.spongycastle.mail.smime.handlers.multipart_signed");

        return mc;
    }


    /**
     * @param sigInfoGen
     */
    public void addSignerInfoGenerator(SignerInfoGenerator sigInfoGen) {
        signerInfoGens.add(sigInfoGen);
    }

    private void addHashHeader(
            StringBuffer header,
            List signers) {
        int count = 0;

        //
        // build the hash header
        //
        Iterator it = signers.iterator();
        Set micAlgSet = new TreeSet();

        while (it.hasNext()) {
            Object signer = it.next();
            ASN1ObjectIdentifier digestOID;

            if (signer instanceof SignerInformation) {
                digestOID = ((SignerInformation) signer).getDigestAlgorithmID().getAlgorithm();
            } else {
                digestOID = ((SignerInfoGenerator) signer).getDigestAlgorithm().getAlgorithm();
            }

            String micAlg = (String) micAlgs.get(digestOID);

            if (micAlg == null) {
                micAlgSet.add("unknown");
            } else {
                micAlgSet.add(micAlg);
            }
        }

        it = micAlgSet.iterator();

        while (it.hasNext()) {
            String alg = (String) it.next();

            if (count == 0) {
                if (micAlgSet.size() != 1) {
                    header.append("; micalg=\"");
                } else {
                    header.append("; micalg=");
                }
            } else {
                header.append(',');
            }

            header.append(alg);

            count++;
        }

        if (count != 0) {
            if (micAlgSet.size() != 1) {
                header.append('\"');
            }
        }
    }

    private MimeMultipart make(
            MimeBodyPart content)
            throws SMIMEException {
        try {
            MimeBodyPart sig = new MimeBodyPart();

            sig.setContent(new ContentSigner(content, false), DETACHED_SIGNATURE_TYPE);
            sig.addHeader("Content-Type", DETACHED_SIGNATURE_TYPE);
            sig.addHeader("Content-Disposition", "attachment; filename=\"smime.p7s\"");
            sig.addHeader("Content-Description", "S/MIME Cryptographic Signature");
            sig.addHeader("Content-Transfer-Encoding", encoding);

            //
            // build the multipart header
            //
            StringBuffer header = new StringBuffer(
                    "signed; protocol=\"application/pkcs7-signature\"");

            List allSigners = new ArrayList(_signers);

            allSigners.addAll(_oldSigners);

            allSigners.addAll(signerInfoGens);

            addHashHeader(header, allSigners);

            MimeMultipart mm = new MimeMultipart(header.toString());

            mm.addBodyPart(content);
            mm.addBodyPart(sig);

            return mm;
        } catch (MessagingException e) {
            throw new SMIMEException("exception putting multi-part together.", e);
        }
    }

    public MimeMultipart generate(
            MimeBodyPart content)
            throws SMIMEException {
        return make(makeContentBodyPart(content));
    }

    private class ContentSigner
            implements SMIMEStreamingProcessor {
        private final MimeBodyPart content;
        private final boolean encapsulate;
        private final boolean noProvider;

        ContentSigner(
                MimeBodyPart content,
                boolean encapsulate) {
            this.content = content;
            this.encapsulate = encapsulate;
            this.noProvider = true;
        }

        protected CMSSignedDataStreamGenerator getGenerator()
                throws CMSException {
            CMSSignedDataStreamGenerator gen = new CMSSignedDataStreamGenerator();

            for (Iterator it = certStores.iterator(); it.hasNext(); ) {
                gen.addCertificates((Store) it.next());
            }

            for (Iterator it = crlStores.iterator(); it.hasNext(); ) {
                gen.addCRLs((Store) it.next());
            }

            for (Iterator it = attrCertStores.iterator(); it.hasNext(); ) {
                gen.addAttributeCertificates((Store) it.next());
            }

            for (Iterator it = signerInfoGens.iterator(); it.hasNext(); ) {
                gen.addSignerInfoGenerator((SignerInfoGenerator) it.next());
            }

            gen.addSigners(new SignerInformationStore(_oldSigners));

            return gen;
        }

        private void writeBodyPart(
                OutputStream out,
                MimeBodyPart bodyPart)
                throws IOException, MessagingException {
            if (SMIMEUtil.isMultipartContent(bodyPart)) {
                Multipart mp = (Multipart) bodyPart.getContent();
                ContentType contentType = new ContentType(mp.getContentType());
                String boundary = "--" + contentType.getParameter("boundary");

                SMIMEUtil.LineOutputStream lOut = new SMIMEUtil.LineOutputStream(out);

                Enumeration headers = bodyPart.getAllHeaderLines();
                while (headers.hasMoreElements()) {
                    lOut.writeln((String) headers.nextElement());
                }

                lOut.writeln();      // CRLF separator

                SMIMEUtil.outputPreamble(lOut, bodyPart, boundary);

                for (int i = 0; i < mp.getCount(); i++) {
                    lOut.writeln(boundary);
                    writeBodyPart(out, (MimeBodyPart) mp.getBodyPart(i));
                    lOut.writeln();       // CRLF terminator
                }

                lOut.writeln(boundary + "--");
            } else {
                if (SMIMEUtil.isCanonicalisationRequired(bodyPart, defaultContentTransferEncoding)) {
                    out = new CRLFOutputStream(out);
                }

                bodyPart.writeTo(out);
            }
        }

        public void write(OutputStream out)
                throws IOException {
            try {
                CMSSignedDataStreamGenerator gen = getGenerator();

                OutputStream signingStream = gen.open(out, encapsulate);

                if (content != null) {
                    if (!encapsulate) {
                        writeBodyPart(signingStream, content);
                    } else {
                        CommandMap commandMap = CommandMap.getDefaultCommandMap();

                        if (commandMap instanceof MailcapCommandMap) {
                            content.getDataHandler().setCommandMap(addCommands((MailcapCommandMap) commandMap));
                        }

                        content.writeTo(signingStream);
                    }
                }
                signingStream.close();
            } catch (MessagingException e) {
                throw new IOException(e.toString());
            } catch (CMSException e) {
                throw new IOException(e.toString());
            }
        }
    }
}