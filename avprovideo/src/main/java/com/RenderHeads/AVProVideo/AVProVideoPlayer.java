package com.RenderHeads.AVProVideo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Handler;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AVProVideoPlayer
        implements SurfaceTexture.OnFrameAvailableListener
{
    protected boolean m_bWatermarked;
    protected static boolean s_bCompressedWatermarkDataGood = false;
    protected boolean m_bWatermarkDataGood;
    protected Context m_Context;
    protected int m_iOpenGLVersion;
    protected boolean m_bCanUseGLBindVertexArray;
    protected boolean m_bUseFastOesPath;
    protected boolean m_bShowPosterFrame;
    protected int m_iPlayerIndex;
    protected int m_Width;
    protected int m_Height;
    protected long m_DurationMs;
    protected boolean m_bLooping;
    protected float m_fPlaybackRate;
    protected int m_FrameCount;
    protected boolean m_bIsStream;
    protected boolean m_bIsBuffering;
    protected float m_AudioVolume;
    protected float m_AudioPan;
    protected boolean m_AudioMuted;
    protected float m_fBufferingProgressPercent;
    protected AVProMobileVideo_GlRender m_GlRender_Video;
    protected AVProMobileVideo_GlRender m_GlRender_Watermark;
    protected SurfaceTexture m_SurfaceTexture;
    protected AtomicInteger m_iNumberFramesAvailable;
    protected long m_TextureTimeStamp;
    protected static int VideoCommand_Play = 0;
    protected static int VideoCommand_Pause = 1;
    protected static int VideoCommand_Stop = 2;
    protected static int VideoCommand_Seek = 3;
    protected static int VideoCommand_AudioVolumes = 4;
    protected static int VideoCommand_SetLooping = 5;
    protected Queue<VideoCommand> m_CommandQueue;
    protected static final int VideoState_Idle = 0;
    protected static final int VideoState_Opening = 1;
    protected static final int VideoState_Preparing = 2;
    protected static final int VideoState_Prepared = 3;
    protected static final int VideoState_Buffering = 4;
    protected static final int VideoState_Playing = 5;
    protected static final int VideoState_Stopped = 6;
    protected static final int VideoState_Paused = 7;
    protected static final int VideoState_Finished = 8;
    protected int m_VideoState;
    protected boolean m_bVideo_CreateRenderSurface;
    protected boolean m_bVideo_DestroyRenderSurface;
    protected boolean m_bVideo_RenderSurfaceCreated;
    protected boolean m_bVideo_AcceptCommands;
    protected int m_iCurrentAudioTrackIndex;
    protected boolean m_bSourceHasVideo;
    protected int m_iNumberAudioTracks;
    protected boolean m_bSourceHasTimedText;
    protected boolean m_bSourceHasSubtitles;
    protected float m_fSourceVideoFrameRate;
    protected Handler m_WatermarkSizeHandler;
    protected Runnable m_WatermarkPositionRunnable;
    protected Point m_WatermarkPosition;
    protected float m_WatermarkScale;
    protected long m_DisplayRate_LastSystemTimeMS;
    protected long m_DisplayRate_NumberFrames;
    protected float m_DisplayRate_FrameRate;
    protected boolean m_bDeinitialiseFlagged;
    protected boolean m_bDeinitialised;
    protected static final int ErrorCode_None = 0;
    protected static final int ErrorCode_LoadFailed = 100;
    protected static final int ErrorCode_DecodeFailed = 200;
    protected int m_iLastError;
    protected AtomicBoolean m_Extracting;
    protected int m_ExtractWaitTimeout;
    protected long m_FrameArrivalThreadID;
    protected abstract boolean InitialisePlayer(boolean paramBoolean, int paramInt);

    protected abstract void CloseVideoOnPlayer();

    protected abstract void DeinitializeVideoPlayer();

    public abstract boolean IsPlaying();

    public abstract boolean IsPaused();

    public abstract boolean IsSeeking();

    public abstract boolean IsFinished();

    public abstract boolean CanPlay();

    protected abstract boolean OpenVideoFromFileInternal(String paramString1, long paramLong, String paramString2);

    public abstract void SetLooping(boolean paramBoolean);

    public abstract long GetCurrentTimeMs();

    public abstract void SetPlaybackRate(float paramFloat);

    public abstract void SetAudioTrack(int paramInt);

    public abstract void SetHeadRotation(float paramFloat1, float paramFloat2, float paramFloat3, float paramFloat4);

    public abstract void SetFocusRotation(float paramFloat1, float paramFloat2, float paramFloat3, float paramFloat4);

    public abstract void SetFocusProps(float paramFloat1, float paramFloat2);

    public abstract void SetPositionTrackingEnabled(boolean paramBoolean);

    public abstract void SetFocusEnabled(boolean paramBoolean);

    protected abstract void _play();

    protected abstract void _pause();

    protected abstract void _stop();

    protected abstract void _seek(int paramInt);

    protected abstract void UpdateAudioVolumes();

    protected abstract void UpdateLooping();

    protected abstract void BindSurfaceToPlayer();

    public abstract float GetBufferingProgressPercent();

    protected class VideoCommand
    {
        int _command = -1;
        int _intValue = 0;
        float _floatValue = 0.0F;

        protected VideoCommand() {}
    }

    protected final ReentrantLock _mutex = new ReentrantLock();
    protected Random m_Random;

    public AVProVideoPlayer(int playerIndex, boolean watermarked, Random random)
    {
        this.m_bWatermarked = watermarked;
        if (this.m_bWatermarked) {
            if (!AVProMobileWMImage.s_bImagePrepared) {
                s_bCompressedWatermarkDataGood = AVProMobileWMImage.PrepareImage();
            }
        }
        this.m_bWatermarkDataGood = false;

        this.m_iPlayerIndex = playerIndex;

        this.m_bUseFastOesPath = false;
        this.m_bShowPosterFrame = false;

        this.m_Width = 0;
        this.m_Height = 0;
        this.m_DurationMs = 0L;
        this.m_bLooping = false;
        this.m_fPlaybackRate = 1.0F;

        this.m_FrameCount = 0;

        this.m_bIsStream = false;
        this.m_bIsBuffering = false;

        this.m_AudioVolume = 1.0F;
        this.m_AudioPan = 0.0F;
        this.m_AudioMuted = false;

        this.m_fBufferingProgressPercent = 0.0F;

        this.m_iNumberFramesAvailable = new AtomicInteger();
        this.m_iNumberFramesAvailable.set(0);

        this.m_TextureTimeStamp = Long.MIN_VALUE;

        this.m_CommandQueue = new LinkedList();

        this.m_VideoState = 0;

        this.m_bVideo_CreateRenderSurface = false;
        this.m_bVideo_DestroyRenderSurface = false;
        this.m_bVideo_RenderSurfaceCreated = false;
        this.m_bVideo_AcceptCommands = false;

        this.m_iCurrentAudioTrackIndex = -1;

        this.m_bSourceHasVideo = false;
        this.m_iNumberAudioTracks = 0;
        this.m_bSourceHasTimedText = false;
        this.m_bSourceHasSubtitles = false;
        this.m_fSourceVideoFrameRate = 0.0F;

        this.m_DisplayRate_FrameRate = 0.0F;
        this.m_DisplayRate_NumberFrames = 0L;
        this.m_DisplayRate_LastSystemTimeMS = System.currentTimeMillis();

        this.m_bDeinitialiseFlagged = false;
        this.m_bDeinitialised = false;

        this.m_iLastError = 0;

        this.m_Extracting = new AtomicBoolean(false);
        this.m_ExtractWaitTimeout = 50;
        this.m_FrameArrivalThreadID = -1L;

        this.m_Random = (random != null ? new Random() : random);
    }

    public boolean Initialise(Context context, boolean enableAudio360, int audio360Channels)
    {
        if (this.m_bWatermarked)
        {
            if (!s_bCompressedWatermarkDataGood) {
                return false;
            }
            this.m_bWatermarkDataGood = AVProMobileWMImage.CheckWatermarkData();
            if (!this.m_bWatermarkDataGood) {
                return false;
            }
        }
        this.m_Context = context;
        Activity activity = (Activity)this.m_Context;

        this.m_WatermarkPosition = new Point();
        if (this.m_bWatermarked)
        {
            ChangeWatermarkPosition();

            activity.runOnUiThread(new Runnable()
            {
                public final void run()
                {
                    AVProVideoPlayer.this.m_WatermarkSizeHandler = new Handler();
                    AVProVideoPlayer.this.m_WatermarkPositionRunnable = new Runnable()
                    {
                        public final void run()
                        {
                            AVProVideoPlayer.this.ChangeWatermarkPosition();
                            int nextDelay = 1000 + AVProVideoPlayer.this.m_Random.nextInt(1000);
                            AVProVideoPlayer.this.m_WatermarkSizeHandler.postDelayed(this, nextDelay);
                        }
                    };
                    AVProVideoPlayer.this.m_WatermarkSizeHandler.postDelayed(AVProVideoPlayer.this.m_WatermarkPositionRunnable, AVProVideoPlayer.this.m_Random.nextInt(2000));
                }
            });
        }
        return InitialisePlayer(enableAudio360, audio360Channels);
    }

    public boolean StartExtractFrame()
    {
        if (this.m_FrameArrivalThreadID == Thread.currentThread().getId()) {
            return false;
        }
        if (this.m_Extracting.get()) {
            return true;
        }
        this.m_Extracting.set(true);

        return true;
    }

    public void WaitForExtract()
    {
        int iterations = 0;
        while ((iterations++ < this.m_ExtractWaitTimeout) && (this.m_Extracting.get())) {
            try
            {
                Thread.sleep(10L);
            }
            catch (InterruptedException localInterruptedException) {}
        }
        this.m_Extracting.set(false);
    }

    public void WaitForNewFrame()
    {
        int lastFrameCount = this.m_FrameCount;
        int iterations = 0;
        int framesAvailable = this.m_iNumberFramesAvailable.get();
        while ((lastFrameCount + framesAvailable >= this.m_FrameCount) && (iterations++ < this.m_ExtractWaitTimeout))
        {
            AVProMobileVideo.RenderPlayer(this.m_iPlayerIndex);
            try
            {
                Thread.sleep(10L);
            }
            catch (InterruptedException localInterruptedException) {}
        }
        this.m_Extracting.set(false);
    }

    public void SetDeinitialiseFlagged()
    {
        this.m_bDeinitialiseFlagged = true;
        this.m_bDeinitialised = false;
    }

    public boolean GetDeinitialised()
    {
        return this.m_bDeinitialised;
    }

    public void CloseVideo()
    {
        CloseVideoOnPlayer();

        this.m_VideoState = 0;

        this.m_CommandQueue = new LinkedList();
        this.m_bVideo_AcceptCommands = false;
        this.m_Width = 0;
        this.m_Height = 0;
        this.m_DurationMs = 0L;
        this.m_fPlaybackRate = 1.0F;

        this.m_iCurrentAudioTrackIndex = -1;

        this.m_bSourceHasVideo = false;
        this.m_iNumberAudioTracks = 0;
        this.m_bSourceHasTimedText = false;
        this.m_bSourceHasSubtitles = false;
        this.m_fSourceVideoFrameRate = 0.0F;

        this.m_TextureTimeStamp = Long.MIN_VALUE;
        this.m_FrameCount = 0;

        this.m_fBufferingProgressPercent = 0.0F;
        if (this.m_bVideo_RenderSurfaceCreated) {
            this.m_bVideo_DestroyRenderSurface = true;
        }
        this.m_bVideo_CreateRenderSurface = false;

        this.m_iLastError = 0;
    }

    public void Deinitialise()
    {
        CloseVideo();
        DeinitializeVideoPlayer();
        if (this.m_GlRender_Video != null)
        {
            this.m_GlRender_Video.Destroy();
            this.m_GlRender_Video = null;
        }
        if (this.m_GlRender_Watermark != null)
        {
            this.m_GlRender_Watermark.Destroy();
            this.m_GlRender_Watermark = null;
        }
        if (this.m_SurfaceTexture != null)
        {
            this.m_SurfaceTexture.setOnFrameAvailableListener(null);
            this.m_SurfaceTexture.release();
            this.m_SurfaceTexture = null;
        }
        if (this.m_CommandQueue != null)
        {
            this.m_CommandQueue.clear();
            this.m_CommandQueue = null;
        }
        this.m_WatermarkPosition = null;
        if ((this.m_WatermarkSizeHandler != null) && (this.m_WatermarkPositionRunnable != null)) {
            this.m_WatermarkSizeHandler.removeCallbacks(this.m_WatermarkPositionRunnable);
        }
        this.m_WatermarkSizeHandler = null;
        this.m_WatermarkPositionRunnable = null;

        this.m_bDeinitialised = true;
    }

    protected void PlayerRendererSetup() {}

    protected void PlayerRenderUpdate() {}

    public void RendererSetup(int glesVersion)
    {
        if (this.m_bDeinitialiseFlagged) {
            return;
        }
        this.m_iOpenGLVersion = glesVersion;
        this.m_bCanUseGLBindVertexArray = ((this.m_iOpenGLVersion > 2) && (Build.VERSION.SDK_INT >= 18));
        if (this.m_GlRender_Video == null)
        {
            this.m_GlRender_Video = new AVProMobileVideo_GlRender();
            this.m_GlRender_Video.Setup(0, 0, null, true, this.m_bCanUseGLBindVertexArray, this.m_bUseFastOesPath);
            CreateAndBindSinkTexture(this.m_GlRender_Video.GetGlTextureHandle());
        }
        if ((this.m_bWatermarked) && (this.m_GlRender_Watermark == null))
        {
            this.m_GlRender_Watermark = new AVProMobileVideo_GlRender();
            this.m_GlRender_Watermark.Setup(254, 141, AVProMobileWMImage.s_aImageData, false, this.m_bCanUseGLBindVertexArray, false);
        }
        PlayerRendererSetup();
    }

    public int GetPlayerIndex()
    {
        return this.m_iPlayerIndex;
    }

    public int GetTextureHandle()
    {
        if ((this.m_GlRender_Video == null) || (!this.m_bVideo_RenderSurfaceCreated) || (this.m_bVideo_DestroyRenderSurface)) {
            return 0;
        }
        return this.m_GlRender_Video.GetGlTextureHandle();
    }

    public int GetLastErrorCode()
    {
        int iReturnError = this.m_iLastError;
        this.m_iLastError = 0;
        return iReturnError;
    }

    public void SetPlayerOptions(boolean useFastOesPath, boolean showPosterFrame)
    {
        if (!this.m_bWatermarked) {
            this.m_bUseFastOesPath = useFastOesPath;
        }
        this.m_bShowPosterFrame = showPosterFrame;
    }

    public boolean OpenVideoFromFile(String filePath, long fileOffset, String httpHeaderJson)
    {
        CloseVideo();

        this.m_VideoState = 1;

        this.m_bVideo_CreateRenderSurface = false;
        this.m_bVideo_DestroyRenderSurface = false;
        this.m_bVideo_AcceptCommands = false;

        this.m_iCurrentAudioTrackIndex = -1;

        this.m_bSourceHasVideo = false;
        this.m_iNumberAudioTracks = 0;
        this.m_bSourceHasTimedText = false;
        this.m_bSourceHasSubtitles = false;
        this.m_fSourceVideoFrameRate = 0.0F;

        this.m_DurationMs = 0L;
        this.m_FrameCount = 0;

        this.m_bIsStream = false;
        this.m_bIsBuffering = false;

        this.m_fBufferingProgressPercent = 0.0F;

        return OpenVideoFromFileInternal(filePath, fileOffset, httpHeaderJson);
    }

    public boolean IsLooping()
    {
        return this.m_bLooping;
    }

    public int GetFrameCount()
    {
        return this.m_FrameCount;
    }

    public long GetDurationMs()
    {
        return this.m_DurationMs;
    }

    public int GetWidth()
    {
        return this.m_Width;
    }

    public int GetHeight()
    {
        return this.m_Height;
    }

    public float GetDisplayRate()
    {
        return this.m_DisplayRate_FrameRate;
    }

    public float GetPlaybackRate()
    {
        return this.m_fPlaybackRate;
    }

    public boolean HasVideo()
    {
        return this.m_bSourceHasVideo;
    }

    public boolean HasAudio()
    {
        return this.m_iNumberAudioTracks > 0;
    }

    public boolean HasTimedText()
    {
        return this.m_bSourceHasTimedText;
    }

    public boolean HasSubtitles()
    {
        return this.m_bSourceHasSubtitles;
    }

    public boolean IsMuted()
    {
        return this.m_AudioMuted;
    }

    public float GetVolume()
    {
        return this.m_AudioVolume;
    }

    public float GetAudioPan()
    {
        return this.m_AudioPan;
    }

    public void Play()
    {
        AddVideoCommandInt(VideoCommand_Play, 0);
    }

    public void Pause()
    {
        AddVideoCommandInt(VideoCommand_Pause, 0);
    }

    public void Stop()
    {
        AddVideoCommandInt(VideoCommand_Stop, 0);
    }

    public void Seek(int timeMs)
    {
        AddVideoCommandInt(VideoCommand_Seek, timeMs);
    }

    public int GetCurrentAudioTrackIndex()
    {
        return this.m_iCurrentAudioTrackIndex;
    }

    public int GetNumberAudioTracks()
    {
        return this.m_iNumberAudioTracks;
    }

    public boolean IsBuffering()
    {
        return this.m_bIsBuffering;
    }

    public float GetSourceVideoFrameRate()
    {
        return this.m_fSourceVideoFrameRate;
    }

    public long GetTextureTimeStamp()
    {
        return this.m_TextureTimeStamp / 100L;
    }

    public void MuteAudio(boolean muted)
    {
        this.m_AudioMuted = muted;
        if (this.m_VideoState >= 3)
        {
            UpdateAudioVolumes();return;
        }
        AddVideoCommandInt(VideoCommand_AudioVolumes, 0);
    }

    public void SetVolume(float volume)
    {
        volume = Math.max(Math.min(volume, 1.0F), 0.0F);

        this.m_AudioVolume = volume;
        if (this.m_VideoState >= 3)
        {
            UpdateAudioVolumes();return;
        }
        AddVideoCommandInt(VideoCommand_AudioVolumes, 0);
    }

    public void SetAudioPan(float pan)
    {
        pan = Math.max(Math.min(pan, 1.0F), -1.0F);

        this.m_AudioPan = pan;
        if (this.m_VideoState >= 3)
        {
            UpdateAudioVolumes();return;
        }
        AddVideoCommandInt(VideoCommand_AudioVolumes, 0);
    }

    protected void AddVideoCommandInt(int command, int intData)
    {
        this._mutex.lock();
        if (this.m_CommandQueue != null)
        {
            VideoCommand videoCommand;
            (videoCommand = new VideoCommand())._command = command;
            videoCommand._intValue = intData;

            this.m_CommandQueue.add(videoCommand);

            UpdateCommandQueue();
        }
        this._mutex.unlock();
    }

    public boolean Render()
    {
        boolean result = false;

        PlayerRenderUpdate();
        if (this.m_bDeinitialiseFlagged) {
            return false;
        }
        if ((!this.m_bWatermarked) || ((s_bCompressedWatermarkDataGood) && (this.m_bWatermarkDataGood)))
        {
            if (this.m_bVideo_DestroyRenderSurface)
            {
                if (this.m_GlRender_Video != null) {
                    this.m_GlRender_Video.DestroyRenderTarget();
                }
                this.m_bVideo_DestroyRenderSurface = false;
                this.m_bVideo_RenderSurfaceCreated = false;
            }
            if (this.m_bVideo_CreateRenderSurface)
            {
                if (this.m_GlRender_Video != null)
                {
                    this.m_GlRender_Video.DestroyRenderTarget();
                    if (!this.m_bUseFastOesPath) {
                        this.m_GlRender_Video.CreateRenderTarget(this.m_Width, this.m_Height);
                    }
                }
                this.m_bVideo_DestroyRenderSurface = false;
                this.m_bVideo_CreateRenderSurface = false;
                this.m_bVideo_RenderSurfaceCreated = true;
                if ((!this.m_bIsStream) && (this.m_VideoState >= 3))
                {
                    this.m_bVideo_AcceptCommands = true;
                    if ((this.m_VideoState != 5) && (this.m_VideoState != 4)) {
                        this.m_VideoState = 6;
                    }
                }
                SetVolume(this.m_AudioVolume);
            }
            else
            {
                this._mutex.lock();
                UpdateCommandQueue();
                this._mutex.unlock();
            }
            synchronized (this)
            {
                if ((this.m_iNumberFramesAvailable.get() > 0) && (this.m_bVideo_RenderSurfaceCreated) && (this.m_GlRender_Video != null))
                {
                    int iNumFramesAvailable = this.m_iNumberFramesAvailable.get();
                    if (this.m_bUseFastOesPath)
                    {
                        while (this.m_iNumberFramesAvailable.get() > 0)
                        {
                            this.m_SurfaceTexture.updateTexImage();

                            this.m_TextureTimeStamp = this.m_SurfaceTexture.getTimestamp();

                            this.m_iNumberFramesAvailable.decrementAndGet();
                        }
                        this.m_iNumberFramesAvailable.set(0);
                    }
                    else
                    {
                        this.m_GlRender_Video.StartRender();

                        this.m_TextureTimeStamp = this.m_GlRender_Video.Blit(this.m_SurfaceTexture, this.m_iNumberFramesAvailable.get(), null);
                        this.m_iNumberFramesAvailable.set(0);
                        if (this.m_bWatermarked)
                        {
                            float[] mtxWM;
                            (mtxWM = new float[16])[0] = this.m_WatermarkScale;
                            mtxWM[1] = 0.0F;
                            mtxWM[2] = 0.0F;
                            mtxWM[3] = 0.0F;
                            mtxWM[4] = 0.0F;
                            mtxWM[5] = (-this.m_WatermarkScale);
                            mtxWM[6] = 0.0F;
                            mtxWM[7] = 0.0F;
                            mtxWM[8] = 0.0F;
                            mtxWM[9] = 0.0F;
                            mtxWM[10] = 1.0F;
                            mtxWM[11] = 0.0F;
                            mtxWM[12] = (-this.m_WatermarkPosition.x);
                            mtxWM[13] = this.m_WatermarkPosition.y;
                            mtxWM[14] = 0.0F;
                            mtxWM[15] = 1.0F;

                            this.m_GlRender_Watermark.Blit(null, 0, mtxWM);
                        }
                        this.m_GlRender_Video.EndRender();
                    }
                    if (this.m_Width > 0)
                    {
                        this.m_FrameCount += iNumFramesAvailable;
                        UpdateDisplayFrameRate(iNumFramesAvailable);
                    }
                    result = true;
                }
            }
        }
        return result;
    }

    private void UpdateCommandQueue()
    {
        if ((this.m_bVideo_AcceptCommands) && (this.m_CommandQueue != null)) {
            while (!this.m_CommandQueue.isEmpty())
            {
                VideoCommand videoCommand;
                if ((videoCommand = (VideoCommand)this.m_CommandQueue.poll())._command == VideoCommand_Play) {
                    _play();
                } else if (videoCommand._command == VideoCommand_Pause) {
                    _pause();
                } else if (videoCommand._command == VideoCommand_Stop) {
                    _stop();
                } else if (videoCommand._command == VideoCommand_Seek) {
                    _seek(videoCommand._intValue);
                } else if (videoCommand._command == VideoCommand_AudioVolumes) {
                    UpdateAudioVolumes();
                } else if (videoCommand._command == VideoCommand_SetLooping) {
                    UpdateLooping();
                }
            }
        }
    }

    private void ChangeWatermarkPosition()
    {
        this.m_WatermarkPosition.x = ((int)(0.0F + 4.0F * this.m_Random.nextFloat()));
        this.m_WatermarkPosition.y = ((int)(1.0F + 4.0F * this.m_Random.nextFloat()));
        this.m_WatermarkScale = 5.0F;
    }

    private void CreateAndBindSinkTexture(int glTextureHandle)
    {
        this.m_SurfaceTexture = new SurfaceTexture(glTextureHandle);

        this.m_SurfaceTexture.setOnFrameAvailableListener(this);

        BindSurfaceToPlayer();
    }

    private void UpdateDisplayFrameRate(int iNumFrames)
    {
        long systemTimeMS;
        long elapsedTime = ((systemTimeMS = System.nanoTime()) - this.m_DisplayRate_LastSystemTimeMS) / 1000000L;

        this.m_DisplayRate_NumberFrames += iNumFrames;
        if (elapsedTime >= 500L)
        {
            this.m_DisplayRate_FrameRate = ((float)this.m_DisplayRate_NumberFrames / ((float)elapsedTime * 0.001F));

            this.m_DisplayRate_NumberFrames = 0L;
            this.m_DisplayRate_LastSystemTimeMS = systemTimeMS;
        }
    }

    public void onFrameAvailable(SurfaceTexture surfaceTexture)
    {
        synchronized (this)
        {
            this.m_FrameArrivalThreadID = Thread.currentThread().getId();
            this.m_iNumberFramesAvailable.incrementAndGet();
            return;
        }
    }

    protected abstract void UpdateVideoMetadata();
}
