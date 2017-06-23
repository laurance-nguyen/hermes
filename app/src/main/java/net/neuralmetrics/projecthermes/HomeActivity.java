package net.neuralmetrics.projecthermes;

import android.app.Service;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.crashlytics.android.Crashlytics;
import com.mapbox.services.android.navigation.v5.listeners.NavigationEventListener;

import net.neuralmetrics.projecthermes.navigationservice.HermesNavigationService;
import net.neuralmetrics.projecthermes.navigationservice.ServiceConstants;
import net.neuralmetrics.projecthermes.utils.ServiceRunningUtils;

import io.fabric.sdk.android.Fabric;

public class HomeActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    ConstraintLayout contentFrame;
    DrawerLayout drawer;
    Fragment chosenMenuFragment; // The fragment chosen from the Navigation Drawer
    NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Crashlytics plugin
        Fabric.with(this, new Crashlytics());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        contentFrame = (ConstraintLayout) findViewById(R.id.content_frame);

        drawer.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override public void onDrawerSlide(View drawerView, float slideOffset) {}
            @Override public void onDrawerOpened(View drawerView) {}
            @Override public void onDrawerStateChanged(int newState) {}

            @Override
            public void onDrawerClosed(View drawerView) {
                //Set your new fragment here
                navReplaceFragment();
            }
        });

        if (getIntent().getAction().equals(ServiceConstants.START_NAV_FRAGMENT) || ServiceRunningUtils.isMyServiceRunning(HermesNavigationService.class,this))
        {
            requestFragmentDrive();
            return;
        }

        // Switch to maps fragment on start
        navigationView.setCheckedItem(R.id.nav_maps);
        chosenMenuFragment = MapFragment.newInstance();
        navReplaceFragment();
    }

    void navReplaceFragment()
    {
        if (chosenMenuFragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.content_frame, chosenMenuFragment)
                    .addToBackStack(null)
                    .commit();
            chosenMenuFragment = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {

        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_maps) {
            chosenMenuFragment = MapFragment.newInstance();
        } else if (id == R.id.nav_drive) {
            if (ServiceRunningUtils.isMyServiceRunning(HermesNavigationService.class,this))
            {
                requestFragmentDrive();
                return true;
            }
            AlertDialog.Builder notLaunchedFromMapsDialogBuilder = new AlertDialog.Builder(this)
                    .setTitle("Action not permitted")
                    .setMessage("Drive function can not be launched directly from menu. Please use the Maps function to choose location first.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Do nothing!
                        }
                    });
            /*AlertDialog notLaunchedFromMapsDialog = */ notLaunchedFromMapsDialogBuilder.show();
            return false;
        } else if (id == R.id.nav_about)
        {
            chosenMenuFragment=AboutFragment.newInstance();
            navigationView.setCheckedItem(R.id.nav_hidden);
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;

    }

    // Request fragment drive by MapFragment to switch to driving mode
    public void requestFragmentDrive(double lat, double lon) {
        chosenMenuFragment = DriveFragment.newInstance(lat, lon);
        navigationView.setCheckedItem(R.id.nav_drive);
        navReplaceFragment();
    }

    public void requestFragmentMap()
    {
        chosenMenuFragment = MapFragment.newInstance();
        navigationView.setCheckedItem(R.id.nav_maps);
        navReplaceFragment();
    }

    public void requestFragmentDrive()
    {
        chosenMenuFragment = DriveFragment.newInstance();
        navigationView.setCheckedItem(R.id.nav_drive);
        navReplaceFragment();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
