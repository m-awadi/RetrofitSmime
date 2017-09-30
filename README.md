# RetrofitSmime
Android app that show how to use spongycastle S/MIME package to send an AS2-Message (RFC 4130), this project use Android Studio 2.3.3

You could send signed & encrypted Purchase Order using this certificate '[peritel.p12](https://github.com/dawud-tan/RetrofitSmime/raw/master/peritel.p12)', the passphrase is `test`

In this project, the purchase order will be delivered to the following endpoint `http://testas2.mendelson-e-c.com:8080/as2/HttpReceiver`. You can monitor messages being send to Mendelson using URL `http://testas2.mendelson-e-c.com:8080/webas2/`. Username and password is `guest`.

If you would like to try RetrofitSmime, just grab it from the following Google Play button
[![Google Play](https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png)](https://play.google.com/store/apps/details?id=id.co.blogspot.datacomlink.ediint&utm_source=global_co&utm_medium=prtnr&utm_content=Mar2515&utm_campaign=PartBadge&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1)

![Layout Preview](/Screenshot.png)
