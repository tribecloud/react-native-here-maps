package com.heremapsrn.react.map;

import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.util.Log;


import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.MapEngine;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.ViewObject;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapGesture;
import com.here.android.mpa.mapping.MapMarker;
import com.here.android.mpa.mapping.MapObject;
import com.here.android.mpa.mapping.MapState;
import com.here.android.mpa.mapping.MapView;
import com.here.android.mpa.common.Image;
import com.heremapsrn.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HereMapView extends MapView {

    private static final String TAG = HereMapView.class.getSimpleName();

    private static final String MAP_TYPE_NORMAL = "normal";
    private static final String MAP_TYPE_SATELLITE = "satellite";

    private Map map;

    private GeoCoordinate mapCenter;
    private String mapType = "normal";

    private boolean mapIsReady = false;

    private double zoomLevel = 10;

    ArrayList<MapMarker> markers;
    final Handler mSettledHandler = new Handler();
    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            GeoCoordinate coordinate = getMap().getCenter();
            WritableMap map = Arguments.createMap();
            map.putDouble("latitude", coordinate.getLatitude());
            map.putDouble("longitude", coordinate.getLongitude());

            final ReactContext context = (ReactContext) getContext();
            context.getJSModule(RCTEventEmitter.class).receiveEvent(
                    getId(),
                    "onCenterChanged",
                    map
            );

            Log.i(TAG, String.format("Lat: %.12f, lng: %.12f", coordinate.getLatitude(), coordinate.getLongitude()));
        }
    };

    public HereMapView(Context context) {
        super(context);

        markers = new ArrayList<MapMarker>();

        MapEngine.getInstance().init(context, new OnEngineInitListener() {
            @Override
            public void onEngineInitializationCompleted(OnEngineInitListener.Error error) {
                if (error == OnEngineInitListener.Error.NONE) {
                    Log.i(TAG, "--- Initialization ---");

                    map = new Map();
                    setMap(map);

                    map.setMapScheme(Map.Scheme.NORMAL_DAY);

                    mapIsReady = true;

                    if (mapCenter != null) map.setCenter(mapCenter, Map.Animation.NONE);

                    // Add the marker
                    if (markers != null) {
                        for (MapMarker marker : markers) {
                            map.addMapObject(marker);
                        }
                    }

                    Log.d(TAG, String.format("mapType: %s", mapType));
                    setMapType(mapType);

                    setZoomLevel(zoomLevel);

                    getMap().addTransformListener(new Map.OnTransformListener() {
                        @Override
                        public void onMapTransformStart() {
                            Log.i(TAG, "Transform start");
                            mSettledHandler.removeCallbacks(mRunnable);
                            mSettledHandler.postDelayed(mRunnable, 1000);
                        }

                        @Override
                        public void onMapTransformEnd(MapState mapState) {
                            Log.i(TAG, "Transform end");
                            mSettledHandler.removeCallbacks(mRunnable);
                            mSettledHandler.postDelayed(mRunnable, 1000);
                        }
                    });

                    // Create a gesture listener on marker object
                    getMapGesture().addOnGestureListener(
                            new MapGesture.OnGestureListener.OnGestureListenerAdapter() {
                                @Override
                                public boolean onMapObjectsSelected(List<ViewObject> objects) {
                                    for (ViewObject viewObj : objects) {
                                        if (viewObj.getBaseType() == ViewObject.Type.USER_OBJECT) {
                                            if (((MapObject) viewObj).getType() == MapObject.Type.MARKER) {
                                                // At this point we have the originally added
                                                // map marker, so we can do something with it
                                                // (like change the visibility, or more
                                                // marker-specific actions)
                                                if(((MapMarker) viewObj).isInfoBubbleVisible()){
                                                    ((MapMarker) viewObj).hideInfoBubble();
                                                } else {
                                                    ((MapMarker) viewObj).showInfoBubble();
                                                }

                                            }
                                        }
                                    }
                                    // return false to allow the map to handle this callback also
                                    return false;
                                }
                            });





                    Log.i(TAG, "INIT FINISH !!!!");

                } else {
                    Log.e(TAG, String.format("Error initializing map: %s", error.getDetails()));
                }


            }
        });


    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause...");
        MapEngine.getInstance().onPause();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume...");
        MapEngine.getInstance().onResume();
    }

    public void setCenter(ReadableMap center) {

        double latitude = center.getDouble("latitude");
        double longitude = center.getDouble("longitude");

        mapCenter = new GeoCoordinate(latitude, longitude);
        if (mapIsReady) map.setCenter(mapCenter, Map.Animation.NONE);

    }

    public void setMapType(String mapType) {
        this.mapType = mapType;
        if (!mapIsReady) return;

        if (mapType.equals(MAP_TYPE_NORMAL)) {
            map.setMapScheme(Map.Scheme.NORMAL_DAY);
        } else if (MAP_TYPE_SATELLITE.equals(mapType)) {
            map.setMapScheme(Map.Scheme.SATELLITE_DAY);
        }
    }

    public void setZoomLevel(double zoomLevel) {
        this.zoomLevel = zoomLevel;
        if (!mapIsReady) return;

        map.setZoomLevel(zoomLevel);
    }

    public void setUserLocation(ReadableMap markerPosition) {


        double latitude = markerPosition.getDouble("latitude");
        double longitude = markerPosition.getDouble("longitude");

        // Create a custom marker image
        Image myImage = new Image();

        try {
            myImage.setImageResource(R.drawable.location);
        } catch (IOException e) {
            Log.e(TAG, String.format("Error initializing image marker: %s", e.getMessage()));
        }
        // Create the MapMarker
        MapMarker marker =
                new MapMarker(new GeoCoordinate(latitude, longitude), myImage);
        marker.setAnchorPoint(new PointF(myImage.getWidth() / 2f, myImage.getHeight()));

        markers.add(marker);

        if (mapIsReady) map.addMapObject(marker);

    }

    public void setMarkersList(ReadableArray markersPosition) {

        for(int i=0; i< markersPosition.size(); i++) {

            ReadableMap readableMap = markersPosition.getMap(i);

            // String[] values = readableMap.getString("location").split(",");

                double latitude = readableMap.getDouble("latitude");
                double longitude = readableMap.getDouble("longitude");

                String title = readableMap.getString("title");
                String description = readableMap.getString("description");

                // Create a custom marker image
                Image myImage = new Image();

                try {
                    myImage.setImageResource(R.drawable.marker);
                } catch (IOException e) {
                    Log.e(TAG, String.format("Error initializing image marker: %s", e.getMessage()));
                }

                //Create the MamMarker
                MapMarker marker = new MapMarker(new GeoCoordinate(latitude, longitude), myImage);
                marker.setAnchorPoint(new PointF(myImage.getWidth() / 2f, myImage.getHeight()));

                marker.setTitle(title);
                marker.setDescription(description);

                // Add the MapMarker in the list
                markers.add(marker);

                if (mapIsReady) map.addMapObject(marker);

        }

    }

}
