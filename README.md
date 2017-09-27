# RetrofitSmime
Android app that show how to use spongycastle mail package to send an AS2-Message (RFC 4130), this project use Android Studio 2.3.3

You could send signed & encrypted purchase order using this file '[pembeli.p12](https://github.com/dawud-tan/RetrofitSmime/raw/master/pembeli.p12)', the password is 'test'

In this project, the purchase order will be delivered to the following endpoint http://10.0.2.2:10080/, you could use AS2-Server 3.1.0 that will accept the purchase order and save it to local file, [AS-Server-3.1.0](https://github.com/phax/as2-server/archive/as2-server-3.1.0.zip).

# Building and running [AS-Server-3.1.0](https://github.com/phax/as2-server/archive/as2-server-3.1.0.zip) from source
To run the stand-alone [AS-Server-3.1.0](https://github.com/phax/as2-server/archive/as2-server-3.1.0.zip) from the source build, perform the following steps.
In the below commands `x.y.z` denotes the effective version number

1. build the binary artefacts using [Apache Maven 3.x](https://maven.apache.org/install.html): `mvn clean install -Pwithdep` (it selects the profile "withdep" which means "with dependencies"). On Windows you may run `build.cmd` as an alternative.
  1. If this fails than potentially because a SNAPSHOT version of `as2-lib` is referenced by the author unintentionally, and because he is human :). In that case check out the [as2-lib](https://github.com/phax/as2-lib/) project as well, run `mvn clean install` on as2-lib and go back to the first step on AS2-server project. 
2. The resulting JAR file is then located at `standalone/as2-server.jar`
3. Launch the server (note: `src/main/resources/config/config.xml` is the path to the configuration file to be used and may be changed): 
  1. On Unix/Linux systems run the AS2 server using the following command (on one line):

     `java -cp "standalone/*" com.helger.as2.app.MainOpenAS2Server standalone/config/config.xml`

if you would like to try this app, just grab it from the following Google Play button
[![Google Play](https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png)](https://play.google.com/store/apps/details?id=id.co.blogspot.datacomlink.ediint&utm_source=global_co&utm_medium=prtnr&utm_content=Mar2515&utm_campaign=PartBadge&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1)

![Layout Preview](/Screenshot.png)
