package com.esoxjem.movieguide.listing;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.test.espresso.IdlingResource;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;


import com.esoxjem.movieguide.R;
import com.esoxjem.movieguide.Constants;
import com.esoxjem.movieguide.details.MovieDetailsActivity;
import com.esoxjem.movieguide.details.MovieDetailsFragment;
import com.esoxjem.movieguide.Movie;
import com.esoxjem.movieguide.util.RxUtils;
import com.esoxjem.movieguide.util.EspressoIdlingResource;
import com.jakewharton.rxbinding2.support.v7.widget.RxSearchView;
import java.util.concurrent.TimeUnit;
import io.reactivex.disposables.Disposable;
import com.vungle.warren.Vungle;
import com.vungle.warren.InitCallback;          // Initialization callback
import com.vungle.warren.LoadAdCallback;        // Load ad callback
import com.vungle.warren.PlayAdCallback;        // Play ad callback
import com.vungle.warren.error.VungleException;



public class MoviesListingActivity extends AppCompatActivity implements MoviesListingFragment.Callback {
    public static final String DETAILS_FRAGMENT = "DetailsFragment";
    public static final String appId = "5cf3a7a5cf7fcd0018c1450a";   //Vungle appId
    public static final String placementReferenceId = "DEFAULT-5746877";  //Vungle placementReferenceId
    public static final String logSDKTag = "VungleSDK";  //Vungle SDK LOG TAG
    private boolean twoPaneMode;
    private Disposable searchViewTextSubscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setToolbar();

        if (findViewById(R.id.movie_details_container) != null) {
            twoPaneMode = true;

            if (savedInstanceState == null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.movie_details_container, new MovieDetailsFragment())
                        .commit();
            }
        } else {
            twoPaneMode = false;
        }

        // Initialize the New v6 Vungle API
        initializeVungleSDK();

    }

    private void setToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.movie_guide);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        final MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        final MoviesListingFragment mlFragment = (MoviesListingFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_listing);
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                MoviesListingFragment mlFragment = (MoviesListingFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_listing);
                mlFragment.searchViewBackButtonClicked();
                return true;
            }
        });

        searchViewTextSubscription = RxSearchView.queryTextChanges(searchView)
                .debounce(500, TimeUnit.MILLISECONDS)
                .subscribe(charSequence -> {
                    if (charSequence.length() > 0) {
                        mlFragment.searchViewClicked(charSequence.toString());
                    }
                });

        return true;
    }

    @Override
    public void onMoviesLoaded(Movie movie) {
        if (twoPaneMode) {
            loadMovieFragment(movie);
        } else {
            // Do not load in single pane view
        }
    }

    @Override
    public void onMovieClicked(Movie movie) {
        if (twoPaneMode) {
            loadMovieFragment(movie);
        } else {
            //Each time we click on a movie, we will play an ad.
            loadVugleAd(); //load the ad
            //play the ad
            if (Vungle.canPlayAd(placementReferenceId)) {
                Vungle.playAd(placementReferenceId, null, new PlayAdCallback() {
                    @Override
                    public void onAdStart(String placementReferenceId) { }

                    @Override
                    public void onAdEnd(String placementReferenceId, boolean completed, boolean isCTAClicked) {
                        //After playing the ad, enter the movie details page
                        startMovieActivity(movie);
                    }

                    @Override
                    public void onError(String placementReferenceId, Throwable throwable) {
                        // Play ad error occurred - throwable.getLocalizedMessage() contains error message
                        try {
                            VungleException ex = (VungleException) throwable;

                            if (ex.getExceptionCode() == VungleException.VUNGLE_NOT_INTIALIZED) {
                                initializeVungleSDK();  //Re-init the Vungle SDK
                            }
                        } catch (ClassCastException cex) {
                            Log.d(logSDKTag, cex.getMessage());
                        }
                    }
                });
            }
        }
    }

    private void startMovieActivity(Movie movie) {
        Intent intent = new Intent(this, MovieDetailsActivity.class);
        Bundle extras = new Bundle();
        extras.putParcelable(Constants.MOVIE, movie);
        intent.putExtras(extras);
        startActivity(intent);
    }

    private void loadMovieFragment(Movie movie) {
        MovieDetailsFragment movieDetailsFragment = MovieDetailsFragment.getInstance(movie);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.movie_details_container, movieDetailsFragment, DETAILS_FRAGMENT)
                .commit();
    }

    public void loadVugleAd(){
        if (Vungle.isInitialized()) {
            Vungle.loadAd(placementReferenceId, new LoadAdCallback() {
                @Override
                public void onAdLoad(String placementReferenceId) {
                    //give some time to load the ad
                    new android.os.Handler().postDelayed(
                            new Runnable() {
                                public void run() {
                                    Log.i("loadingAd","Giving 1 seconds to load the ad.");
                                }
                            }, 1000);
                }

                @Override
                public void onError(String placementReferenceId, Throwable throwable) {
                    // Load ad error occurred - throwable.getLocalizedMessage() contains error message
                    //Log.e(logSDKTag, Log.getStackTraceString(throwable));
                    try {
                        VungleException ex = (VungleException) throwable;

                        if (ex.getExceptionCode() == VungleException.VUNGLE_NOT_INTIALIZED) {
                            initializeVungleSDK();  //Re-init the Vungle SDK
                        }
                    } catch (ClassCastException cex) {
                        Log.d(logSDKTag, cex.getMessage());
                    }
                }
            });
        }
    }

    public void initializeVungleSDK(){
        Vungle.init(appId, getApplicationContext(), new InitCallback() {
            @Override
            public void onSuccess() {
                // Initialization has succeeded and SDK is ready to load an ad or play one if there
                // is one pre-cached already
                Log.d(logSDKTag, "Vungle SDK successfully initialized!");
            }

            @Override
            public void onError(Throwable throwable) {
                // Initialization error occurred - throwable.getLocalizedMessage() contains error message
                Log.e(logSDKTag, Log.getStackTraceString(throwable));
            }

            @Override
            public void onAutoCacheAdAvailable(String placementReferenceId) {
                // Callback to notify when an ad becomes available for the auto-cached placement
                // NOTE: This callback works only for the auto-cached placement. Otherwise, please use
                // LoadAdCallback with loadAd API for loading placements.
            }
        });
    }

    @VisibleForTesting
    @NonNull
    public IdlingResource getCountingIdlingResource() {
        return EspressoIdlingResource.getIdlingResource();
    }

    @Override
    protected void onDestroy() {
        RxUtils.unsubscribe(searchViewTextSubscription);
        super.onDestroy();
    }
}
