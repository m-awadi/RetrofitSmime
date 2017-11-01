<?php
/* Assumption: Sender sends encrypted and signed
 * data and requests a signed receipt.
 * Receiver sends back the synchronous signed
 * receipt.
 *
 * Encryption, signature
 *   -RFC2616/2045
 *     -RFC3851 (application/pkcs7-mime)
 *       -RFC1847 (multipart/signed)(encrypted)
 *         -RFC1767 (application/EDIFACT)(encrypted)
 *         -RFC3851 (application/pkcs7-signature)(encrypted)
 **/

//encrypted content
$encrypted  = "Content-Type: ".$_SERVER['CONTENT_TYPE']."\r\n";
$encrypted .= "Content-Disposition: ".$_SERVER['HTTP_CONTENT_DISPOSITION']."\r\n";
$encrypted .= "Content-Transfer-Encoding: ".$_SERVER['HTTP_CONTENT_TRANSFER_ENCODING']."\r\n";
$encrypted .= "\r\n";
$encrypted .= file_get_contents("php://input");
//encrypted content

//put encrypted content to temporary file
$encrypted_temp = tempnam('', 'enc');
file_put_contents($encrypted_temp, $encrypted);

//get temporary file name for storing decrypted content
$decrypted = tempnam('', 'dec');

//load server's public key
$server_public_key = file_get_contents(__DIR__.'/kepabeanan.crt');

//load server's public key
$server_private_key = file_get_contents(__DIR__.'/kepabeanan.key');

//decrypt the encrypted content, and store it to @$encrypted_conten
//beware, openssl doesn't support RC2/68 encryption algorithm
openssl_pkcs7_decrypt($encrypted_temp, $decrypted, $server_public_key, $server_private_key);
@unlink($encrypted_temp);

$content_temp = tempnam('', 'content_temp');
$outfilename = tempnam('', 'outfilename');

//verify the signed content using the sender public key
//public key may be obtained from any store identified by AS2-From header value
openssl_pkcs7_verify($decrypted, PKCS7_NOVERIFY | PKCS7_NOINTERN | PKCS7_NOCHAIN, $outfilename, array(), __DIR__.'/importir.crt', $content_temp);
$customs_dec_msg = file_get_contents($content_temp);
file_put_contents('./pemberitahuan_impor_barang.txt', $customs_dec_msg);
@unlink($content_temp);
@unlink($outfilename);
@unlink($decrypted);

/* the absence of $_SERVER['HTTP_RECEIPT_DELIVERY_OPTION'],
 * means MDN is sent synchronously,
 * by placing it on HTTP Response
 **/

/* Receipt is just like 'R' indicator in BBM,
 * or blue-colored double check in WhatsApp,
 * indicating that it's been read.
 * But, BBM/WA's receipt might be use proprietary format,
 * not necessarily conforms to RFC 3462.
 *
 * MDN over HTTP, signature
 *       -RFC2616/2045
 *         -RFC1847 (multipart/signed)
 *          -RFC3798 (message/disposition-notification)
 *          -RFC3851 (application/pkcs7-signature)
 **/

//extract the digest algorithm from Disposition-Notitification-Options header
$digest_algorithm = explode(", ", explode("; ", $_SERVER['HTTP_DISPOSITION_NOTIFICATION_OPTIONS'])[1])[1];
//calculate the message digest over request body, then base64 encode the binary reps
$message_digest = base64_encode(openssl_digest($customs_dec_msg, $digest_algorithm, true));
//define the multipart/report message boundary
$boundary = uniqid('----=_Part_');

$receipt = <<<EOT
Content-Type: multipart/report; report-type=disposition-notification; 
	boundary="$boundary"

--$boundary
Content-Type: text/plain
Content-Transfer-Encoding: 7bit

The AS2 message has been received.
--$boundary
Content-Type: message/disposition-notification
Content-Transfer-Encoding: 7bit

Reporting-UA: php AS2 Server
Original-Recipient: rfc822; {$_SERVER['HTTP_AS2_TO']}
Final-Recipient: rfc822; {$_SERVER['HTTP_AS2_TO']}
Original-Message-ID: {$_SERVER['HTTP_MESSAGE_ID']}
Disposition: automatic-action/MDN-sent-automatically; processed
Received-Content-MIC: $message_digest, $digest_algorithm

--$boundary--

EOT;

//put the above MDN (receipt) to temp_file
$receipt_temp = tempnam('', 'enc');
file_put_contents($receipt_temp, $receipt);
$signed_receipt_handler = tempnam('', 'dec');

//Bouncycastle also doing PKCS7_NOATTR
openssl_pkcs7_sign($receipt_temp, $signed_receipt_handler, $server_public_key, $server_private_key, array(), PKCS7_NOATTR | PKCS7_DETACHED);
@unlink($receipt_temp);
$signed_receipt = file_get_contents($signed_receipt_handler);
@unlink($signed_receipt_handler);
$header_and_body = explode("\n\nThis is an S/MIME signed message\n", $signed_receipt);

header('AS2-From: '.$_SERVER['HTTP_AS2_TO']);
header('AS2-To: '.$_SERVER['HTTP_AS2_FROM']);
header('AS2-Version: 1.1');
header('MIME-Version: 1.0');
date_default_timezone_set('Asia/Jakarta');
header("Message-Id: github-dawud-tan-RetrofitSmime-".date('dmoHisO')."-".rand()."@".$_SERVER['HTTP_AS2_TO']."_".$_SERVER['HTTP_AS2_FROM']);
//extract the Content-Type from openssl_pkcs7_sign
header(explode("\n", $header_and_body[0])[1]);
//extract the response body from openssl_pkcs7_sign
$response_body = substr($header_and_body[1], 0, -1);
header("Content-Length: ".strlen($response_body));
echo $response_body;
