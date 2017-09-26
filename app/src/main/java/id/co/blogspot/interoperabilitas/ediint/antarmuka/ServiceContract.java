package id.co.blogspot.interoperabilitas.ediint.antarmuka;

import java.util.List;

import id.co.blogspot.interoperabilitas.ediint.domain.LineItem;
import id.co.blogspot.interoperabilitas.ediint.domain.AS2MDN;
import io.reactivex.Completable;
import io.reactivex.Single;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

/**
 * Created by dawud_tan on 9/13/17.
 */
public interface ServiceContract {
    @Headers({
            "Mime-Version: 1.0",
            "AS2-Version: 1.1"
    })
    @POST("./")
    Completable sendAsynchronously(
            @Header("Message-Id") String messageId,
            @Header("Subject") String subject,
            @Header("Recipient-Address") String recipientAddress,
            @Header("AS2-From") String as2from,
            @Header("AS2-To") String as2to,
            @Header("from") String from,
            @Header("Disposition-Notification-To") String dispositionNotificationTo,
            @Header("Disposition-Notification-Options") String dispositionNotificationOptions,
            @Header("Receipt-Delivery-Option:") String receiptDeliveryOption,//request an asynchronous MDN
            @Body List<LineItem> eo);

    @Headers({
            "Mime-Version: 1.0",
            "AS2-Version: 1.1"
    })
    @POST("./")
    Single<AS2MDN> callSynchronously(
            @Header("Message-Id") String messageId,
            @Header("Subject") String subject,
            @Header("Recipient-Address") String recipientAddress,
            @Header("AS2-From") String as2from,
            @Header("AS2-To") String as2to,
            @Header("From") String from,
            @Header("Disposition-Notification-To") String dispositionNotificationTo,
            @Header("Disposition-Notification-Options") String dispositionNotificationOptions,
            @Body List<LineItem> eo);
}