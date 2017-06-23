package net.neuralmetrics.projecthermes.navigationservice;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.mapbox.mapboxsdk.location.LocationSource;
import com.mapbox.services.android.navigation.v5.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.RouteProgress;
import com.mapbox.services.android.navigation.v5.listeners.AlertLevelChangeListener;
import com.mapbox.services.android.navigation.v5.listeners.NavigationEventListener;
import com.mapbox.services.android.navigation.v5.listeners.OffRouteListener;
import com.mapbox.services.android.navigation.v5.listeners.ProgressChangeListener;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngineListener;
import com.mapbox.services.android.telemetry.location.LocationEnginePriority;
import com.mapbox.services.api.directions.v5.models.DirectionsResponse;
import com.mapbox.services.api.directions.v5.models.DirectionsRoute;
import com.mapbox.services.api.directions.v5.models.StepManeuver;
import com.mapbox.services.commons.models.Position;

import net.neuralmetrics.projecthermes.HomeActivity;
import net.neuralmetrics.projecthermes.MockLocationEngine;
import net.neuralmetrics.projecthermes.R;
import net.neuralmetrics.projecthermes.utils.ResourceUtils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

// NOTE: NavigationService only exposes the EventReception to DriveFragment!
// ONLY NEED TO UNBIND AT ONDESTROY. Events listeners, however, must be unplugged onPause and replugged onStart!

public class HermesNavigationService extends Service implements NavigationEventListener, ProgressChangeListener, AlertLevelChangeListener, OffRouteListener {
    private static final String LOG_TAG = "HermesNavigationService";
    NotificationCompat.Builder notificationBuilder;

    // Navigation system
    private LocationEngine locationEngine;
    private MapboxNavigation navigation;
    private DirectionsRoute route;
    private Position destination;
    private NotificationManager notificationManager;
    private OnNavigationServiceReady eventReception;
    private final IBinder binder = new NavigationServiceBinder();

    public HermesNavigationService() {
        // Do nothing
    }

    public void setEventReception(OnNavigationServiceReady mListener)
    {
        eventReception=mListener;
    }

    public void removeEventReception()
    {
        eventReception=null;
    }

    // Service binding interface

    public class NavigationServiceBinder extends Binder {
        public HermesNavigationService getService()
        {
            return HermesNavigationService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(ServiceConstants.START_FOREGROUND)) {
            // Init notification manager
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            // Get the destination
            double lat = intent.getDoubleExtra(ServiceConstants.LATITUDE, 0);
            double lon = intent.getDoubleExtra(ServiceConstants.LONGITUDE, 0);
            destination = Position.fromCoordinates(lon, lat);

            // Configure the MapboxNavigation object
            navigation = new MapboxNavigation(this, getResources().getString(R.string.mapbox_access_token));
            // Attach listeners to MapboxNavigation object
            navigation.addNavigationEventListener(this);
            navigation.addProgressChangeListener(this);
            navigation.addAlertLevelChangeListener(this);
            navigation.addOffRouteListener(this);

            // Location engine setup
            locationEngine = LocationSource.getLocationEngine(this);
            locationEngine.activate();
            /*locationEngine = new MockLocationEngine(); // Mock location engine, please change to real location engine in production
            locationEngine.setInterval(0);
            locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
            locationEngine.setFastestInterval(1000);
            locationEngine.activate();*/

            // Given location engine


            // Prepare the notification and start foreground service
            Intent stopNavIntent = new Intent(this, HermesNavigationService.class);
            stopNavIntent.setAction(ServiceConstants.STOP_FOREGROUND);
            PendingIntent pStopNavIntent = PendingIntent.getService(this, 0, stopNavIntent, PendingIntent.FLAG_CANCEL_CURRENT);

            // Show screen when user taps on Notification
            Intent startNavFragmentIntent = new Intent(this, HomeActivity.class);
            startNavFragmentIntent.setAction(ServiceConstants.START_NAV_FRAGMENT);
            startNavFragmentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            PendingIntent pStartNavFragmentIntent = PendingIntent.getActivity(this, 0, startNavFragmentIntent, PendingIntent.FLAG_CANCEL_CURRENT);

            Log.i(LOG_TAG, "START_FOREGROUND intent is received by the service");
            Bitmap icon = ResourceUtils.drawableToBitmap(getResources().getDrawable(R.mipmap.ic_launcher));
            notificationBuilder = new NotificationCompat.Builder(this)
                    .setContentTitle("Hermes Navigation")
                    .setTicker("Hermes navigation mode is active")
                    .setContentText("Waiting for GPS and routing service...")
                    .setSmallIcon(R.drawable.ic_bubble_chart_black_24dp)
                    .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                    .setContentIntent(pStartNavFragmentIntent)
                    .setOngoing(true)
                    .addAction(R.drawable.ic_close_black_24dp, "EXIT NAVIGATION", pStopNavIntent);

            Notification notification = notificationBuilder.build();
            startForeground(ServiceConstants.NOTIFICATION_ID, notification);

            // Get the route and start navigation
            initGPSPosition();

        } else if (intent.getAction().equals(ServiceConstants.STOP_FOREGROUND)) {
            Log.i(LOG_TAG, "STOP_FOREGROUND intent is received by the service. Preparing to stop the service");

            // Request DriveFragment to release the binding and unplug all the listeners
            if (eventReception!=null) eventReception.serviceTerminate();
            eventReception=null;

            // Terminate all navigation threads
            destroyNavigationThreads();

            // We are clear to terminate this service
            stopForeground(true); // unload all the notifications
            Log.i(LOG_TAG, "Service is about to be killed");
            stopSelf();
        }

