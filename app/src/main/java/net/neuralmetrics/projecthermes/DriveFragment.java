package net.neuralmetrics.projecthermes;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.MyLocationTracking;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.services.Constants;
import com.mapbox.services.android.navigation.v5.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.RouteProgress;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.permissions.PermissionsManager;
import com.mapbox.services.api.directions.v5.models.DirectionsRoute;
import com.mapbox.services.api.directions.v5.models.StepManeuver;
import com.mapbox.services.api.utils.turf.TurfConstants;
import com.mapbox.services.api.utils.turf.TurfMeasurement;
import com.mapbox.services.commons.geojson.LineString;
import com.mapbox.services.commons.models.Position;

import net.neuralmetrics.projecthermes.navigationservice.HermesNavigationService;
import net.neuralmetrics.projecthermes.navigationservice.ServiceConstants;
import net.neuralmetrics.projecthermes.utils.ResourceUtils;
import net.neuralmetrics.projecthermes.utils.ServiceRunningUtils;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * to handle interaction events.
 * Use the {@link DriveFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class DriveFragment extends Fragment implements OnMapReadyCallback, HermesNavigationService.OnNavigationServiceReady {

    private static final String LOG_TAG = "DriveFragment";

    private static boolean RESUME_NAVIGATION_FLAG = false;
    private boolean LIFECYCLE_RESUME_FLAG = false;

    private MapView mapView;
    private LocationEngine locationEngine;
    private MapboxNavigation navigation;
    private MapboxMap mapboxMap;
    private Polyline routeLine;
    private Marker destinationMarker;
    private DirectionsRoute route;
    private Position destination;
    private TextView txtCurrentRoute;
    private TextView txtInstruction;
    private ImageView imgCue;
    private FloatingActionButton quitNavButton;
    private OnRequestReturnToMapFragment returnToMapFragmentListener;

    private HermesNavigationService navService;
    private boolean navServiceBound = false;
    private ServiceConnection navServiceConnect;

    // private OnFragmentInteractionListener mListener;

    public DriveFragment() {
        // Required empty public constructor
    }

    public void setReturnToMapFragmentListener(OnRequestReturnToMapFragment mListener)
    {
        returnToMapFragmentListener = mListener;
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment DriveFragment.
     */
    public static DriveFragment newInstance(double latitude, double longitude) {
        DriveFragment fragment = new DriveFragment();
        Bundle args = new Bundle();
        args.putDouble("latitude",latitude);
        args.putDouble("longitude",longitude);
        fragment.setArguments(args);
        return fragment;
    }

    public static DriveFragment newInstance() {
        DriveFragment fragment = new DriveFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        RESUME_NAVIGATION_FLAG = true;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this.getContext(), getActivity().getResources().getString(R.string.mapbox_access_token));
        getActivity().setTitle("Navigation");
        setHasOptionsMenu(true);
        if (getArguments() != null) {
            // RESUME NAVIGATION PROCEDURE
            if (RESUME_NAVIGATION_FLAG)
            {
                bindServiceToFragment();
                return;
            }

            // NORMAL START PROCEDURE
            // Interpret destination coordinates
            double lat = getArguments().getDouble("latitude");
            double lon = getArguments().getDouble("longitude");

            if(ServiceRunningUtils.isMyServiceRunning(HermesNavigationService.class, getActivity().getApplicationContext()))
            {
                Intent endIntent = new Intent(getActivity().getApplicationContext(), HermesNavigationService.class);
                endIntent.setAction(ServiceConstants.STOP_FOREGROUND);
                getActivity().getApplicationContext().startService(endIntent);
            }

            // Start the Navigation Service
            Log.i(LOG_TAG, "Attempting to start navigation service");
            Intent startIntent = new Intent(getActivity().getApplicationContext(), HermesNavigationService.class);
            startIntent.setAction(ServiceConstants.START_FOREGROUND);
            startIntent.putExtra(ServiceConstants.LATITUDE,lat);
            startIntent.putExtra(ServiceConstants.LONGITUDE,lon);
            getActivity().getApplicationContext().startService(startIntent);

            // Bind service
            bindServiceToFragment();

            // Configure the destination field
            destination=Position.fromCoordinates(lon,lat);
        }
    }

    // Service binding and unbinding functions

    private void bindServiceToFragment()
    {
        // Bind the Navigation Service to current fragment
        if(navService==null)
        {
            navServiceConnect = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    navServiceBound=true;
                    HermesNavigationService.NavigationServiceBinder binder = (HermesNavigationService.NavigationServiceBinder) service;
                    navService = binder.getService();
                    navService.setEventReception(DriveFragment.this);
                    Log.i(LOG_TAG, "Navigation Service connected");
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.i(LOG_TAG, "Navigation Service disconnected");
                    navService = null; // Not related to unbind anyway!
                    navServiceBound=false;
                }
            };
            Intent i = new Intent(getActivity().getApplicationContext(), HermesNavigationService.class);
            getActivity().getApplicationContext().bindService(i, navServiceConnect, Context.BIND_AUTO_CREATE);
        }
    }

    private void unbindServiceFromFragment()
    {
        if (navService!=null)
        {
            getActivity().getApplicationContext().unbindService(navServiceConnect);
            navService.removeEventReception();
            navService=null;
            Log.i(LOG_TAG, "Navigation Service unbind");
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_drive, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Prepare mapView
        mapView=(MapView) getView().findViewById(R.id.map_box);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // UI components fields assignment
        txtCurrentRoute = (TextView) getView().findViewById(R.id.txtRoadTravelling);
        txtInstruction = (TextView) getView().findViewById(R.id.txtDirections);
        imgCue = (ImageView) getView().findViewById(R.id.imgVisualCue);

        quitNavButton = (FloatingActionButton) getView().findViewById(R.id.quit_nav_button);
        quitNavButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(ServiceRunningUtils.isMyServiceRunning(HermesNavigationService.class, getActivity().getApplicationContext()))
                {
                    Intent endIntent = new Intent(getActivity().getApplicationContext(), HermesNavigationService.class);
                    endIntent.setAction(ServiceConstants.STOP_FOREGROUND);
                    getActivity().getApplicationContext().startService(endIntent);
                }
                ((HomeActivity)getActivity()).requestFragmentMap();
            }
        });
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {

        this.mapboxMap = mapboxMap;
        if (navService!=null && RESUME_NAVIGATION_FLAG) // Resume when activity is destroyed and notification is tapped
        {
            navService.onReattachListener();
            RESUME_NAVIGATION_FLAG=false;
        }
        if (navService!=null && LIFECYCLE_RESUME_FLAG) // Resume from backstack
        {
            navService.setEventReception(DriveFragment.this);
            navService.onReattachListener();
            LIFECYCLE_RESUME_FLAG=false;
        }
    }

    // Markers and route illustrating

    private void drawRouteLine(DirectionsRoute route) {
        List<Position> positions = LineString.fromPolyline(route.getGeometry(), Constants.PRECISION_6).getCoordinates();
        List<LatLng> latLngs = new ArrayList<>();
        for (Position position : positions) {
            latLngs.add(new LatLng(position.getLatitude(), position.getLongitude()));
        }

        // Remove old route if currently being shown on map.
        if (routeLine != null) {
            mapboxMap.removePolyline(routeLine);
        }

        routeLine = mapboxMap.addPolyline(new PolylineOptions()
                .addAll(latLngs)
                .color(Color.parseColor("#56b881"))
                .width(5f));
    }

    private void decorateMap() {
        Location userLocation = mapboxMap.getMyLocation();
        if (userLocation == null) {
            Timber.d("calculateRoute: User location is null, therefore, origin can't be set.");
            return;
        }
        Position origin = (Position.fromCoordinates(userLocation.getLongitude(), userLocation.getLatitude()));
        // Zoom to the map
        mapboxMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(origin.getLatitude(),origin.getLongitude()), 16));
        // Some marker decoration
        if (TurfMeasurement.distance(origin, destination, TurfConstants.UNIT_METERS) < 50) {
            mapboxMap.removeMarker(destinationMarker);
            return;
        }
    }

    // Listeners

    /*
     *   Service listeners
     */

    @Override
    public void serviceReady(DirectionsRoute directionsRoute, LocationEngine locationEngine, Position destination) {
        this.destination = destination;
        // mapboxMap.setLocationSource(locationEngine);
        // Enable location tracking
        if (PermissionsManager.areLocationPermissionsGranted(getActivity())) {
            mapboxMap.setMyLocationEnabled(true);
            mapboxMap.getTrackingSettings().setMyLocationTrackingMode(MyLocationTracking.TRACKING_FOLLOW);
            mapboxMap.getTrackingSettings().setDismissAllTrackingOnGesture(false);
        }
        drawRouteLine(directionsRoute);
        decorateMap();
    }

    @Override
    public void progressChange(Location location, RouteProgress routeProgress) {
        // Log.i(LOG_TAG, routeProgress.getCurrentLegProgress().getCurrentStep().getName());
        txtCurrentRoute.setText(routeProgress.getCurrentLegProgress().getCurrentStep().getName());
        // Log.i(LOG_TAG, routeProgress.getCurrentLegProgress().getUpComingStep().getManeuver().getInstruction());
        double timeToStep = routeProgress.getCurrentLegProgress().getCurrentStepProgress().getDurationRemaining();
        StepManeuver m = routeProgress.getCurrentLegProgress().getUpComingStep().getManeuver();
        txtInstruction.setText((timeToStep<500?String.format("%.0f",timeToStep):"500+") + " | " + m.getInstruction().toUpperCase());
        String cueResourceString = "direction_"+m.getType()+"_"+m.getModifier();
        cueResourceString = cueResourceString.replace(" ","_");
        int cueResourceId = ResourceUtils.getResourceId(getActivity(),cueResourceString,"drawable",getActivity().getPackageName());
        imgCue.setImageResource(cueResourceId);
        // Log.i(LOG_TAG, String.valueOf(routeProgress.getCurrentLegProgress().getCurrentStepProgress().getDurationRemaining()));
    }

    @Override
    public void serviceTerminate() {
        // Clean up all the exposed resources from service
        unbindServiceFromFragment();
    }

    /*@Override
    public void onRunning(boolean running) {
        if (running) {
            Timber.d("onRunning: Started");
        } else {
            Timber.d("onRunning: Stopped");
        }
    }*/

    /*@Override
    public void onProgressChange(Location location, RouteProgress routeProgress) {
        Timber.d("onProgressChange: fraction of route traveled: %f", routeProgress.getFractionTraveled());
        // Log.i(LOG_TAG, routeProgress.getCurrentLegProgress().getCurrentStep().getName());
        txtCurrentRoute.setText(routeProgress.getCurrentLegProgress().getCurrentStep().getName());
        // Log.i(LOG_TAG, routeProgress.getCurrentLegProgress().getUpComingStep().getManeuver().getInstruction());
        double timeToStep = routeProgress.getCurrentLegProgress().getCurrentStepProgress().getDurationRemaining();
        StepManeuver m = routeProgress.getCurrentLegProgress().getUpComingStep().getManeuver();
        txtInstruction.setText((timeToStep<500?String.format("%.0f",timeToStep):"500+") + " | " + m.getInstruction().toUpperCase());
        String cueResourceString = "direction_"+m.getType()+"_"+m.getModifier();
        cueResourceString = cueResourceString.replace(" ","_");
        int cueResourceId = ResourceUtils.getResourceId(getActivity(),cueResourceString,"drawable",getActivity().getPackageName());
        imgCue.setImageResource(cueResourceId);
        // Log.i(LOG_TAG, String.valueOf(routeProgress.getCurrentLegProgress().getCurrentStepProgress().getDurationRemaining()));
    }*/

    /*@Override
    public void onAlertLevelChange(int alertLevel, RouteProgress routeProgress) {

        switch (alertLevel) {
            *//*case NavigationConstants.HIGH_ALERT_LEVEL:
                Toast.makeText(MockNavigationActivity.this, "HIGH", Toast.LENGTH_LONG).show();
                Log.d("MANEUVER_ALERT","HIGH");
                Log.d("MANEUVER_INSTR",routeProgress.getCurrentLegProgress().getUpComingStep().getManeuver().getInstruction());
                break;
            case NavigationConstants.MEDIUM_ALERT_LEVEL:
                Toast.makeText(MockNavigationActivity.this, "MEDIUM", Toast.LENGTH_LONG).show();
                Log.d("MANEUVER_ALERT","MEDIUM");
                break;
            case NavigationConstants.LOW_ALERT_LEVEL:
                Toast.makeText(MockNavigationActivity.this, "LOW", Toast.LENGTH_LONG).show();
                Log.d("MANEUVER_ALERT","LOW");
                break;
            case NavigationConstants.ARRIVE_ALERT_LEVEL:
                Toast.makeText(MockNavigationActivity.this, "ARRIVE", Toast.LENGTH_LONG).show();
                break;
            case NavigationConstants.DEPART_ALERT_LEVEL:
                Toast.makeText(MockNavigationActivity.this, "DEPART", Toast.LENGTH_LONG).show();
                break;
            default:
            case NavigationConstants.NONE_ALERT_LEVEL:
                Toast.makeText(MockNavigationActivity.this, "NONE", Toast.LENGTH_LONG).show();
                break;*//*
        }
    }*/

    /*@Override
    public void userOffRoute(Location location) {
        *//*Position newOrigin = Position.fromCoordinates(location.getLongitude(), location.getLatitude());
        navigation.getRoute(newOrigin, destination, location.getBearing(), new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                DirectionsRoute route = response.body().getRoutes().get(0);
                MockNavigationActivity.this.route = route;

                // Remove old route line from map and draw the new one.
                if (routeLine != null) {
                    mapboxMap.removePolyline(routeLine);
                }
                drawRouteLine(route);
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                Timber.e("onFailure: navigation.getRoute()", throwable);
            }
        });*//*
    }*/

    // Life cycle methods

    public interface OnRequestReturnToMapFragment
    {
        void onRequestReturnToMap();
    }

    private void requestReturnToMapFragment()
    {
        if (returnToMapFragmentListener!=null) returnToMapFragmentListener.onRequestReturnToMap();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        /*if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }*/
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // mListener = null;
    }

    @Override
    public void onResume() {
        mapView.onResume();
        Log.i(LOG_TAG, "DriveFragment OnResume");
        if (navService!=null) {
            LIFECYCLE_RESUME_FLAG = true;
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        Log.i(LOG_TAG, "DriveFragment OnPause");
        if (navService!=null) navService.removeEventReception();
        mapboxMap.setLocationSource(null);
        mapView.onPause();
        super.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i(LOG_TAG, "DriveFragment OnStart");
        getActivity().setTitle("Navigation");
        // bindServiceToFragment();
        mapView.onStart();
    }

    @Override
    public void onStop() {
        Log.i(LOG_TAG, "DriveFragment OnStop");
        // unbindServiceFromFragment();
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onDestroy() {
        Log.i(LOG_TAG, "DriveFragment is prepared to be destroyed");
        unbindServiceFromFragment();
        if (navService!=null) navService.removeEventReception();
        mapboxMap.setLocationSource(null);
        returnToMapFragmentListener = null;
        mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }*/
}
