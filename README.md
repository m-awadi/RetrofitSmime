# RetrofitSmime
Android app that show how to use resteasy crypto & spongycastle mail package to send an ecrypted mail over http, this project use Android Studio 2.3.3

You could send signed & encrypted purchase order using this file '[pembeli.p12](https://github.com/dawud-tan/RetrofitSmime/raw/master/pembeli.p12)', the password is 'testas2'

In this project, the purchase order will be delivered to the following endpoint http://192.168.1.3:10080/, you could use OpenAS2Server 2.3.2 that will accept the purchase order and save it to local file, [OpenAS2Server-2.3.2](https://downloads.sourceforge.net/project/openas2/OpenAS2Server-2.3.2.zip?r=https%3A%2F%2Fsourceforge.net%2Fprojects%2Fopenas2%2Ffiles%2F&ts=1506403784&use_mirror=nchc), change default cipher you will need to start the OpenAS2 server and the remote command client passing
`SSL_DH_anon_WITH_RC4_128_MD5` as a system property using the `-DCmdProcessorSocketCipher=` system property that can be added to `start-openas2.sh`

if you would like to try this app, just grab it from following Google Play button
[![Google Play](https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png)](https://play.google.com/store/apps/details?id=id.co.blogspot.datacomlink.ediint&utm_source=global_co&utm_medium=prtnr&utm_content=Mar2515&utm_campaign=PartBadge&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1)

![Layout Preview](/Screenshot.png)