        return START_STICKY;
    }

    // Get the GPS location first
    private void initGPSPosition() {
        Log.i(LOG_TAG, "Getting GPS position");
        // Get the current user location first
        /*Location userLocation;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Location lastLocation = locationEngine.getLastLocation();
        if (lastLocation != null) {
            userLocation=lastLocation;
        }
        calculateRoute(lastLocation);*/

        LocationEngineListener locationEngineListener = new LocationEngineListener() {
            @Override
            public void onConnected() {
                // No action needed here.
            }

            @Override
            public void onLocationChanged(Location location) {
                if (location != null) {
                    locationEngine.removeLocationEngineListener(this);
                    Log.i(LOG_TAG, "GPS position acquired");
                    calculateRoute(location);
                }
            }
        };
        locationEngine.addLocationEngineListener(locationEngineListener);
    }

    private void calculateRoute(Location userLocation)
    {
        Position origin = (Position.fromCoordinates(userLocation.getLongitude(), userLocation.getLatitude()));
        navigation.getRoute(origin, destination, new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                DirectionsRoute route = response.body().getRoutes().get(0);
                HermesNavigationService.this.route = route;
                Log.i(LOG_TAG, "Route acquired, attempting to start navigation");

                // Route is available, start fake navigation
                // ((MockLocationEngine) locationEngine).setRoute(route); // Comment this in production
                navigation.setLocationEngine(locationEngine);
                navigation.startNavigation(route);

                // A good time to tell everyone outside that the service is up and running
                if (eventReception!=null) eventReception.serviceReady(HermesNavigationService.this.route, HermesNavigationService.this.locationEngine, HermesNavigationService.this.destination);

            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                Timber.e("onFailure: navigation.getRoute()", throwable);
                Log.e(LOG_TAG, "Navigation Service encountered an error while calculateRoute()");
            }
        });
    }

    // Service lifecycle

    private void destroyNavigationThreads()
    {
        if(navigation!=null) {
            Log.i(LOG_TAG, "Unloading navigation service event listeners");
            navigation.removeAlertLevelChangeListener(this);
            navigation.removeNavigationEventListener(this);
            navigation.removeProgressChangeListener(this);
            navigation.removeOffRouteListener(this);

            // End the navigation session
            navigation.endNavigation();

            Log.i(LOG_TAG, "Mapbox Navigation Service terminated!");
        }
    }

    public void onReattachListener()
    {
        if (eventReception!=null) eventReception.serviceReady(HermesNavigationService.this.route, HermesNavigationService.this.locationEngine, HermesNavigationService.this.destination);

    }

    @Override
    public void onDestroy() {
        destroyNavigationThreads();
        super.onDestroy();
    }

    // Mapbox navigation event listeners

    @Override
    public void onAlertLevelChange(int alertLevel, RouteProgress routeProgress) {

    }

    @Override
    public void onRunning(boolean running) {

    }

    @Override
    public void onProgressChange(Location location, RouteProgress routeProgress) {
        double timeToStep = routeProgress.getCurrentLegProgress().getCurrentStepProgress().getDurationRemaining();
        if (routeProgress.getCurrentLegProgress().getUpComingStep()!=null)
        {
            StepManeuver m = routeProgress.getCurrentLegProgress().getUpComingStep().getManeuver();
            String cueResourceString = "direction_"+m.getType()+"_"+m.getModifier();
            cueResourceString = cueResourceString.replace(" ","_");
            int cueResourceId = ResourceUtils.getResourceId(this,cueResourceString,"drawable",this.getPackageName());
            Bitmap icon = ResourceUtils.drawableToBitmap(getResources().getDrawable(cueResourceId));

            Notification notification = notificationBuilder.setContentTitle(routeProgress.getCurrentLegProgress().getCurrentStep().getName())
                    .setContentText((timeToStep<500?String.format("%.0f",timeToStep):"500+") + " | " + m.getInstruction().toUpperCase())
                    .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                    .setColor(getResources().getColor(R.color.colorPrimary))
                    .build();
            notificationManager.notify(ServiceConstants.NOTIFICATION_ID, notification);
            // Notify outside listeners
            if (eventReception!=null) eventReception.progressChange(location, routeProgress);
        }

    }

    @Override
    public void userOffRoute(Location location) {
        Position newOrigin = Position.fromCoordinates(location.getLongitude(), location.getLatitude());
        navigation.getRoute(newOrigin, destination, location.getBearing(), new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                HermesNavigationService.this.route = response.body().getRoutes().get(0);

                if (eventReception!=null) eventReception.serviceReady(HermesNavigationService.this.route, HermesNavigationService.this.locationEngine, HermesNavigationService.this.destination);

                /*// Remove old route line from map and draw the new one.
                if (routeLine != null) {
                    mapboxMap.removePolyline(routeLine);
                }
                drawRouteLine(route);*/
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                Timber.e("onFailure: navigation.getRoute()", throwable);
            }
        });
    }

    public interface OnNavigationServiceReady
    {
        void serviceReady(DirectionsRoute directionsRoute, LocationEngine locationEngine, Position destination);
        void progressChange(Location location, RouteProgress routeProgress);
        void serviceTerminate();
    }
}
