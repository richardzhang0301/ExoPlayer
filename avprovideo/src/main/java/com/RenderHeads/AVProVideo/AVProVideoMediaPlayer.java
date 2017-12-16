package com.RenderHeads.AVProVideo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.view.Surface;
import com.android.vending.expansion.zipfile.ZipResourceFile;
import com.android.vending.expansion.zipfile.ZipResourceFile.ZipEntryRO;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import org.json.JSONObject;

public class AVProVideoMediaPlayer
        extends AVProVideoPlayer
        implements MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnVideoSizeChangedListener
{
    private MediaPlayer m_MediaPlayer;
    private MediaExtractor m_MediaExtractor;
    private MediaPlayer.TrackInfo[] m_aTrackInfo;

    public AVProVideoMediaPlayer(int playerIndex, boolean watermarked, Random random)
    {
        super(playerIndex, watermarked, random);
        this.m_aTrackInfo = null;
    }

    protected boolean InitialisePlayer(boolean enableAudio360, int audio360Channels)
    {
        this.m_MediaPlayer = new MediaPlayer();
        this.m_aTrackInfo = null;
        return true;
    }

    protected void CloseVideoOnPlayer()
    {
        if (this.m_VideoState >= 3)
        {
            _pause();

            _stop();
        }
        if (Build.VERSION.SDK_INT > 15) {
            if (this.m_MediaExtractor != null)
            {
                this.m_MediaExtractor.release();
                this.m_MediaExtractor = null;
            }
        }
        this.m_aTrackInfo = null;
        if (this.m_MediaPlayer != null) {
            this.m_MediaPlayer.reset();
        }
    }

    protected void DeinitializeVideoPlayer()
    {
        if (this.m_MediaPlayer != null)
        {
            this.m_MediaPlayer.setSurface(null);
            this.m_MediaPlayer.stop();
            this.m_MediaPlayer.reset();
            this.m_MediaPlayer.release();
            this.m_MediaPlayer = null;
        }
    }

    public boolean IsPlaying()
    {
        return this.m_VideoState == 5;
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

    private static Map<String, String> GetJsonAsMap(String json)
    {
        HashMap<String, String> result = new HashMap();
        try
        {
            JSONObject jsonObj;
            Iterator<String> keyIt = (jsonObj = new JSONObject(json)).keys();
            while (keyIt.hasNext())
            {
                String key = (String)keyIt.next();
                String val = jsonObj.getString(key);
                result.put(key, val);
            }
            return result;
        }
        catch (Exception e)
        {
            throw new RuntimeException("Couldn't parse json:" + json, e);
        }
    }

    public void SetHeadRotation(float x, float y, float z, float w) {}

    public void SetFocusRotation(float x, float y, float z, float w) {}

    public void SetFocusProps(float offFocusLevel, float widthDegrees) {}

    public void SetPositionTrackingEnabled(boolean enabled) {}

    public void SetFocusEnabled(boolean enabled) {}

    protected boolean OpenVideoFromFileInternal(String filePath, long fileOffset, String httpHeaderJson)
    {
        boolean bReturn = false;

        this.m_aTrackInfo = null;
        if (this.m_MediaPlayer != null)
        {
            boolean bFileGood = true;
            try
            {
                if ((filePath.toLowerCase().startsWith("http://")) ||
                        (filePath.toLowerCase().startsWith("https://")) ||
                        (filePath.toLowerCase().startsWith("rtsp://")))
                {
                    Uri uri = Uri.parse(filePath);
                    if ((httpHeaderJson != null) && (!httpHeaderJson.isEmpty()))
                    {
                        Map<String, String> httpHeaderMap = GetJsonAsMap(httpHeaderJson);
                        this.m_MediaPlayer.setDataSource(this.m_Context, uri, httpHeaderMap);
                    }
                    else
                    {
                        this.m_MediaPlayer.setDataSource(this.m_Context, uri);
                    }
                    this.m_bIsStream = true;
                }
                else
                {
                    try
                    {
                        String lookFor = ".obb!/";
                        int iIndexIntoString;
                        if ((iIndexIntoString = filePath.lastIndexOf(lookFor)) >= 0)
                        {
                            String zipPathName = filePath.substring(11, iIndexIntoString + lookFor.length() - 2);
                            String zipFileName = filePath.substring(iIndexIntoString + lookFor.length());

                            setMediaPlayerDataSourceFromZip(zipPathName, zipFileName);
                        }
                        else
                        {
                            throw new IOException("Not an obb file");
                        }
                    }
                    catch (IOException localIOException1)
                    {
                        try
                        {
                            IOException exc;
                            String fileName = filePath.substring(filePath.lastIndexOf("/assets/") + 8);
                            AssetFileDescriptor assetFileDesc;
                            if ((assetFileDesc = this.m_Context.getAssets().openFd(fileName)) != null)
                            {
                                this.m_MediaPlayer.setDataSource(assetFileDesc.getFileDescriptor(), assetFileDesc.getStartOffset() + fileOffset, assetFileDesc.getLength() - fileOffset);
                                if (Build.VERSION.SDK_INT > 15)
                                {
                                    this.m_MediaExtractor = new MediaExtractor();
                                    try
                                    {
                                        this.m_MediaExtractor.setDataSource(assetFileDesc.getFileDescriptor(), assetFileDesc.getStartOffset() + fileOffset, assetFileDesc.getLength() - fileOffset);
                                    }
                                    catch (IOException localIOException2)
                                    {
                                        IOException mediaExtractorE;
                                        this.m_MediaExtractor.release();
                                        this.m_MediaExtractor = null;
                                    }
                                }
                            }
                        }
                        catch (IOException localIOException3)
                        {
                            try
                            {
                                IOException e;
                                if (fileOffset == 0L)
                                {
                                    FileInputStream inputStream;
                                    FileDescriptor fileDescriptor = new FileInputStream(filePath).getFD();

                                    this.m_MediaPlayer.setDataSource(fileDescriptor);
                                    if (Build.VERSION.SDK_INT > 15)
                                    {
                                        this.m_MediaExtractor = new MediaExtractor();
                                        try
                                        {
                                            this.m_MediaExtractor.setDataSource(fileDescriptor);
                                        }
                                        catch (IOException localIOException4)
                                        {
                                            this.m_MediaExtractor.release();
                                            this.m_MediaExtractor = null;
                                        }
                                    }
                                }
                                else
                                {
                                    FileInputStream inputStream;
                                    FileDescriptor fileDescriptor = (inputStream = new FileInputStream(filePath)).getFD();

                                    this.m_MediaPlayer.setDataSource(fileDescriptor, fileOffset, inputStream.getChannel().size() - fileOffset);
                                    if (Build.VERSION.SDK_INT > 15)
                                    {
                                        this.m_MediaExtractor = new MediaExtractor();
                                        try
                                        {
                                            this.m_MediaExtractor.setDataSource(fileDescriptor, fileOffset, inputStream.getChannel().size() - fileOffset);
                                        }
                                        catch (IOException localIOException5)
                                        {
                                            this.m_MediaExtractor.release();
                                            this.m_MediaExtractor = null;
                                        }
                                    }
                                }
                            }
                            catch (IOException localIOException6)
                            {
                                IOException uri_e;
                                Uri uri = Uri.parse("file://" + filePath);
                                this.m_MediaPlayer.setDataSource(this.m_Context, uri);
                                if (Build.VERSION.SDK_INT > 15)
                                {
                                    this.m_MediaExtractor = new MediaExtractor();
                                    try
                                    {
                                        this.m_MediaExtractor.setDataSource(this.m_Context, uri, null);
                                    }
                                    catch (IOException localIOException7)
                                    {
                                        this.m_MediaExtractor.release();
                                        this.m_MediaExtractor = null;
                                    }
                                }
                            }
                        }
                    }
                }
                if (!bFileGood) {
                    //break label668;
                    this.m_iLastError = 100;
                    bReturn = bFileGood;
                    return bReturn;
                }
            }
            catch (IOException e)
            {
                new StringBuilder("Failed to open video file: ").append(e);
                bFileGood = false;
            }
            this.m_MediaPlayer.setOnPreparedListener(this);
            this.m_MediaPlayer.setOnVideoSizeChangedListener(this);
            this.m_MediaPlayer.setOnErrorListener(this);
            this.m_MediaPlayer.setOnCompletionListener(this);
            this.m_MediaPlayer.setOnBufferingUpdateListener(this);
            this.m_MediaPlayer.setOnInfoListener(this);
            this.m_MediaPlayer.setLooping(this.m_bLooping);

            this.m_VideoState = 2;
            this.m_MediaPlayer.prepareAsync();
            //break label674;
            //label668:
            //this.m_iLastError = 100;
            //label674:
            bReturn = bFileGood;
        }
        return bReturn;
    }

    public void SetLooping(boolean bLooping)
    {
        this.m_bLooping = bLooping;
        if ((this.m_MediaPlayer != null) && (this.m_VideoState >= 3))
        {
            UpdateLooping();return;
        }
        AddVideoCommandInt(VideoCommand_SetLooping, 0);
    }

    public long GetCurrentTimeMs()
    {
        long result = 0L;
        if (this.m_MediaPlayer != null) {
            if ((this.m_VideoState >= 3) && (this.m_VideoState <= 8)) {
                if (((result = this.m_MediaPlayer.getCurrentPosition()) > this.m_DurationMs) && (this.m_DurationMs > 0L)) {
                    result = this.m_DurationMs;
                }
            }
        }
        return result;
    }

    public void SetPlaybackRate(float fRate)
    {
        if (Build.VERSION.SDK_INT > 22) {
            if ((this.m_MediaPlayer != null) && (this.m_VideoState >= 3))
            {
                PlaybackParams playbackParams = new PlaybackParams();
                if (fRate < 0.01F) {
                    fRate = 0.01F;
                }
                playbackParams.setSpeed(fRate);
                this.m_MediaPlayer.setPlaybackParams(playbackParams);
                this.m_fPlaybackRate = fRate;
            }
        }
    }

    public void SetAudioTrack(int iTrackIndex)
    {
        if (Build.VERSION.SDK_INT > 15) {
            if ((this.m_MediaPlayer != null) && (iTrackIndex < this.m_iNumberAudioTracks) && (iTrackIndex != this.m_iCurrentAudioTrackIndex))
            {
                int iAudioTrack = 0;
                int iTrack = 0;
                MediaPlayer.TrackInfo[] arrayOfTrackInfo;
                int i = (arrayOfTrackInfo = this.m_aTrackInfo).length;
                for (int j = 0; j < i; j++)
                {
                    MediaPlayer.TrackInfo info;
                    if (((info = arrayOfTrackInfo[j]) != null) && (info.getTrackType() == 2))
                    {
                        if (iAudioTrack == iTrackIndex)
                        {
                            this.m_MediaPlayer.selectTrack(iTrack);

                            this.m_iCurrentAudioTrackIndex = iTrackIndex;

                            return;
                        }
                        iAudioTrack++;
                    }
                    iTrack++;
                }
            }
        }
    }

    private void ResetPlaybackFrameRate()
    {
        this.m_DisplayRate_FrameRate = 0.0F;
        this.m_DisplayRate_NumberFrames = 0L;

        this.m_DisplayRate_LastSystemTimeMS = System.nanoTime();
    }

    protected void _play()
    {
        if (this.m_MediaPlayer != null) {
            this.m_MediaPlayer.start();
        }
        ResetPlaybackFrameRate();

        this.m_VideoState = 5;
    }

    protected void _pause()
    {
        if ((this.m_VideoState > 4) && (this.m_VideoState != 6) && (this.m_VideoState != 8))
        {
            if (this.m_MediaPlayer != null) {
                this.m_MediaPlayer.pause();
            }
            ResetPlaybackFrameRate();

            this.m_VideoState = 7;
        }
    }

    protected void _stop()
    {
        if (this.m_VideoState > 4)
        {
            if (this.m_MediaPlayer != null) {
                this.m_MediaPlayer.stop();
            }
            ResetPlaybackFrameRate();

            this.m_VideoState = 6;
        }
    }

    protected void _seek(int timeMs)
    {
        if (this.m_MediaPlayer != null) {
            this.m_MediaPlayer.seekTo(timeMs);
        }
    }

    protected void UpdateAudioVolumes()
    {
        float leftVolume = 0.0F;
        float rightVolume = 0.0F;
        if (!this.m_AudioMuted)
        {
            float leftPan = Math.max(Math.min(Math.abs(this.m_AudioPan - 1.0F), 1.0F), 0.0F);
            float rightPan = Math.max(Math.min(this.m_AudioPan + 1.0F, 1.0F), 0.0F);

            leftVolume = this.m_AudioVolume * leftPan;
            rightVolume = this.m_AudioVolume * rightPan;
            if (leftVolume > 1.0F) {
                leftVolume = 1.0F;
            }
            if (rightVolume > 1.0F) {
                rightVolume = 1.0F;
            }
        }
        if (this.m_MediaPlayer != null) {
            this.m_MediaPlayer.setVolume(leftVolume, rightVolume);
        }
    }

    protected void UpdateLooping()
    {
        if (this.m_MediaPlayer != null) {
            this.m_MediaPlayer.setLooping(this.m_bLooping);
        }
    }

    protected void BindSurfaceToPlayer()
    {
        if (this.m_MediaPlayer != null)
        {
            Surface surface = new Surface(this.m_SurfaceTexture);
            this.m_MediaPlayer.setSurface(surface);
            surface.release();
        }
    }

    private static ZipResourceFile.ZipEntryRO zipFindFile(ZipResourceFile zip, String fileNameInZip)
    {
        ZipResourceFile.ZipEntryRO[] arrayOfZipEntryRO;
        int i = (arrayOfZipEntryRO = zip.getAllEntries()).length;
        for (int j = 0; j < i; j++)
        {
            ZipResourceFile.ZipEntryRO entry;
            if ((entry = arrayOfZipEntryRO[j]).mFileName.equals(fileNameInZip)) {
                return entry;
            }
        }
        throw new RuntimeException(String.format("File \"%s\"not found in zip", new Object[] { fileNameInZip }));
    }

    private void setMediaPlayerDataSourceFromZip(String zipFileName, String fileNameInZip)
            throws IOException
    {
        if (this.m_MediaPlayer == null) {
            return;
        }
        ZipResourceFile zip = new ZipResourceFile(zipFileName);
        FileInputStream fis = new FileInputStream(zipFileName);
        try
        {
            FileDescriptor zipfd = fis.getFD();

            ZipResourceFile.ZipEntryRO entry = zipFindFile(zip, fileNameInZip);
            this.m_MediaPlayer.setDataSource(zipfd, entry.mOffset, entry.mUncompressedLength);
            if (Build.VERSION.SDK_INT > 15) {
                if (this.m_MediaExtractor != null) {
                    try
                    {
                        this.m_MediaExtractor.setDataSource(zipfd, entry.mOffset, entry.mUncompressedLength);
                    }
                    catch (IOException localIOException)
                    {
                        this.m_MediaExtractor.release();
                        this.m_MediaExtractor = null;
                    }
                }
            }
        }
        finally
        {
            fis.close();
        }
    }

    public void onRenderersError(Exception e)
    {
        new StringBuilder("ERROR - onRenderersError: ").append(e);
    }

    private void UpdateGetDuration()
    {
        if (this.m_MediaPlayer != null) {
            this.m_DurationMs = this.m_MediaPlayer.getDuration();
        }
        new StringBuilder("Video duration is: ").append(this.m_DurationMs).append("ms");
    }

    public void onPrepared(MediaPlayer mp)
    {
        this.m_VideoState = 3;

        UpdateGetDuration();
        if (this.m_bIsStream) {
            this.m_iNumberAudioTracks = 1;
        }
        if (this.m_MediaPlayer != null) {
            if (Build.VERSION.SDK_INT > 15) {
                try
                {
                    this.m_aTrackInfo = this.m_MediaPlayer.getTrackInfo();
                    if (this.m_aTrackInfo != null)
                    {
                        new StringBuilder("Source has ").append(this.m_aTrackInfo.length).append(" tracks");
                        if (this.m_aTrackInfo.length > 0)
                        {
                            this.m_iNumberAudioTracks = 0;
                            int iTrack = 0;
                            MediaPlayer.TrackInfo[] arrayOfTrackInfo;
                            int i = (arrayOfTrackInfo = this.m_aTrackInfo).length;
                            for (int j = 0; j < i; j++)
                            {
                                MediaPlayer.TrackInfo info;
                                if ((info = arrayOfTrackInfo[j]) != null) {
                                    switch (info.getTrackType())
                                    {
                                        case 1:
                                            this.m_bSourceHasVideo = true;
                                            if (this.m_fSourceVideoFrameRate == 0.0F) {
                                                if (Build.VERSION.SDK_INT >= 19)
                                                {
                                                    MediaFormat mediaFormat;
                                                    if ((mediaFormat = info.getFormat()) != null) {
                                                        this.m_fSourceVideoFrameRate = mediaFormat.getInteger("frame-rate");
                                                    }
                                                }
                                            }
                                            if (Build.VERSION.SDK_INT > 15) {
                                                if (this.m_MediaExtractor != null)
                                                {
                                                    if (this.m_fSourceVideoFrameRate == 0.0F)
                                                    {
                                                        MediaFormat mediaFormat = this.m_MediaExtractor.getTrackFormat(iTrack);

                                                        this.m_fSourceVideoFrameRate = mediaFormat.getInteger("frame-rate");
                                                        new StringBuilder("Source video frame rate: ").append(this.m_fSourceVideoFrameRate);
                                                    }
                                                    this.m_MediaExtractor.release();
                                                    this.m_MediaExtractor = null;
                                                }
                                            }
                                            break;
                                        case 3:
                                            this.m_bSourceHasTimedText = true;

                                            break;
                                        case 4:
                                            this.m_bSourceHasSubtitles = true;

                                            break;
                                        case 2:
                                            this.m_iNumberAudioTracks += 1;
                                    }
                                }
                                iTrack++;
                            }
                            if (this.m_iNumberAudioTracks > 0) {
                                SetAudioTrack(0);
                            }
                            new StringBuilder("Number of audio tracks in source: ").append(this.m_iNumberAudioTracks);
                        }
                    }
                }
                catch (Exception localException) {}
            }
        }
        if ((this.m_bIsStream) || (this.m_iNumberAudioTracks > 0) || ((this.m_bVideo_RenderSurfaceCreated) && (!this.m_bVideo_DestroyRenderSurface) && (!this.m_bVideo_CreateRenderSurface)))
        {
            this.m_bVideo_AcceptCommands = true;
            if ((this.m_VideoState != 5) && (this.m_VideoState != 4)) {
                this.m_VideoState = 6;
            }
        }
        if ((!this.m_bIsStream) || (this.m_Width > 0)) {
            if (this.m_bShowPosterFrame) {
                _seek(0);
            }
        }
    }

    public void onVideoSizeChanged(MediaPlayer mp, int width, int height)
    {
        if ((this.m_Width != width) || (this.m_Height != height))
        {
            new StringBuilder("onVideoSizeChanged : New size: ").append(width).append(" x ").append(height);

            this.m_Width = width;
            this.m_Height = height;

            this.m_bSourceHasVideo = true;

            this.m_bVideo_CreateRenderSurface = true;
            this.m_bVideo_DestroyRenderSurface = false;
        }
    }

    public boolean onError(MediaPlayer mp, int what, int extra)
    {
        new StringBuilder("onError what(").append(what).append("), extra(").append(extra).append(")");

        boolean result = false;
        switch (this.m_VideoState)
        {
            case 0:
                break;
            case 1:
            case 2:
            case 4:
                this.m_iLastError = 100;
                result = true;

                break;
            case 5:
                this.m_iLastError = 200;
                result = true;
        }
        return result;
    }

    public void onCompletion(MediaPlayer mp)
    {
        if (!this.m_bLooping) {
            if ((this.m_VideoState >= 3) && (this.m_VideoState < 8)) {
                this.m_VideoState = 8;
            }
        }
    }

    public float GetBufferingProgressPercent()
    {
        return this.m_fBufferingProgressPercent;
    }

    public void onBufferingUpdate(MediaPlayer mp, int percent)
    {
        this.m_fBufferingProgressPercent = percent;
    }

    public boolean onInfo(MediaPlayer mp, int what, int extra)
    {
        switch (what)
        {
            case 701:
                this.m_bIsBuffering = true;
                break;
            case 702:
                this.m_bIsBuffering = false;
        }
        return false;
    }

    protected void UpdateVideoMetadata() {}
}
