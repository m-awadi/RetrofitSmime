# RetrofitSmime
Android app that show how to use Spongycastle S/MIME package to send an AS2-Message (RFC 4130), this project use Android Studio 3.0.1

You could send signed & encrypted Customs declaration message using this certificate '[importir.p12](https://github.com/dawud-tan/RetrofitSmime/raw/master/importir.p12)', the passphrase is `test`

In this project, the Custom Declaration message will be delivered to the following endpoint `http://testas2.mendelson-e-c.com:8080/as2/HttpReceiver`. You can monitor messages being send to Mendelson using URL `http://testas2.mendelson-e-c.com:8080/webas2/`. Username and password is `guest`. You could also use following simple as2 server coded in php, '[SecureTransmissionLoop.php](https://github.com/dawud-tan/RetrofitSmime/raw/master/SecureTransmissionLoop.php)', then change the address bar pointing to `http://yourdomain:port/path/to/SecureTransmissionLoop.php`. The use of Elliptic Curve Cryptography-based x509 Certificate is also possible by employing KeyAgreeRecipientInfo, just swap with this certificate '[pedagang.p12](https://github.com/dawud-tan/RetrofitSmime/raw/master/pedagang.p12)', the passphrase is also `test`, then change the endpoint to `http://192.168.1.xxx:5080/spring-boot-smime`

If you would like to try RetrofitSmime, just grab it from the following Google Play button
[![Google Play](https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png)](https://play.google.com/store/apps/details?id=id.co.blogspot.datacomlink.ediint&utm_source=global_co&utm_medium=prtnr&utm_content=Mar2515&utm_campaign=PartBadge&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1)

![Layout Preview](/Screenshot.png)
