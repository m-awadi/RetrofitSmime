package id.co.blogspot.interoperabilitas.ediint.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by dawud_tan on 9/27/17.
 */

public class AS2MDN {
    public Boolean signedByRecipient;
    public String text;
    public String action;
    public String mdnAction;
    public Status status;
    public String returnMIC;
    public String calcMIC;

    /* Returned-Content-MIC header and rfc822 headers can contain spaces all over the place.
     * (not to mention comments!). Simple fix - delete all spaces.
     * Since the partner could return the algorithm in different case to
     * what was sent, remove the algorithm before compare
     * The Algorithm is appended as a part of the MIC by adding a comma then
     * optionally a space followed by the algorithm
     */
    public void validateMIC() {
        String regex = "^\\s*(\\S+)\\s*,\\s*(\\S+)\\s*$";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(returnMIC);
        if (!m.find()) {
            throw new RuntimeException("Invalid MIC format in returned MIC: " + returnMIC);
        }
        String rMic = m.group(1);
        String rMicAlg = m.group(2);
        m = p.matcher(calcMIC);
        if (!m.find()) {
            throw new RuntimeException("Invalid MIC format in calculated MIC: " + calcMIC);
        }
        String cMic = m.group(1);
        String cMicAlg = m.group(2);
        if (!cMicAlg.equalsIgnoreCase(rMicAlg)) {
            // Appears not to match.... make sure dash is not the issue as in SHA-1 compared to SHA1
            if (!cMicAlg.replaceAll("-", "").equalsIgnoreCase(rMicAlg.replaceAll("-", ""))) {
                /*
                 * RFC 6362 specifies that the sent attachments should be
				 * considered invalid and retransmitted
				 */
                String errmsg = "MIC algorithm returned by partner is not the same as the algorithm requested, original MIC alg: "
                        + cMicAlg
                        + " ::: returned MIC alg: "
                        + rMicAlg
                        + "\n\t\tPartner probably not implemented AS2 spec correctly or does not support the requested algorithm. Check that the \"as2_mdn_options\" attribute for the partner uses the same algorithm as the \"sign\" attribute.";
                throw new RuntimeException(errmsg + " Forcing Resend");
            }
        }
        if (!cMic.equals(rMic)) {
            /* RFC 6362 specifies that the sent attachments should be considered invalid and retransmitted
             */
            throw new RuntimeException("MIC not matched, original MIC: " + calcMIC + " return MIC: " + returnMIC);
        }
    }
}