package com.google.example.games.basegameutils;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.request.GameRequest;
import com.google.android.gms.games.request.Requests;
import com.unity3d.player.UnityPlayer;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by xtra on 11/09/2014.
 */
public class GiftResultActivity extends Activity {
    // Request code inbox.
    final static int SHOW_INBOX = 1;

    // Request code inbox.
    final static int SEND_GIFT_CODE = 2;

    // Request code inbox.
    final static int WISH_GIFT_CODE = 3;

    // Request code inbox.
    final static int MANUAL_SHOW_INBOX = 4;

    // Request code inbox.
    final static int ACCEPT_REQUEST = 5;



    @Override
    protected void onStart() {
        super.onStart();
        Bundle extras = getIntent().getExtras();

        int type = extras.getInt(GameHelper.PARAM_TYPE);
        if(type == WISH_GIFT_CODE || type == SEND_GIFT_CODE) {
            int day  =  extras.getInt(GameHelper.PARAM_DAY);
            String encodedImage  =  extras.getString(GameHelper.PARAM_BITMAP);
            String payload  =  extras.getString(GameHelper.PARAM_PAYLOAD);
            String description  =  extras.getString(GameHelper.PARAM_DESCRIPTION);

            byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            byte[] payloadData = payload.getBytes();

            if(type == 1)
            {
                Intent intent = Games.Requests.getSendIntent(GameHelper.mInstance.mGoogleApiClient, GameRequest.TYPE_GIFT, payloadData, day, bitmap, description);
                startActivityForResult(intent, SEND_GIFT_CODE);
            }
            else
            {
                Intent intent = Games.Requests.getSendIntent(GameHelper.mInstance.mGoogleApiClient, GameRequest.TYPE_WISH, payloadData, day, bitmap, description);
                startActivityForResult(intent, WISH_GIFT_CODE);
            }
        }
        else if(type == SHOW_INBOX)
        {
            startActivityForResult(Games.Requests.getInboxIntent(GameHelper.mInstance.getApiClient()), SHOW_INBOX);
        }
        else if(type == MANUAL_SHOW_INBOX)
        {
            updateRequestCounts();
        }
        else if(type == ACCEPT_REQUEST)
        {
            int typeRequest = extras.getInt(GameHelper.PARAM_REQUEST_TYPE);
            int positionRequest = extras.getInt(GameHelper.PARAM_REQUEST_POSITION);
            handleRequest(typeRequest,positionRequest);
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int responseCode, Intent data)
    {
        this.finish();
        if (requestCode == SHOW_INBOX)
        {
            if (responseCode == Activity.RESULT_OK && data != null) {
                handleRequests(Games.Requests.getGameRequestsFromInboxResponse(data));
            }
        }
        else if (requestCode == SEND_GIFT_CODE)
        {
            UnityPlayer.UnitySendMessage("GiftListener","GiftSent","");
        }
        else if (requestCode == WISH_GIFT_CODE)
        {
            UnityPlayer.UnitySendMessage("GiftListener","WishSent","");
        }
    }

    // Deal with any requests that are incoming, either from a bundle from the
    // app starting via notification, or from the inbox. Players should give
    // explicit approval to accept any gift or request, so we pop up a dialog.
    private void handleRequests(ArrayList<GameRequest> requests) {
        if (requests == null) {
            return;
        }
        acceptRequests(requests);
    }

    // Deal with any requests that are incoming, either from a bundle from the
    // app starting via notification, or from the inbox. Players should give
    // explicit approval to accept any gift or request, so we pop up a dialog.
    private void handleRequest(int type, int position) {
        // Must have final for anonymous function
        final ArrayList<GameRequest> theRequests = new ArrayList<GameRequest>();
        if( type == GameRequest.TYPE_WISH)
        {
            if (null != GameHelper.mInstance.bufWish)
            {
                theRequests.add(GameHelper.mInstance.bufWish.get(position));
            }
        }
        else
        {
            if (null != GameHelper.mInstance.bufGift)
            {
                theRequests.add(GameHelper.mInstance.bufGift.get(position));
            }
        }
        acceptRequests(theRequests);
    }



    // Actually accepts the requests
    private void acceptRequests(ArrayList<GameRequest> requests) {
        // Attempt to accept these requests.
        ArrayList<String> requestIds = new ArrayList<String>();

        // Make sure we have a valid API client.
        GoogleApiClient client = GameHelper.mInstance.getApiClient();

        /**
         * Map of cached game request ID to its corresponding game request
         * object.
         */
        final HashMap<String, GameRequest> gameRequestMap = new HashMap<String, GameRequest>();

        // Cache the requests.
        for (GameRequest request : requests) {
            String requestId = request.getRequestId();
            requestIds.add(requestId);
            gameRequestMap.put(requestId, request);
        }

        // Accept the requests.
        Games.Requests.acceptRequests(client, requestIds).setResultCallback(
                new ResultCallback<Requests.UpdateRequestsResult>() {
                    @Override
                    public void onResult(Requests.UpdateRequestsResult result) {
                        int numGifts = 0;
                        int numRequests = 0;
                        // Scan each result outcome.
                        for (String requestId : result.getRequestIds()) {
                            // We must have a local cached copy of the request
                            // and the request needs to be a
                            // success in order to continue.
                            if (!gameRequestMap.containsKey(requestId)
                                    || result.getRequestOutcome(requestId) != Requests.REQUEST_UPDATE_OUTCOME_SUCCESS) {
                                continue;
                            }
                            // Update succeeded here. Find the type of request
                            // and act accordingly. For wishes, a
                            // responding gift will be automatically sent.
                            byte[] lByte = gameRequestMap.get(requestId).getData();
                            String lPayload = new String(lByte);
                            UnityPlayer.UnitySendMessage("GiftListener","GiftAccepted", lPayload);
                        }
                    }
                });

    }

    // Changes the numbers at the top of the layout
    private void updateRequestCounts() {
        PendingResult<Requests.LoadRequestsResult> result = Games.Requests
                .loadRequests(GameHelper.mInstance.getApiClient(),
                        Requests.REQUEST_DIRECTION_INBOUND,
                        GameRequest.TYPE_ALL,
                        Requests.SORT_ORDER_EXPIRING_SOON_FIRST);
        result.setResultCallback(mLoadRequestsCallback);
    }

    // Called back after you load the current requests
    private final ResultCallback<Requests.LoadRequestsResult> mLoadRequestsCallback = new ResultCallback<Requests.LoadRequestsResult>() {
        @Override
        public void onResult(Requests.LoadRequestsResult result) {
            GameHelper.mInstance.bufGift = result.getRequests(GameRequest.TYPE_GIFT);
            GameHelper.mInstance.bufWish = result.getRequests(GameRequest.TYPE_WISH);
            UnityPlayer.UnitySendMessage("GiftListener","GiftListUpdated", "");
        }
    };
}
