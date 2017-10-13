package org.spongycastle.mail.smime;

import org.spongycastle.mail.smime.util.CRLFOutputStream;
import org.spongycastle.util.Strings;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

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

    /**
     * internal postamble is generally included in signatures, while this is technically wrong,
     * if we find internal postamble we include it by default.
     */
    static void outputPostamble(LineOutputStream lOut, MimeBodyPart part, int count, String boundary)
            throws MessagingException, IOException {
        InputStream in;

        try {
            in = part.getRawInputStream();
        } catch (MessagingException e) {
            return;   // no underlying content rely on default generation
        }

        String line;
        int boundaries = count + 1;

        while ((line = readLine(in)) != null) {
            if (line.startsWith(boundary)) {
                boundaries--;

                if (boundaries == 0) {
                    break;
                }
            }
        }

        while ((line = readLine(in)) != null) {
            lOut.writeln(line);
        }

        in.close();

        if (boundaries != 0) {
            throw new MessagingException("all boundaries not found for: " + boundary);
        }
    }

    static void outputPostamble(LineOutputStream lOut, BodyPart parent, String parentBoundary, BodyPart part)
            throws MessagingException, IOException {
        InputStream in;

        try {
            in = ((MimeBodyPart) parent).getRawInputStream();
        } catch (MessagingException e) {
            return;   // no underlying content rely on default generation
        }


        MimeMultipart multipart = (MimeMultipart) part.getContent();
        ContentType contentType = new ContentType(multipart.getContentType());
        String boundary = "--" + contentType.getParameter("boundary");
        int count = multipart.getCount() + 1;
        String line;
        while (count != 0 && (line = readLine(in)) != null) {
            if (line.startsWith(boundary)) {
                count--;
            }
        }

        while ((line = readLine(in)) != null) {
            if (line.startsWith(parentBoundary)) {
                break;
            }
            lOut.writeln(line);
        }

        in.close();
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

    static void outputBodyPart(
            OutputStream out,
            boolean topLevel,
            BodyPart bodyPart,
            String defaultContentTransferEncoding)
            throws MessagingException, IOException {
        if (bodyPart instanceof MimeBodyPart) {
            MimeBodyPart mimePart = (MimeBodyPart) bodyPart;
            String[] cte = mimePart.getHeader("Content-Transfer-Encoding");
            String contentTransferEncoding;

            if (isMultipartContent(mimePart)) {
                MimeMultipart mp = (MimeMultipart) bodyPart.getContent();
                ContentType contentType = new ContentType(mp.getContentType());
                String boundary = "--" + contentType.getParameter("boundary");

                SMIMEUtil.LineOutputStream lOut = new SMIMEUtil.LineOutputStream(out);

                Enumeration headers = mimePart.getAllHeaderLines();
                while (headers.hasMoreElements()) {
                    String header = (String) headers.nextElement();
                    lOut.writeln(header);
                }

                lOut.writeln();      // CRLF separator

                outputPreamble(lOut, mimePart, boundary);

                for (int i = 0; i < mp.getCount(); i++) {
                    lOut.writeln(boundary);
                    BodyPart part = mp.getBodyPart(i);
                    outputBodyPart(out, false, part, defaultContentTransferEncoding);
                    if (!isMultipartContent(part)) {
                        lOut.writeln();       // CRLF terminator needed
                    } else {                        // output nested preamble
                        outputPostamble(lOut, mimePart, boundary, part);
                    }
                }

                lOut.writeln(boundary + "--");

                if (topLevel) {
                    outputPostamble(lOut, mimePart, mp.getCount(), boundary);
                }

                return;
            }

            if (cte == null) {
                contentTransferEncoding = defaultContentTransferEncoding;
            } else {
                contentTransferEncoding = cte[0];
            }

            if (!contentTransferEncoding.equalsIgnoreCase("base64")
                    && !contentTransferEncoding.equalsIgnoreCase("quoted-printable")) {
                if (!contentTransferEncoding.equalsIgnoreCase("binary")) {
                    out = new CRLFOutputStream(out);
                }
                bodyPart.writeTo(out);
                out.flush();
                return;
            }

            boolean base64 = contentTransferEncoding.equalsIgnoreCase("base64");

            //
            // Write raw content, performing canonicalization
            //
            InputStream inRaw;

            try {
                inRaw = mimePart.getRawInputStream();
            } catch (MessagingException e) {
                // this is less than ideal, but if the raw output stream is unavailable it's the
                // best option we've got.
                out = new CRLFOutputStream(out);
                bodyPart.writeTo(out);
                out.flush();
                return;
            }

            //
            // Write headers
            //
            LineOutputStream outLine = new LineOutputStream(out);
            for (Enumeration e = mimePart.getAllHeaderLines(); e.hasMoreElements(); ) {
                String header = (String) e.nextElement();

                outLine.writeln(header);
            }

            outLine.writeln();
            outLine.flush();


            OutputStream outCRLF;

            if (base64) {
                outCRLF = new Base64CRLFOutputStream(out);
            } else {
                outCRLF = new CRLFOutputStream(out);
            }

            byte[] buf = new byte[BUF_SIZE];

            int len;
            while ((len = inRaw.read(buf, 0, buf.length)) > 0) {

                outCRLF.write(buf, 0, len);
            }

            inRaw.close();

            outCRLF.flush();
        } else {
            if (!defaultContentTransferEncoding.equalsIgnoreCase("binary")) {
                out = new CRLFOutputStream(out);
            }

            bodyPart.writeTo(out);

            out.flush();
        }
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

    static class Base64CRLFOutputStream extends FilterOutputStream {
        protected static byte newline[];

        static {
            newline = new byte[2];
            newline[0] = '\r';
            newline[1] = '\n';
        }

        protected int lastb;
        private boolean isCrlfStream;

        public Base64CRLFOutputStream(OutputStream outputstream) {
            super(outputstream);
            lastb = -1;
        }

        public void write(int i)
                throws IOException {
            if (i == '\r') {
                out.write(newline);
            } else if (i == '\n') {
                if (lastb != '\r') {                                 // imagine my joy...
                    if (!(isCrlfStream && lastb == '\n')) {
                        out.write(newline);
                    }
                } else {
                    isCrlfStream = true;
                }
            } else {
                out.write(i);
            }

            lastb = i;
        }

        public void write(byte[] buf)
                throws IOException {
            this.write(buf, 0, buf.length);
        }

        public void write(byte buf[], int off, int len)
                throws IOException {
            for (int i = off; i != off + len; i++) {
                this.write(buf[i]);
            }
        }

        public void writeln()
                throws IOException {
            super.out.write(newline);
        }
    }
}