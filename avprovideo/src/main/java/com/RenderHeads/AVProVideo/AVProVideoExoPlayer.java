package com.RenderHeads.AVProVideo;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;
import com.twobigears.audio360.AudioEngine;
import com.twobigears.audio360.ChannelMap;
import com.twobigears.audio360.SpatDecoderQueue;
import com.twobigears.audio360.TBQuat;
import com.twobigears.audio360.TBVector;
import com.twobigears.audio360exo.SpatialAudioTrack;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Random;

import javax.vecmath.Vector3d;

import static com.google.android.exoplayer2.Player.STATE_READY;

public class AVProVideoExoPlayer
        extends AVProVideoPlayer
        implements Player.EventListener, SimpleExoPlayer.VideoListener, AdaptiveMediaSourceEventListener, ExtractorMediaSource.EventListener
{
    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private static final AdaptiveTrackSelection.Factory ADAPTIVE_TRACK_SELECTION_FACTORY = new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
    private Handler m_MainHandler;
    private SimpleExoPlayer m_ExoPlayer;
    private DefaultTrackSelector m_TrackSelector;
    private EventLogger m_EventLogger;
    private String m_UserAgent;
    private DataSource.Factory m_MediaDataSourceFactory;
    private String m_FilePath;
    private Surface m_Surface;
    private SpatDecoderQueue m_Spat;
    private AudioEngine m_AudioEngine;
    private SpatialAudioTrack m_SpatialTrack;
    private static ChannelMap[] m_ChannelMap;

    private void initChannelMap()
    {
        (m_ChannelMap = new ChannelMap[24])[0] = ChannelMap.TBE_8_2;
        m_ChannelMap[1] = ChannelMap.TBE_8;
        m_ChannelMap[2] = ChannelMap.TBE_6_2;
        m_ChannelMap[3] = ChannelMap.TBE_6;
        m_ChannelMap[4] = ChannelMap.TBE_4_2;
        m_ChannelMap[5] = ChannelMap.TBE_4;
        m_ChannelMap[6] = ChannelMap.TBE_8_PAIR0;
        m_ChannelMap[7] = ChannelMap.TBE_8_PAIR1;
        m_ChannelMap[8] = ChannelMap.TBE_8_PAIR2;
        m_ChannelMap[9] = ChannelMap.TBE_8_PAIR3;
        m_ChannelMap[10] = ChannelMap.TBE_CHANNEL0;
        m_ChannelMap[11] = ChannelMap.TBE_CHANNEL1;
        m_ChannelMap[12] = ChannelMap.TBE_CHANNEL2;
        m_ChannelMap[13] = ChannelMap.TBE_CHANNEL3;
        m_ChannelMap[14] = ChannelMap.TBE_CHANNEL4;
        m_ChannelMap[15] = ChannelMap.TBE_CHANNEL5;
        m_ChannelMap[16] = ChannelMap.TBE_CHANNEL6;
        m_ChannelMap[17] = ChannelMap.TBE_CHANNEL7;
        m_ChannelMap[18] = ChannelMap.HEADLOCKED_STEREO;
        m_ChannelMap[19] = ChannelMap.HEADLOCKED_CHANNEL0;
        m_ChannelMap[20] = ChannelMap.HEADLOCKED_CHANNEL1;
        m_ChannelMap[21] = ChannelMap.AMBIX_4;
        m_ChannelMap[22] = ChannelMap.AMBIX_9;
        m_ChannelMap[23] = ChannelMap.AMBIX_9_2;
    }

    public AVProVideoExoPlayer(int playerIndex, boolean watermarked, Random random)
    {
        super(playerIndex, watermarked, random);
        if (m_ChannelMap == null) {
            initChannelMap();
        }
    }

    public float GetBufferingProgressPercent()
    {
        if (this.m_ExoPlayer != null)
        {
            this.m_fBufferingProgressPercent = this.m_ExoPlayer.getBufferedPercentage();
            return this.m_fBufferingProgressPercent;
        }
        return 0.0F;
    }

    public void SetHeadRotation(float x, float y, float z, float w)
    {
        //System.exit(1);

        if (this.m_AudioEngine != null) {
            this.m_AudioEngine.setListenerRotation(new TBQuat(x, y, z, w));
        }

        updateHeadForHear360(x, y, z, w);
    }

    public void SetFocusRotation(float x, float y, float z, float w)
    {
        if (this.m_Spat != null) {
            this.m_Spat.setFocusOrientationQuat(new TBQuat(x, y, z, w));
        }
    }

    public void SetFocusProps(float offFocusLevel, float widthDegrees)
    {
        if (this.m_Spat != null) {
            this.m_Spat.setFocusProperties(offFocusLevel, widthDegrees);
        }
    }

    public void SetPositionTrackingEnabled(boolean enabled)
    {
        if (this.m_AudioEngine != null) {
            this.m_AudioEngine.enablePositionalTracking(enabled, new TBVector(0.0F, 0.0F, 0.0F));
        }
    }

    public void SetFocusEnabled(boolean enabled)
    {
        if (this.m_Spat != null) {
            this.m_Spat.enableFocus(enabled, true);
        }
    }

    public ChannelMap getChannelMap(int channelID)
    {
        if (m_ChannelMap == null) {
            initChannelMap();
        }
        if ((channelID < 0) || (channelID >= m_ChannelMap.length)) {
            return ChannelMap.TBE_8_2;
        }
        return m_ChannelMap[channelID];
    }

    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio)
    {
        if ((this.m_Width != width) || (this.m_Height != height))
        {
            System.out.println("AVProVideo changing video size " + this.m_Width + "x" + this.m_Height + " to " + width + "x" + height);
            this.m_Width = width;
            this.m_Height = height;

            this.m_bSourceHasVideo = true;

            this.m_bVideo_CreateRenderSurface = true;
            this.m_bVideo_DestroyRenderSurface = false;
        }
    }

    public void onRenderedFirstFrame() {}

    protected boolean InitialisePlayer(final boolean enableAudio360, final int audio360Channels)
    {
        if (this.m_Context == null) {
            return false;
        }
        String version;
        try
        {
            String packageName = this.m_Context.getPackageName();
            PackageInfo info;
            version = this.m_Context.getPackageManager().getPackageInfo(packageName, 0).versionName;
        }
        catch (PackageManager.NameNotFoundException localNameNotFoundException1)
        {
            PackageManager.NameNotFoundException e;
            version = "?";
        }
        this.m_UserAgent = ("AVProMobileVideo/" + version + " (Linux;Android " + Build.VERSION.RELEASE + ") ExoPlayerLib/2.5.4");

        final Activity activity = (Activity)this.m_Context;
        final AVProVideoExoPlayer thisClass = this;

        activity.runOnUiThread(new Runnable()
        {
            public final void run()
            {
                AVProVideoExoPlayer.this.m_MainHandler = new Handler(activity.getMainLooper());
                AVProVideoExoPlayer.this.m_MediaDataSourceFactory = AVProVideoExoPlayer.this.BuildDataSourceFactory(true);
                AVProVideoExoPlayer.this.m_TrackSelector = new DefaultTrackSelector(AVProVideoExoPlayer.ADAPTIVE_TRACK_SELECTION_FACTORY);
                AVProVideoExoPlayer.this.m_EventLogger = new EventLogger(AVProVideoExoPlayer.this.m_TrackSelector);
                DefaultRenderersFactory defaultRenderersFactory;
                RenderersFactory rFactory = defaultRenderersFactory = new DefaultRenderersFactory(AVProVideoExoPlayer.this.m_Context, null, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
                if (enableAudio360)
                {
                    AVProVideoExoPlayer.this.m_AudioEngine = AudioEngine.create(48000.0F, AVProVideoExoPlayer.this.m_Context);
                    AVProVideoExoPlayer.this.m_Spat = AVProVideoExoPlayer.this.m_AudioEngine.createSpatDecoderQueue();
                    AVProVideoExoPlayer.this.m_AudioEngine.start();

                    ChannelMap channelMap = AVProVideoExoPlayer.this.getChannelMap(audio360Channels);
                    AVProVideoExoPlayer.this.m_SpatialTrack = new SpatialAudioTrack(AVProVideoExoPlayer.this.m_Spat, channelMap);

                    rFactory = new OpusRenderersFactory(AVProVideoExoPlayer.this.m_Spat, defaultRenderersFactory, AVProVideoExoPlayer.this.m_SpatialTrack);
                }
                AVProVideoExoPlayer.this.m_ExoPlayer = ExoPlayerFactory.newSimpleInstance(rFactory, AVProVideoExoPlayer.this.m_TrackSelector);
Log.i("AVProVideoExoPlayer", "Create AVProVideoExoPlayer");
                AVProVideoExoPlayer.this.m_ExoPlayer.addListener(AVProVideoExoPlayer.this.m_EventLogger);
                AVProVideoExoPlayer.this.m_ExoPlayer.addListener(thisClass);
                AVProVideoExoPlayer.this.m_ExoPlayer.setAudioDebugListener(AVProVideoExoPlayer.this.m_EventLogger);
                AVProVideoExoPlayer.this.m_ExoPlayer.setVideoDebugListener(AVProVideoExoPlayer.this.m_EventLogger);
                AVProVideoExoPlayer.this.m_ExoPlayer.setMetadataOutput(AVProVideoExoPlayer.this.m_EventLogger);
                AVProVideoExoPlayer.this.m_ExoPlayer.setVideoListener(thisClass);
            }
        });
        return true;
    }

    protected void CloseVideoOnPlayer()
    {
        if (this.m_VideoState >= 3)
        {
            _pause();

            _stop();

            _seek(0);
            if (this.m_AudioEngine != null) {
                if (this.m_Spat != null)
                {
                    this.m_Spat.flushQueue();
                    this.m_SpatialTrack.reset();
                }
            }
            this.m_ExoPlayer.setVideoSurface(null);
            if (this.m_Surface != null)
            {
                this.m_Surface.release();
                this.m_Surface = null;
            }
        }
        this.m_VideoState = 0;
    }

    protected void DeinitializeVideoPlayer()
    {
        this.m_TrackSelector = null;
        if (this.m_ExoPlayer != null)
        {
            this.m_ExoPlayer.stop();
            this.m_ExoPlayer.release();
            this.m_ExoPlayer = null;
        }
        if (this.m_AudioEngine != null)
        {
            this.m_AudioEngine.destroySpatDecoderQueue(this.m_Spat);
            this.m_Spat = null;
            this.m_AudioEngine.delete();
            this.m_AudioEngine = null;
        }
    }

    public boolean IsPlaying()
    {
        return (this.m_ExoPlayer != null) && ((this.m_VideoState == 5) || ((IsSeeking()) && (this.m_ExoPlayer.getPlayWhenReady())));
    }

    public boolean IsPaused()
    {
        return this.m_VideoState == 7;
    }

    public boolean IsSeeking()
    {
        return (this.m_VideoState == 2) || (this.m_VideoState == 4);
    }

    public boolean IsFinished()
    {
        return this.m_VideoState == 8;
    }

    public boolean CanPlay()
    {
        return (this.m_VideoState == 6) || (this.m_VideoState == 7) || (this.m_VideoState == 5) || (this.m_VideoState == 8);
    }

    private HttpDataSource.Factory BuildHttpDataSourceFactory(boolean useBandwidthMeter)
    {
        return new DefaultHttpDataSourceFactory(this.m_UserAgent, useBandwidthMeter ? BANDWIDTH_METER : null, 8000, 8000, true);
    }

    private DataSource.Factory BuildDataSourceFactory(boolean useBandwidthMeter)
    {
        return new DefaultDataSourceFactory(this.m_Context, useBandwidthMeter ? BANDWIDTH_METER : null,
                BuildHttpDataSourceFactory(useBandwidthMeter));
    }

    protected void PlayerRendererSetup()
    {
        if ((this.m_FilePath != null) && (this.m_FilePath.length() > 0)) {
            OpenVideoFromFileInternal(this.m_FilePath, 0L, "");
        }
    }

    protected void PlayerRenderUpdate()
    {
        if ((this.m_FilePath != null) && (this.m_FilePath.length() > 0)) {
            OpenVideoFromFileInternal(this.m_FilePath, 0L, "");
        }
        if (this.m_ExoPlayer != null)
        {
            PlaybackParameters currParams = this.m_ExoPlayer.getPlaybackParameters();
            this.m_fPlaybackRate = currParams.speed;
        }
    }

    private MediaSource BuildMediaSource(String filepath)
    {
        Uri uri = Uri.parse(filepath);
        if ((filepath.startsWith("jar:")) || (filepath.contains(".zip!")) || (filepath.contains(".obb!"))) {
            return new ExtractorMediaSource(uri, new JarDataSourceFactory(filepath), new DefaultExtractorsFactory(), this.m_MainHandler, this.m_EventLogger);
        }
        int type;
        switch (type = Util.inferContentType(uri))
        {
            case 1:
                return new SsMediaSource(uri, BuildDataSourceFactory(false), new DefaultSsChunkSource.Factory(this.m_MediaDataSourceFactory), this.m_MainHandler, this.m_EventLogger);
            case 0:
                return new DashMediaSource(uri, BuildDataSourceFactory(false), new DefaultDashChunkSource.Factory(this.m_MediaDataSourceFactory), this.m_MainHandler, this.m_EventLogger);
            case 2:
                return new HlsMediaSource(uri, this.m_MediaDataSourceFactory, this.m_MainHandler, this.m_EventLogger);
            case 3:
                return new ExtractorMediaSource(uri, new DefaultDataSourceFactory(this.m_Context, "ExoExample"), new DefaultExtractorsFactory(), this.m_MainHandler, this.m_EventLogger);
        }
        throw new IllegalStateException("Unsupported type: " + type);
    }

    protected boolean OpenVideoFromFileInternal(String filePath, long fileOffset, String httpHeaderJson)
    {
        boolean success = false;
        if ((this.m_ExoPlayer != null) && (this.m_VideoState != 2) && (this.m_SurfaceTexture != null))
        {
            final MediaSource mediaSource;
            if ((mediaSource = BuildMediaSource(filePath)) != null)
            {
                this.m_VideoState = 2;
                ((Activity)this.m_Context)

                        .runOnUiThread(new Runnable()
                        {
                            public final void run()
                            {
                                if (AVProVideoExoPlayer.this.m_Surface != null)
                                {
                                    AVProVideoExoPlayer.this.m_ExoPlayer.setVideoSurface(null);
                                    AVProVideoExoPlayer.this.m_Surface.release();
                                }
                                AVProVideoExoPlayer.this.m_Surface = new Surface(AVProVideoExoPlayer.this.m_SurfaceTexture);
                                AVProVideoExoPlayer.this.m_ExoPlayer.setVideoSurface(AVProVideoExoPlayer.this.m_Surface);

                                AVProVideoExoPlayer.this.m_ExoPlayer.setPlayWhenReady(false);
                                AVProVideoExoPlayer.this.m_ExoPlayer.prepare(mediaSource, false, true);
                            }
                        });
                success = true;
            }
            this.m_FilePath = null;
        }
        else
        {
            this.m_FilePath = filePath;
            success = true;
        }
        return success;
    }

    public void SetLooping(boolean bLooping)
    {
        this.m_bLooping = bLooping;
        if ((this.m_ExoPlayer != null) && (this.m_VideoState >= 3))
        {
            UpdateLooping();return;
        }
        AddVideoCommandInt(VideoCommand_SetLooping, 0);
    }

    public long GetCurrentTimeMs()
    {
        if (this.m_ExoPlayer == null) {
            return 0L;
        }
        return this.m_ExoPlayer.getCurrentPosition();
    }

    public void SetPlaybackRate(float fRate)
    {
        if (this.m_ExoPlayer != null)
        {
            PlaybackParameters currParams = this.m_ExoPlayer.getPlaybackParameters();
            this.m_ExoPlayer.setPlaybackParameters(new PlaybackParameters(fRate, currParams.pitch));
        }
    }

    public void SetAudioTrack(int iTrackIndex)
    {
        if ((this.m_ExoPlayer == null) || (iTrackIndex >= this.m_iNumberAudioTracks) || (iTrackIndex == this.m_iCurrentAudioTrackIndex)) {
            return;
        }
        for (int i = 0; i < this.m_ExoPlayer.getRendererCount(); i++) {
            if (this.m_ExoPlayer.getRendererType(i) == 1)
            {
                this.m_TrackSelector.clearSelectionOverrides(i);
                this.m_TrackSelector.setRendererDisabled(i, true);
            }
        }
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo;
        if ((mappedTrackInfo = this.m_TrackSelector.getCurrentMappedTrackInfo()) == null) {
            return;
        }
        int audioTracksPassed = 0;
        for (int i = 0; i < mappedTrackInfo.length; i++) {
            if (this.m_ExoPlayer.getRendererType(i) == 1)
            {
                TrackGroupArray trackGroups;
                if ((trackGroups = mappedTrackInfo.getTrackGroups(i)) != null)
                {
                    int index;
                    if ((index = iTrackIndex - audioTracksPassed) >= trackGroups.length)
                    {
                        audioTracksPassed += trackGroups.length;
                    }
                    else
                    {
                        TrackGroup trackGroup = trackGroups.get(index);

                        ArrayList<Integer> supportedTracks = new ArrayList();
                        for (int j = 0; j < trackGroup.length; j++) {
                            if (mappedTrackInfo.getTrackFormatSupport(i, index, j) == 4) {
                                supportedTracks.add(Integer.valueOf(j));
                            }
                        }
                        if (supportedTracks.size() == 0) {
                            break;
                        }
                        int[] tracks = new int[supportedTracks.size()];
                        for (int j = 0; j < supportedTracks.size(); j++) {
                            tracks[j] = ((Integer)supportedTracks.get(j)).intValue();
                        }
                        MappingTrackSelector.SelectionOverride selectionOverride = new MappingTrackSelector.SelectionOverride(ADAPTIVE_TRACK_SELECTION_FACTORY, index, tracks);

                        this.m_TrackSelector.setSelectionOverride(i, trackGroups, selectionOverride);
                        this.m_iCurrentAudioTrackIndex = index;
                        this.m_TrackSelector.setRendererDisabled(i, false);

                        return;
                    }
                }
            }
        }
    }

    protected void _play()
    {
        if (this.m_ExoPlayer != null)
        {
            this.m_ExoPlayer.setPlayWhenReady(true);
            this.m_VideoState = 5;
        }
    }

    protected void _pause()
    {
        if ((this.m_ExoPlayer != null) && (this.m_VideoState != 6) && (this.m_VideoState != 8))
        {
            this.m_ExoPlayer.setPlayWhenReady(false);
            this.m_VideoState = 7;
        }
    }

    protected void _stop()
    {
        if (this.m_ExoPlayer != null)
        {
            this.m_ExoPlayer.stop();
            if (this.m_Spat != null) {
                this.m_Spat.flushQueue();
            }
            this.m_VideoState = 6;
        }
    }

    protected void _seek(int timeMs)
    {
        if (this.m_ExoPlayer != null) {
            this.m_ExoPlayer.seekTo(timeMs);
        }
    }

    protected void UpdateAudioVolumes()
    {
        if (this.m_ExoPlayer != null)
        {
            float volume = 0.0F;
            if (!this.m_AudioMuted) {
                volume = this.m_AudioVolume;
            }
            this.m_ExoPlayer.setVolume(volume);
        }
    }

    protected void UpdateLooping()
    {
        if (this.m_ExoPlayer != null) {
            this.m_ExoPlayer.setRepeatMode(this.m_bLooping ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
        }
    }

    protected void BindSurfaceToPlayer()
    {
        if (this.m_ExoPlayer != null)
        {
            if (this.m_Surface != null)
            {
                this.m_ExoPlayer.setVideoSurface(null);
                this.m_Surface.release();
            }
            this.m_Surface = new Surface(this.m_SurfaceTexture);
            this.m_ExoPlayer.setVideoSurface(this.m_Surface);
        }
    }

    public void onPlayerStateChanged(boolean playWhenReady, int state)
    {
        switch (state)
        {
            case 1:
                System.out.println("AVProVideo video state idle");
                if (this.m_VideoState != 2)
                {
                    this.m_VideoState = 0; return;
                }
                break;
            case 2:
                System.out.println("AVProVideo video state buffering");
                if (this.m_VideoState != 2)
                {
                    this.m_VideoState = 4;return;
                }
                System.out.println("AVProVideo buffer preparing");

                return;
            case 3:
                System.out.println("AVProVideo video state ready");
                if ((this.m_FilePath != null) && (this.m_FilePath.length() > 0))
                {
                    OpenVideoFromFile(this.m_FilePath, 0L, "");return;
                }
                boolean bDoSetup = false;
                if (this.m_VideoState == 2)
                {
                    this.m_VideoState = 3;
                    Format videoFormat = this.m_ExoPlayer.getVideoFormat();
                    Format audioFormat = this.m_ExoPlayer.getAudioFormat();

                    System.out.println("AVProVideo " + videoFormat + " Audio " + audioFormat);
                    if (videoFormat != null)
                    {
                        this.m_DisplayRate_FrameRate = (this.m_fSourceVideoFrameRate = videoFormat.frameRate);

                        this.m_Width = videoFormat.width;
                        this.m_Height = videoFormat.height;
                        this.m_DurationMs = this.m_ExoPlayer.getDuration();
                        if ((this.m_Width > 0) && (this.m_Height > 0))
                        {
                            this.m_bSourceHasVideo = true;

                            this.m_bVideo_CreateRenderSurface = true;
                            this.m_bVideo_DestroyRenderSurface = false;
                        }
                        bDoSetup = true;
                    }
                    else if (audioFormat != null)
                    {
                        this.m_DisplayRate_FrameRate = (this.m_fSourceVideoFrameRate = audioFormat.frameRate);

                        this.m_Width = 0;
                        this.m_Height = 0;
                        this.m_DurationMs = this.m_ExoPlayer.getDuration();
                        bDoSetup = true;
                    }
                }
                if (bDoSetup)
                {
                    this.m_bVideo_AcceptCommands = true;

                    this.m_VideoState = (this.m_ExoPlayer.getPlayWhenReady() ? 5 : 6);
                }
                return;
            case 4:
                System.out.println("AVProVideo state ended");
                this.m_VideoState = 8;
        }
    }

    public void onLoadingChanged(boolean isLoading) {}

    public void onPositionDiscontinuity() {}

    public void onTimelineChanged(Timeline timeline, Object manifest) {}

    public void onPlayerError(ExoPlaybackException e)
    {
        System.out.println("AVProVideo error " + e.getMessage());
        if ((this.m_VideoState < 5) && (this.m_VideoState > 0)) {
            this.m_iLastError = 100;
        }
    }

    public void onTracksChanged(TrackGroupArray ignored, TrackSelectionArray trackSelections)
    {
        System.out.println("AVProVideo tracks changed");
        this.m_iNumberAudioTracks = 0;
        if ((this.m_ExoPlayer == null) || (this.m_TrackSelector == null)) {
            return;
        }
        MappingTrackSelector.MappedTrackInfo trackinfo;
        if ((trackinfo = this.m_TrackSelector.getCurrentMappedTrackInfo()) == null) {
            return;
        }
        for (int i = 0; i < trackinfo.length; i++) {
            if (this.m_ExoPlayer.getRendererType(i) == 1)
            {
                TrackGroupArray trackGroups = trackinfo.getTrackGroups(i);
                this.m_iNumberAudioTracks += (trackGroups != null ? trackGroups.length : 0);
            }
        }
    }

    public void onLoadError(IOException error)
    {
        if ((this.m_VideoState > 5) && (this.m_VideoState > 0)) {
            this.m_iLastError = 100;
        }
    }

    public void onLoadStarted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs)
    {
        System.out.println("AVProVideo Load Started FrameRate " + trackFormat.frameRate);
    }

    public void onLoadError(DataSpec dataSpec, int dataType, int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded, IOException error, boolean wasCanceled)
    {
        if ((this.m_VideoState > 5) && (this.m_VideoState > 0)) {
            this.m_iLastError = 100;
        }
    }

    public void onLoadCanceled(DataSpec dataSpec, int dataType, int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {}

    public void onLoadCompleted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded)
    {
        System.out.println("AVProVideo Load Completed FrameRate " + trackFormat.frameRate);
    }

    public void onUpstreamDiscarded(int trackType, long mediaStartTimeMs, long mediaEndTimeMs) {}

    public void onDownstreamFormatChanged(int trackType, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long mediaTimeMs)
    {
        System.out.println("AVProVideo Track Changed FrameRate " + trackFormat.frameRate);
    }

    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {}

    public void onRepeatModeChanged(int repeatMode) {}

    protected void UpdateVideoMetadata()
    {
        if (this.m_Context == null) {
            return;
        }
        ((Activity)this.m_Context).runOnUiThread(new Runnable()
        {
            public final void run()
            {
                if ((AVProVideoExoPlayer.this.m_fSourceVideoFrameRate < 0.0F) && (AVProVideoExoPlayer.this.m_ExoPlayer != null))
                {
                    Format videoFormat;
                    if ((videoFormat = AVProVideoExoPlayer.this.m_ExoPlayer.getVideoFormat()) != null)
                    {
                        System.out.println("AVProVideo frame rate " + videoFormat.sampleRate);
                        AVProVideoExoPlayer.this.m_fSourceVideoFrameRate = videoFormat.frameRate;
                    }
                }
            }
        });
    }

    //Hear360 Spatial
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

    private void updateHeadForHear360(float x, float y, float z, float w)
    {
        //double azimuth = 1;
        double azimuth = getAzimuthFromQuat(x, y, z, w);

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

            if(this.m_ExoPlayer != null && this.m_ExoPlayer.getPlaybackState() == STATE_READY && this.m_TrackSelector != null)
            {
                //Update 8 channels' volume according to the detected azimuth
                update8BallVolumes(currentAzimuth);
                updateVolumeMatrix(-currentAzimuth, volumeMatrix);
                this.m_ExoPlayer.setVolumeMatrix(volumeMatrix);
                //player.setAzimuth(azimuth);
                //debugViewHelper.setAzimuth(-currentAzimuth);
                //debugViewHelper.setVolumeMatrix(volumeMatrix);

                //System.exit(1);
            }
        }
    }

    //Swap z and y because in Unity y is upwards
    private double getAzimuthFromQuat(float x, float z, float y, float w)
    {
        // yaw (z-axis rotation)
        double siny = +2.0 * (w * z + x * y);
        double cosy = +1.0 - 2.0 * (y * y + z * z);
        double yaw = Math.atan2(siny, cosy);
        return yaw;
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
        this.m_ExoPlayer.set8BallVolume(volumes);
        this.m_ExoPlayer.setAzimuth(azimuth);

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

        Format format = this.m_ExoPlayer.getAudioFormat();
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
        while ((i > first) && (target.angleValue < arr[i - 1].angleValue)) {
            //while((i > first) && (target.dotValue > arr[i - 1].dotValue)) {
            arr[i] = arr[i - 1];
            i = i - 1;
        }
        arr[i] = target;
        return i;
    }

}
