package id.co.blogspot.interoperabilitas.ediint.antarmuka;

import java.util.List;

import id.co.blogspot.interoperabilitas.ediint.domain.LineItem;
import io.reactivex.Completable;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * Created by dawud_tan on 9/13/17.
 */
public interface ServiceContract {
    @POST("0e753acc-296d-425a-80f9-c67c1e0095c8")
    Completable sendAsynchronously(
            @Header("AS2-From") String username,
            @Header("Disposition-Notification-To") String dispositionNotificationTo,
            @Header("Disposition-Notification-Options") String dispositionNotificationOptions,
            @Header("Receipt-Delivery-Option:") String receiptDeliveryOption,
            @Body List<LineItem> eo);
}