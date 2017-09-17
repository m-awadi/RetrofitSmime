package org.spongycastle.mail.smime;

import org.spongycastle.util.Strings;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.MimeBodyPart;

public class SMIMEUtil {
    private static final String MULTIPART = "multipart";
    private static final int BUF_SIZE = 32760;

    public static boolean isMultipartContent(Part part)
            throws MessagingException {
        String partType = Strings.toLowerCase(part.getContentType());

        return partType.startsWith(MULTIPART);
    }

    static boolean isCanonicalisationRequired(
            MimeBodyPart bodyPart,
            String defaultContentTransferEncoding)
            throws MessagingException {
        String[] cte = bodyPart.getHeader("Content-Transfer-Encoding");
        String contentTransferEncoding;

        if (cte == null) {
            contentTransferEncoding = defaultContentTransferEncoding;
        } else {
            contentTransferEncoding = cte[0];
        }

        return !contentTransferEncoding.equalsIgnoreCase("binary");
    }

    /**
     * internal preamble is generally included in signatures, while this is technically wrong,
     * if we find internal preamble we include it by default.
     */
    static void outputPreamble(LineOutputStream lOut, MimeBodyPart part, String boundary)
            throws MessagingException, IOException {
        InputStream in;

        try {
            in = part.getRawInputStream();
        } catch (MessagingException e) {
            return;   // no underlying content rely on default generation
        }

        String line;

        while ((line = readLine(in)) != null) {
            if (line.equals(boundary)) {
                break;
            }

            lOut.writeln(line);
        }

        in.close();

        if (line == null) {
            throw new MessagingException("no boundary found");
        }
    }

    /*
     * read a line of input stripping of the tailing \r\n
     */
    private static String readLine(InputStream in)
            throws IOException {
        StringBuffer b = new StringBuffer();

        int ch;
        while ((ch = in.read()) >= 0 && ch != '\n') {
            if (ch != '\r') {
                b.append((char) ch);
            }
        }

        if (ch < 0 && b.length() == 0) {
            return null;
        }

        return b.toString();
    }

    static class LineOutputStream extends FilterOutputStream {
        private static byte newline[];

        static {
            newline = new byte[2];
            newline[0] = 13;
            newline[1] = 10;
        }

        public LineOutputStream(OutputStream outputstream) {
            super(outputstream);
        }

        private static byte[] getBytes(String s) {
            char ac[] = s.toCharArray();
            int i = ac.length;
            byte abyte0[] = new byte[i];
            int j = 0;

            while (j < i) {
                abyte0[j] = (byte) ac[j++];
            }

            return abyte0;
        }

        public void writeln(String s)
                throws MessagingException {
            try {
                byte abyte0[] = getBytes(s);
                super.out.write(abyte0);
                super.out.write(newline);
            } catch (Exception exception) {
                throw new MessagingException("IOException", exception);
            }
        }

        public void writeln()
                throws MessagingException {
            try {
                super.out.write(newline);
            } catch (Exception exception) {
                throw new MessagingException("IOException", exception);
            }
        }
    }
}