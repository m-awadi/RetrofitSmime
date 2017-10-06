<?php //asumsinya, application/pkcs7-mime membungkus multipart/signed yang membungkus application/edifact
$encrypted  = "Content-Type: ".$_SERVER['CONTENT_TYPE']."\r\n";
$encrypted .= "Content-Disposition: ".$_SERVER['HTTP_CONTENT_DISPOSITION']."\r\n";
$encrypted .= "Content-Transfer-Encoding: ".$_SERVER['HTTP_CONTENT_TRANSFER_ENCODING']."\r\n";
$encrypted .= "\r\n";
$encrypted .= file_get_contents("php://input");
$encrypted_temp = tempnam('', 'enc');
file_put_contents($encrypted_temp, $encrypted);
$decrypted = tempnam('', 'dec');
$server_public_key = file_get_contents(__DIR__.'/kepabeanan-pub.pem');
$server_private_key = file_get_contents(__DIR__.'/kepabeanan-priv.pem');
openssl_pkcs7_decrypt($encrypted_temp, $decrypted, $server_public_key, $server_private_key);
@unlink($encrypted_temp);
$content_temp = tempnam('', 'content_temp');
$outfilename = tempnam('', 'outfilename');
openssl_pkcs7_verify($decrypted, PKCS7_NOVERIFY | PKCS7_NOINTERN | PKCS7_NOCHAIN, $outfilename, array(), __DIR__.'/importir-pub.pem', $content_temp);
$customs_dec_msg = file_get_contents($content_temp);
file_put_contents('./pemberitahuan_impor_barang.txt', $customs_dec_msg);
@unlink($content_temp);
@unlink($outfilename);
@unlink($decrypted);
$digest_algorithm = explode(", ", explode("; ", $_SERVER['HTTP_DISPOSITION_NOTIFICATION_OPTIONS'])[1])[1];
$message_digest = base64_encode(openssl_digest($customs_dec_msg, $digest_algorithm, true));
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
$receipt_temp = tempnam('', 'enc');
file_put_contents($receipt_temp, $receipt);
$signed_receipt = tempnam('', 'dec');
//PKCS7_NOATTR bouncy ga perlu ini
openssl_pkcs7_sign($receipt_temp, $signed_receipt, $server_public_key, $server_private_key, array(), PKCS7_NOATTR|PKCS7_DETACHED);
@unlink($receipt_temp);
$asik = file_get_contents($signed_receipt);
@unlink($signed_receipt);
$header_and_body = explode("\n\nThis is an S/MIME signed message\n", $asik);
$response_body = substr($header_and_body[1], 0, -1);
header('AS2-From: '.$_SERVER['HTTP_AS2_TO']);
header('AS2-To: '.$_SERVER['HTTP_AS2_FROM']);
header('AS2-Version: 1.1');
header('MIME-Version: 1.0');
date_default_timezone_set('Asia/Jakarta');
header("Message-Id: github-dawud-tan-RetrofitSmime-".date('dmoHisO')."-".rand()."@".$_SERVER['HTTP_AS2_TO']."_".$_SERVER['HTTP_AS2_FROM']);
header(explode("\n", $header_and_body[0])[1]);
header("Content-Length: ".strlen($response_body));
echo $response_body;
