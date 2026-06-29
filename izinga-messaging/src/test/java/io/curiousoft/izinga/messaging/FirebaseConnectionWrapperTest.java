/*
package io.curiousoft.izinga.messaging;

import io.curiousoft.izinga.messaging.firebase.FCMMessage;
import io.curiousoft.izinga.messaging.firebase.FCMUnSubscribeMessage;
import io.curiousoft.izinga.messaging.firebase.FirebaseConnectionWrapper;
import io.curiousoft.izinga.messaging.firebase.GoogleServices;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

@Ignore
@RunWith(MockitoJUnitRunner.class)
public class FirebaseConnectionWrapperTest {

    public static final String TEST_TOPIC = "TableView-broadcast1";
    FirebaseConnectionWrapper firebaseConnectionWrapper;
    String apikey = "AIzaSyCvhRKdFBDc0xNCugU21RW_Lc3e3eHSnPE";
    String token = "Wke1cS2Wnc:APA91bGSiP87GKYUCey_U8BQyicF2vwhnWZdCcOsB_2ammr5qmbhd0gcnDzAv_OljB8qLBRy9x1h6qa6g1f3P2DV";
    @Mock
    private GoogleServices.FirebaseInstanceIdHttpService instanceIdService;
    @Mock
    private GoogleServices.FirebaseMessageService messagingService;
    @Mock
    private myCall call;


    @Before
    public void setUp() throws Exception {
        firebaseConnectionWrapper = new FirebaseConnectionWrapper(apikey, instanceIdService, messagingService);
    }

    @Test
    public void testSendMessage() throws Exception {
        Map<Object, Object> message = new HashMap<>();
        message.put("body", "hello world");
        //when
        FCMMessage fcmMessage = new FCMMessage(token, null, message);
        when(messagingService.sendMessage("key=" + apikey, fcmMessage)).thenReturn(call);
        Request request = new Request.Builder().url("http://google.com").build();
        when(call.request()).thenReturn(request);
        Response<Map> response = Response.success(new HashMap<>());
        when(call.execute()).thenReturn(response);

        firebaseConnectionWrapper.sendMessage(fcmMessage);
        //verify
        verify(messagingService).sendMessage("key=" + apikey, fcmMessage);
        verify(call).request();
        verify(call).execute();
    }

    @Test
    public void testCreateTopic() throws Exception {

        Map<Object, Object> message = new HashMap<>();
        message.put("body", "hello world");
        //when
        FCMMessage fcmMessage = new FCMMessage(token, null, message);
        when(instanceIdService.createTopic("key=" + apikey, token, TEST_TOPIC)).thenReturn(call);
        Request request = new Request.Builder().url("http://google.com").build();
        when(call.request()).thenReturn(request);
        Response<Map> response = Response.success(new HashMap<>());
        when(call.execute()).thenReturn(response);

        firebaseConnectionWrapper.createTopic(TEST_TOPIC, token);
        //verify
        verify(instanceIdService).createTopic("key=" + apikey, token, TEST_TOPIC);
        verify(call).request();
        verify(call).execute();
    }

    @Test
    public void testSubscribeTopic() throws Exception {
        Map<Object, Object> message = new HashMap<>();
        message.put("body", "hello world");
        //when
        FCMMessage fcmMessage = new FCMMessage(token, null, message);
        when(instanceIdService.subscribeTopic("key=" + apikey, token, TEST_TOPIC)).thenReturn(call);
        Request request = new Request.Builder().url("http://google.com").build();
        when(call.request()).thenReturn(request);
        Response<Map> response = Response.success(new HashMap<>());
        when(call.execute()).thenReturn(response);

        firebaseConnectionWrapper.subscribeTopic(TEST_TOPIC, token);
        //verify
        verify(instanceIdService).subscribeTopic("key=" + apikey, token, TEST_TOPIC);
        verify(call).request();
        verify(call).execute();
    }

    @Test
    public void testSendToTopic() throws Exception {

        Map<Object, Object> message = new HashMap<>();
        message.put("body", "hello world");
        //when
        FCMMessage fcmMessage = new FCMMessage("/topics/" + TEST_TOPIC, null, message);
        when(messagingService.sendMessage("key=" + apikey, fcmMessage)).thenReturn(call);
        Request request = new Request.Builder().url("http://google.com").build();
        when(call.request()).thenReturn(request);
        Response<Map> response = Response.success(new HashMap<>());
        when(call.execute()).thenReturn(response);

        firebaseConnectionWrapper.sendMessage(fcmMessage);
        //verify
        verify(messagingService).sendMessage("key=" + apikey, fcmMessage);
        verify(call).request();
        verify(call).execute();
    }

    @Test
    public void testUnSubscribe() throws Exception {
        Map<Object, Object> message = new HashMap<>();
        message.put("body", "hello world");
        //when
        when(instanceIdService.unSubscribeTopic(eq("key=" + apikey), any(FCMUnSubscribeMessage.class))).thenReturn(
                call);
        Request request = new Request.Builder().url("http://google.com").build();
        when(call.request()).thenReturn(request);
        Response<Map> response = Response.success(new HashMap<>());
        when(call.execute()).thenReturn(response);

        firebaseConnectionWrapper.unSubscribe("/topics/" + TEST_TOPIC, token);
        //verify
        verify(instanceIdService).unSubscribeTopic(eq("key=" + apikey), any(FCMUnSubscribeMessage.class));
        verify(call).request();
        verify(call).execute();
    }

    public abstract class myCall implements Call {
        @Override
        public Call clone() {
            return null;
        }
    }
}*/
