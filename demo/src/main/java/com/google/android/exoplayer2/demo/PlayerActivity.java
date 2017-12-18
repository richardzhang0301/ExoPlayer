/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.UUID;

import javax.vecmath.Vector3d;

import static com.google.android.exoplayer2.Player.STATE_READY;

/**
 * An activity that plays media using {@link SimpleExoPlayer}.
 */
public class PlayerActivity extends Activity implements OnClickListener, EventListener,
    PlaybackControlView.VisibilityListener {

  public static final String DRM_SCHEME_UUID_EXTRA = "drm_scheme_uuid";
  public static final String DRM_LICENSE_URL = "drm_license_url";
  public static final String DRM_KEY_REQUEST_PROPERTIES = "drm_key_request_properties";
  public static final String PREFER_EXTENSION_DECODERS = "prefer_extension_decoders";

  public static final String ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW";
  public static final String EXTENSION_EXTRA = "extension";

  public static final String ACTION_VIEW_LIST =
      "com.google.android.exoplayer.demo.action.VIEW_LIST";
  public static final String URI_LIST_EXTRA = "uri_list";
  public static final String EXTENSION_LIST_EXTRA = "extension_list";
  public static final String AD_TAG_URI_EXTRA = "ad_tag_uri";

  private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
  private static final CookieManager DEFAULT_COOKIE_MANAGER;
  static {
    DEFAULT_COOKIE_MANAGER = new CookieManager();
    DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  private Handler mainHandler;
  private EventLogger eventLogger;
  private SimpleExoPlayerView simpleExoPlayerView;
  private LinearLayout debugRootView;
  private TextView debugTextView;
  private Button retryButton;

  private DataSource.Factory mediaDataSourceFactory;
  private SimpleExoPlayer player;
  private DefaultTrackSelector trackSelector;
  private TrackSelectionHelper trackSelectionHelper;
  private DebugTextViewHelper debugViewHelper;
  private boolean inErrorState;
  private TrackGroupArray lastSeenTrackGroupArray;

  private boolean shouldAutoPlay;
  private int resumeWindow;
  private long resumePosition;

  // Fields used only for ad playback. The ads loader is loaded via reflection.

  private Object imaAdsLoader; // com.google.android.exoplayer2.ext.ima.ImaAdsLoader
  private Uri loadedAdTagUri;
  private ViewGroup adOverlayViewGroup;

  private SensorManager mSensorManager;

  private boolean hasAudioTracksSelected = false;
  private double initialAzimuth = 0;
  private double currentAzimuth = 0;
  private int audioTracksCount = 0;

  private final int MAX_CHANNEL_COUNT = 8;
  private final int DEFAULT_CHANNEL_COUNT = 8;
  private final int LFE_CHANNEL_ID = 3;
  private final boolean HAS_LFE = true;
  private final Vector3d FRONT_VEC = new Vector3d(0, 0, 1);

  private double[][] volumeMatrix = new double[MAX_CHANNEL_COUNT][MAX_CHANNEL_COUNT];

  public class DegreeDiff {
    public double dotValue;
    public double crossValue;
    public double angleValue;
    public int speakerIndex;
  }

  //Add Gyroscope sensor to detect the azimuth change for 8Ball audio rendering
  SensorEventListener mSensorListener = new SensorEventListener() {
    @Override
    public void onSensorChanged(SensorEvent event) {
      float rotationMatrix[];
      switch(event.sensor.getType())
      {
        case Sensor.TYPE_GAME_ROTATION_VECTOR:
          rotationMatrix=new float[16];
          mSensorManager.getRotationMatrixFromVector(rotationMatrix,event.values);
          double azimuth = getAzimuth(rotationMatrix);

          if(initialAzimuth == 0)
          {
            initialAzimuth = azimuth;
          }
          else
          {
            double relativeAzimuth = azimuth - initialAzimuth;
            //Log.i("PlayerActivity", "relativeAzimuth:" + Math.toDegrees(relativeAzimuth));
            if(relativeAzimuth > Math.PI)
            {
              currentAzimuth = relativeAzimuth - Math.PI * 2;
            }
            else if(relativeAzimuth < -Math.PI)
            {
              currentAzimuth = relativeAzimuth + Math.PI * 2;
            }
            else
            {
              currentAzimuth = relativeAzimuth;
            }
            //Log.i("PlayerActivity", "currentAzimuth:" + Math.toDegrees(currentAzimuth));

            if(player != null && player.getPlaybackState() == STATE_READY && trackSelector != null)
            {
              //Update 8 channels' volume according to the detected azimuth
              update8BallVolumes(currentAzimuth);
              updateVolumeMatrix(-currentAzimuth, volumeMatrix);
              player.setVolumeMatrix(volumeMatrix);
              //player.setAzimuth(azimuth);
              debugViewHelper.setAzimuth(-currentAzimuth);
              debugViewHelper.setVolumeMatrix(volumeMatrix);
            }
          }
          break;
      }
    }

    private double getAzimuth(float[] rotationMatrix)
    {
      float[] orientationValues = new float[3];
      SensorManager.getOrientation(rotationMatrix, orientationValues);
      double azimuth = orientationValues[0];

      //Log.i("PlayerActivity", "azimuth:" + Math.toDegrees(azimuth));

      return azimuth;
    }

    private void update8BallVolumes(double azimuth)
    {
      //player.azimuth = azimuth;

      double frontVol = 0;
      double leftVol = 0;
      double backVol = 0;
      double rightVol = 0;

      if (azimuth <= Math.PI / 2 && azimuth >= 0)
      {
        frontVol = Math.cos (azimuth);
        rightVol = Math.sin (azimuth);
        backVol = 0;
        leftVol = 0;
      }
      else if (azimuth > Math.PI / 2 && azimuth <= Math.PI)
      {
        frontVol = 0;
        rightVol = Math.cos (azimuth - Math.PI / 2);
        backVol = Math.sin (azimuth - Math.PI / 2);
        leftVol = 0;
      }
      else if (azimuth < -Math.PI /2 && azimuth >= -Math.PI)
      {
        frontVol = 0;
        rightVol = 0;
        backVol = Math.sin (-azimuth - Math.PI / 2);
        leftVol = Math.cos (-azimuth - Math.PI / 2);
      }
      else if (azimuth < 0 && azimuth >= -Math.PI / 2)
      {
        frontVol = Math.cos (-azimuth);
        rightVol = 0;
        backVol = 0;
        leftVol = Math.sin (-azimuth);
      }

      float[] volumes = new float[] {(float)frontVol, (float)leftVol, (float)backVol, (float)rightVol};
      player.set8BallVolume(volumes);
      player.setAzimuth(azimuth);

      Log.i("PlayerActivity", "azimuth:" + Math.toDegrees(azimuth) + ", front:" + frontVol + ", right:" + rightVol + ", backVol:" + backVol + ", leftVol:" + leftVol);
    }

    private void updateVolumeMatrix(double azimuth, double[][] volumeMatrix) {
      double[] speakerPos = new double[MAX_CHANNEL_COUNT];
      Vector3d[] speakerVec = new Vector3d[MAX_CHANNEL_COUNT];
      Vector3d[] rotatedSpeakerVec = new Vector3d[MAX_CHANNEL_COUNT];

      Vector3d rotatedFrontVec = rotate(azimuth, FRONT_VEC);
/*
        speakerPos[0] = Math.toRadians(-30);
        speakerPos[1] = Math.toRadians(30);
        speakerPos[2] = Math.toRadians(0);
        speakerPos[3] = Math.toRadians(0);
        speakerPos[4] = Math.toRadians(-120);
        speakerPos[5] = Math.toRadians(120);
*/

      speakerPos[0] = Math.toRadians(-45);
      speakerPos[1] = Math.toRadians(45);
      speakerPos[2] = Math.toRadians(0);
      speakerPos[3] = Math.toRadians(0);
      speakerPos[4] = Math.toRadians(-135);
      speakerPos[5] = Math.toRadians(135);
      speakerPos[6] = Math.toRadians(-90);
      speakerPos[7] = Math.toRadians(90);

      for(int i = 0; i < DEFAULT_CHANNEL_COUNT; i++) {
        speakerVec[i] = rotate(speakerPos[i], FRONT_VEC);
      }

      for(int i = 0; i < DEFAULT_CHANNEL_COUNT; i++) {
        if(HAS_LFE && i == LFE_CHANNEL_ID)
          continue;

        rotatedSpeakerVec[i] = rotate(speakerPos[i], rotatedFrontVec);
      }




      for(int i = 0; i < DEFAULT_CHANNEL_COUNT; i++) {
        if(HAS_LFE && i == LFE_CHANNEL_ID)
          continue;

        DegreeDiff[] degreeDiffArrayL = new DegreeDiff[MAX_CHANNEL_COUNT];
        DegreeDiff[] degreeDiffArrayR = new DegreeDiff[MAX_CHANNEL_COUNT];
        int degreeDiffArrayCountL = 0;
        int degreeDiffArrayCountR = 0;

        for (int speakerPosID = 0; speakerPosID < DEFAULT_CHANNEL_COUNT; speakerPosID++) {
          if(HAS_LFE && speakerPosID == LFE_CHANNEL_ID)
            continue;

          //double dotValue = rotatedSpeakerVec[i].dot(speakerVec[speakerPosID]);
          Vector3d crossVec = new Vector3d();
          crossVec.cross(rotatedSpeakerVec[i], speakerVec[speakerPosID]);
          double angleValue = rotatedSpeakerVec[i].angle(speakerVec[speakerPosID]);
          //if(dotValue <= 0)
          //continue;

          DegreeDiff degreeDiff = new DegreeDiff();
          //degreeDiff.dotValue = dotValue;
          degreeDiff.crossValue = crossVec.y;
          degreeDiff.angleValue = angleValue;
          degreeDiff.speakerIndex = speakerPosID;

          if(degreeDiff.angleValue == 0 /*|| Math.abs(degreeDiff.dotValue) < 0.001*/) {
            degreeDiffArrayCountL = 0;
            degreeDiffArrayCountR = 0;
            break;
          }
          //Select the nearest speaker from left
          else if(degreeDiff.crossValue < 0) {
            orderInsert(degreeDiffArrayL, 0, degreeDiffArrayCountL, degreeDiff);
            degreeDiffArrayCountL++;
          }
          //Select the nearest speaker from right
          else {
            orderInsert(degreeDiffArrayR, 0, degreeDiffArrayCountR, degreeDiff);
            degreeDiffArrayCountR++;
          }
        }

        if(degreeDiffArrayCountL != 0 && degreeDiffArrayCountR != 0) {
          //Calculate volume distribution
          int speakerIndex0 = degreeDiffArrayL[0].speakerIndex;
          double angle0 = degreeDiffArrayL[0].angleValue;
          //double dot0 = degreeDiffArrayL[0].dotValue;
          int speakerIndex1 = degreeDiffArrayR[0].speakerIndex;
          double angle1 = degreeDiffArrayR[0].angleValue;
          //double dot1 = degreeDiffArrayR[0].dotValue;

          //double actAngle0 = Math.sin(angle0);
          //double actAngle1 = Math.cos(angle1);
          double speaker0Vol = angle1 / (angle0 + angle1);
          double speaker1Vol = angle0 / (angle0 + angle1);
          //double speaker0ActVol = Math.sin(speaker0Vol * Math.PI / 2);
          //double speaker1ActVol = Math.cos(speaker0Vol * Math.PI / 2);

          //double speaker0Vol = dot0 / (dot0 + dot1);
          //double speaker1Vol = dot1 / (dot0 + dot1);

          volumeMatrix[i][speakerIndex0] = speaker0Vol;
          volumeMatrix[i][speakerIndex1] = speaker1Vol;

          for(int j = 0; j < DEFAULT_CHANNEL_COUNT; j++) {
            if(j == speakerIndex0 || j == speakerIndex1)
              continue;

            if(HAS_LFE && j == LFE_CHANNEL_ID)
              continue;

            volumeMatrix[i][j] = 0;
          }
        }
        else {
          volumeMatrix[i][i] = 1;

          for(int j = 0; j < DEFAULT_CHANNEL_COUNT; j++) {
            if(j == i)
              continue;

            if(HAS_LFE && j == LFE_CHANNEL_ID)
              continue;

            volumeMatrix[i][j] = 0;
          }
        }
      }

      if(HAS_LFE)
        volumeMatrix[LFE_CHANNEL_ID][LFE_CHANNEL_ID] = 1;

      Format format = player.getAudioFormat();
      if(format != null) {
        int channelCount = format.channelCount;
        //For stereo sound track, split the center SPL to L and R and disable the center
        if(channelCount == 2) {
          for(int i = 0; i < volumeMatrix.length; i++) {
            double centerVolume = volumeMatrix[i][2];
            volumeMatrix[i][2] = 0;
            volumeMatrix[i][0] += (centerVolume / 2.0);
            volumeMatrix[i][1] += (centerVolume / 2.0);
          }
        }
      }
    }

    private Vector3d rotate(double theta, Vector3d in) {
      double cosTheta = Math.cos(theta);
      double sinTheta = Math.sin(theta);
      Vector3d out = new Vector3d();
      out.x = in.x * cosTheta + in.z * sinTheta;
      out.y = in.y;
      out.z = -in.x * sinTheta + in.z * cosTheta;
      out.normalize();
      return out;
    }

    private int orderInsert(DegreeDiff[] arr, int first, int last, DegreeDiff target) {
      int i = last;
      while((i > first) && (target.angleValue < arr[i - 1].angleValue)) {
        //while((i > first) && (target.dotValue > arr[i - 1].dotValue)) {
        arr[i] = arr[i - 1];
        i = i - 1;
      }
      arr[i] = target;
      return i;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
      // TODO Auto-generated method stub

    }
  };

  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    shouldAutoPlay = true;
    clearResumePosition();
    mediaDataSourceFactory = buildDataSourceFactory(true);
    mainHandler = new Handler();
    if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
      CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
    }

    setContentView(R.layout.player_activity);
    View rootView = findViewById(R.id.root);
    rootView.setOnClickListener(this);
    debugRootView = (LinearLayout) findViewById(R.id.controls_root);
    debugTextView = (TextView) findViewById(R.id.debug_text_view);
    retryButton = (Button) findViewById(R.id.retry_button);
    retryButton.setOnClickListener(this);

    simpleExoPlayerView = (SimpleExoPlayerView) findViewById(R.id.player_view);
    simpleExoPlayerView.setControllerVisibilityListener(this);
    simpleExoPlayerView.requestFocus();

    //VR Sensors Related (Hear360)
    mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_FASTEST);
  }

  @Override
  public void onNewIntent(Intent intent) {
    releasePlayer();
    shouldAutoPlay = true;
    clearResumePosition();
    setIntent(intent);
  }

  @Override
  public void onStart() {
    super.onStart();
    if (Util.SDK_INT > 23) {
      initializePlayer();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if ((Util.SDK_INT <= 23 || player == null)) {
      initializePlayer();
    }

    mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_FASTEST);
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      releasePlayer();
    }

    mSensorManager.unregisterListener(mSensorListener);
  }

  @Override
  public void onStop() {
    super.onStop();
    if (Util.SDK_INT > 23) {
      releasePlayer();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    releaseAdsLoader();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      initializePlayer();
    } else {
      showToast(R.string.storage_permission_denied);
      finish();
    }
  }

  // Activity input

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // If the event was not handled then see if the player view can handle it.
    return super.dispatchKeyEvent(event) || simpleExoPlayerView.dispatchKeyEvent(event);
  }

  // OnClickListener methods

  @Override
  public void onClick(View view) {
    if (view == retryButton) {
      initializePlayer();
    } else if (view.getParent() == debugRootView) {
      MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
      if (mappedTrackInfo != null) {
        trackSelectionHelper.showSelectionDialog(this, ((Button) view).getText(),
            trackSelector.getCurrentMappedTrackInfo(), (int) view.getTag());
      }
    }
  }

  // PlaybackControlView.VisibilityListener implementation

  @Override
  public void onVisibilityChange(int visibility) {
    debugRootView.setVisibility(visibility);
  }

  // Internal methods

  private void initializePlayer() {
    Intent intent = getIntent();
    boolean needNewPlayer = player == null;
    if (needNewPlayer) {
      TrackSelection.Factory adaptiveTrackSelectionFactory =
          new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
      trackSelector = new DefaultTrackSelector(adaptiveTrackSelectionFactory);
      trackSelectionHelper = new TrackSelectionHelper(trackSelector, adaptiveTrackSelectionFactory);
      lastSeenTrackGroupArray = null;
      eventLogger = new EventLogger(trackSelector);

      UUID drmSchemeUuid = intent.hasExtra(DRM_SCHEME_UUID_EXTRA)
          ? UUID.fromString(intent.getStringExtra(DRM_SCHEME_UUID_EXTRA)) : null;
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;
      if (drmSchemeUuid != null) {
        String drmLicenseUrl = intent.getStringExtra(DRM_LICENSE_URL);
        String[] keyRequestPropertiesArray = intent.getStringArrayExtra(DRM_KEY_REQUEST_PROPERTIES);
        int errorStringId = R.string.error_drm_unknown;
        if (Util.SDK_INT < 18) {
          errorStringId = R.string.error_drm_not_supported;
        } else {
          try {
            drmSessionManager = buildDrmSessionManagerV18(drmSchemeUuid, drmLicenseUrl,
                keyRequestPropertiesArray);
          } catch (UnsupportedDrmException e) {
            errorStringId = e.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown;
          }
        }
        if (drmSessionManager == null) {
          showToast(errorStringId);
          return;
        }
      }

      boolean preferExtensionDecoders = intent.getBooleanExtra(PREFER_EXTENSION_DECODERS, false);
      @DefaultRenderersFactory.ExtensionRendererMode int extensionRendererMode =
          ((DemoApplication) getApplication()).useExtensionRenderers()
              ? (preferExtensionDecoders ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
              : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
              : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
      extensionRendererMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
      DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this,
          drmSessionManager, extensionRendererMode);

      player = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector);
      player.addListener(this);
      player.addListener(eventLogger);
      player.addMetadataOutput(eventLogger);
      player.setAudioDebugListener(eventLogger);
      player.setVideoDebugListener(eventLogger);

      simpleExoPlayerView.setPlayer(player);
      player.setPlayWhenReady(shouldAutoPlay);
      debugViewHelper = new DebugTextViewHelper(player, debugTextView);
      debugViewHelper.start();
    }
    String action = intent.getAction();
    Uri[] uris;
    String[] extensions;
    if (ACTION_VIEW.equals(action)) {
      uris = new Uri[]{intent.getData()};
      extensions = new String[]{intent.getStringExtra(EXTENSION_EXTRA)};
    } else if (ACTION_VIEW_LIST.equals(action)) {
      String[] uriStrings = intent.getStringArrayExtra(URI_LIST_EXTRA);
      uris = new Uri[uriStrings.length];
      for (int i = 0; i < uriStrings.length; i++) {
        uris[i] = Uri.parse(uriStrings[i]);
      }
      extensions = intent.getStringArrayExtra(EXTENSION_LIST_EXTRA);
      if (extensions == null) {
        extensions = new String[uriStrings.length];
      }
    } else {
      showToast(getString(R.string.unexpected_intent_action, action));
      return;
    }
    if (Util.maybeRequestReadExternalStoragePermission(this, uris)) {
      // The player will be reinitialized if the permission is granted.
      return;
    }
    MediaSource[] mediaSources = new MediaSource[uris.length];
    for (int i = 0; i < uris.length; i++) {
      mediaSources[i] = buildMediaSource(uris[i], extensions[i]);
    }
    MediaSource mediaSource = mediaSources.length == 1 ? mediaSources[0]
        : new ConcatenatingMediaSource(mediaSources);
    String adTagUriString = intent.getStringExtra(AD_TAG_URI_EXTRA);
    if (adTagUriString != null) {
      Uri adTagUri = Uri.parse(adTagUriString);
      if (!adTagUri.equals(loadedAdTagUri)) {
        releaseAdsLoader();
        loadedAdTagUri = adTagUri;
      }
      try {
        mediaSource = createAdsMediaSource(mediaSource, Uri.parse(adTagUriString));
      } catch (Exception e) {
        showToast(R.string.ima_not_loaded);
      }
    } else {
      releaseAdsLoader();
    }
    boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
    if (haveResumePosition) {
      player.seekTo(resumeWindow, resumePosition);
    }
    player.prepare(mediaSource, !haveResumePosition, false);
    inErrorState = false;
    updateButtonVisibilities();
  }

  private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
    int type = TextUtils.isEmpty(overrideExtension) ? Util.inferContentType(uri)
        : Util.inferContentType("." + overrideExtension);
    switch (type) {
      case C.TYPE_SS:
        return new SsMediaSource(uri, buildDataSourceFactory(false),
            new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, eventLogger);
      case C.TYPE_DASH:
        return new DashMediaSource(uri, buildDataSourceFactory(false),
            new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, eventLogger);
      case C.TYPE_HLS:
        return new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, eventLogger);
      case C.TYPE_OTHER:
        return new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(),
            mainHandler, eventLogger);
      default: {
        throw new IllegalStateException("Unsupported type: " + type);
      }
    }
  }

  private DrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManagerV18(UUID uuid,
      String licenseUrl, String[] keyRequestPropertiesArray) throws UnsupportedDrmException {
    HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl,
        buildHttpDataSourceFactory(false));
    if (keyRequestPropertiesArray != null) {
      for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
        drmCallback.setKeyRequestProperty(keyRequestPropertiesArray[i],
            keyRequestPropertiesArray[i + 1]);
      }
    }
    return new DefaultDrmSessionManager<>(uuid, FrameworkMediaDrm.newInstance(uuid), drmCallback,
        null, mainHandler, eventLogger);
  }

  private void releasePlayer() {
    if (player != null) {
      debugViewHelper.stop();
      debugViewHelper = null;
      shouldAutoPlay = player.getPlayWhenReady();
      updateResumePosition();
      player.release();
      player = null;
      trackSelector = null;
      trackSelectionHelper = null;
      eventLogger = null;
    }
  }

  private void updateResumePosition() {
    resumeWindow = player.getCurrentWindowIndex();
    resumePosition = Math.max(0, player.getContentPosition());
  }

  private void clearResumePosition() {
    resumeWindow = C.INDEX_UNSET;
    resumePosition = C.TIME_UNSET;
  }

  /**
   * Returns a new DataSource factory.
   *
   * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
   *     DataSource factory.
   * @return A new DataSource factory.
   */
  private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
    return ((DemoApplication) getApplication())
        .buildDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
  }

  /**
   * Returns a new HttpDataSource factory.
   *
   * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
   *     DataSource factory.
   * @return A new HttpDataSource factory.
   */
  private HttpDataSource.Factory buildHttpDataSourceFactory(boolean useBandwidthMeter) {
    return ((DemoApplication) getApplication())
        .buildHttpDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
  }

  /**
   * Returns an ads media source, reusing the ads loader if one exists.
   *
   * @throws Exception Thrown if it was not possible to create an ads media source, for example, due
   *     to a missing dependency.
   */
  private MediaSource createAdsMediaSource(MediaSource mediaSource, Uri adTagUri) throws Exception {
    // Load the extension source using reflection so the demo app doesn't have to depend on it.
    // The ads loader is reused for multiple playbacks, so that ad playback can resume.
    Class<?> loaderClass = Class.forName("com.google.android.exoplayer2.ext.ima.ImaAdsLoader");
    if (imaAdsLoader == null) {
      imaAdsLoader = loaderClass.getConstructor(Context.class, Uri.class)
          .newInstance(this, adTagUri);
      adOverlayViewGroup = new FrameLayout(this);
      // The demo app has a non-null overlay frame layout.
      simpleExoPlayerView.getOverlayFrameLayout().addView(adOverlayViewGroup);
    }
    Class<?> sourceClass =
        Class.forName("com.google.android.exoplayer2.ext.ima.ImaAdsMediaSource");
    Constructor<?> constructor = sourceClass.getConstructor(MediaSource.class,
        DataSource.Factory.class, loaderClass, ViewGroup.class);
    return (MediaSource) constructor.newInstance(mediaSource, mediaDataSourceFactory, imaAdsLoader,
        adOverlayViewGroup);
  }

  private void releaseAdsLoader() {
    if (imaAdsLoader != null) {
      try {
        Class<?> loaderClass = Class.forName("com.google.android.exoplayer2.ext.ima.ImaAdsLoader");
        Method releaseMethod = loaderClass.getMethod("release");
        releaseMethod.invoke(imaAdsLoader);
      } catch (Exception e) {
        // Should never happen.
        throw new IllegalStateException(e);
      }
      imaAdsLoader = null;
      loadedAdTagUri = null;
      simpleExoPlayerView.getOverlayFrameLayout().removeAllViews();
    }
  }

  // Player.EventListener implementation

  @Override
  public void onLoadingChanged(boolean isLoading) {
    // Do nothing.
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    if (playbackState == Player.STATE_ENDED) {
      showControls();
    }
    updateButtonVisibilities();
  }

  @Override
  public void onRepeatModeChanged(int repeatMode) {
    // Do nothing.
  }

  @Override
  public void onPositionDiscontinuity() {
    if (inErrorState) {
      // This will only occur if the user has performed a seek whilst in the error state. Update the
      // resume position so that if the user then retries, playback will resume from the position to
      // which they seeked.
      updateResumePosition();
    }
  }

  @Override
  public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    // Do nothing.
  }

  @Override
  public void onTimelineChanged(Timeline timeline, Object manifest) {
    // Do nothing.
  }

  @Override
  public void onPlayerError(ExoPlaybackException e) {
    String errorString = null;
    if (e.type == ExoPlaybackException.TYPE_RENDERER) {
      Exception cause = e.getRendererException();
      if (cause instanceof DecoderInitializationException) {
        // Special case for decoder initialization failures.
        DecoderInitializationException decoderInitializationException =
            (DecoderInitializationException) cause;
        if (decoderInitializationException.decoderName == null) {
          if (decoderInitializationException.getCause() instanceof DecoderQueryException) {
            errorString = getString(R.string.error_querying_decoders);
          } else if (decoderInitializationException.secureDecoderRequired) {
            errorString = getString(R.string.error_no_secure_decoder,
                decoderInitializationException.mimeType);
          } else {
            errorString = getString(R.string.error_no_decoder,
                decoderInitializationException.mimeType);
          }
        } else {
          errorString = getString(R.string.error_instantiating_decoder,
              decoderInitializationException.decoderName);
        }
      }
    }
    if (errorString != null) {
      showToast(errorString);
    }
    inErrorState = true;
    if (isBehindLiveWindow(e)) {
      clearResumePosition();
      initializePlayer();
    } else {
      updateResumePosition();
      updateButtonVisibilities();
      showControls();
    }
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    updateButtonVisibilities();
    if (trackGroups != lastSeenTrackGroupArray) {
      MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
      if (mappedTrackInfo != null) {
        if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_VIDEO)
            == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
          showToast(R.string.error_unsupported_video);
        }
        if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_AUDIO)
            == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
          showToast(R.string.error_unsupported_audio);
        }
      }
      lastSeenTrackGroupArray = trackGroups;
    }
  }

  // User controls

  private void updateButtonVisibilities() {
    debugRootView.removeAllViews();

    retryButton.setVisibility(inErrorState ? View.VISIBLE : View.GONE);
    debugRootView.addView(retryButton);

    if (player == null) {
      return;
    }

    MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
    if (mappedTrackInfo == null) {
      return;
    }

    for (int i = 0; i < mappedTrackInfo.length; i++) {
      TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(i);
      if (trackGroups.length != 0) {
        Button button = new Button(this);
        int label;
        switch (player.getRendererType(i)) {
          case C.TRACK_TYPE_AUDIO:
            label = R.string.audio;
            break;
          case C.TRACK_TYPE_VIDEO:
            label = R.string.video;
            break;
          case C.TRACK_TYPE_TEXT:
            label = R.string.text;
            break;
          default:
            continue;
        }
        button.setText(label);
        button.setTag(i);
        button.setOnClickListener(this);
        debugRootView.addView(button, debugRootView.getChildCount() - 1);
      }
    }
  }

  private void showControls() {
    debugRootView.setVisibility(View.VISIBLE);
  }

  private void showToast(int messageId) {
    showToast(getString(messageId));
  }

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

  private static boolean isBehindLiveWindow(ExoPlaybackException e) {
    if (e.type != ExoPlaybackException.TYPE_SOURCE) {
      return false;
    }
    Throwable cause = e.getSourceException();
    while (cause != null) {
      if (cause instanceof BehindLiveWindowException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

}
