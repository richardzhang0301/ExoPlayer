package com.RenderHeads.AVProVideo;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLContext;

public class AVProMobileVideo
{
    public static final int MEDIAPLAYER = 1;
    public static final int EXOPLAYER = 2;
    public static final int kUnityGfxRendererOpenGLES20 = 8;
    public static final int kUnityGfxRendererOpenGLES30 = 11;
    private static boolean s_PreviousContextFail = false;
    private static int s_PreviousDeviceIndex = -1;
    private final boolean m_bWatermarked = false;
    private Map<Integer, AVProVideoPlayer> m_Players;
    private Random m_Random;
    private Context m_Context;
    private int m_iOpenGLVersion;
    private static AVProMobileVideo s_Interface = null;

    public AVProMobileVideo()
    {
        this.m_Players = new HashMap();

        this.m_Random = new Random();
        this.m_Context = null;
        this.m_iOpenGLVersion = -1;
        if (s_Interface == null) {
            s_Interface = this;
        }
    }

    public void SetContext(Context context)
    {
        this.m_Context = context;

        int iPackageManagerOpenGLESVersion = getVersionFromPackageManager(this.m_Context);
        int iDeviceInfoOpenGLESVersion = getGlVersionFromDeviceConfig(this.m_Context);
        if ((iPackageManagerOpenGLESVersion >= 3) && (iDeviceInfoOpenGLESVersion >= 3))
        {
            this.m_iOpenGLVersion = 3;return;
        }
        if ((iPackageManagerOpenGLESVersion >= 2) && (iDeviceInfoOpenGLESVersion >= 2)) {
            this.m_iOpenGLVersion = 2;
        }
    }

    public String GetPluginVersion()
    {
        return "1.7.0";
    }

    public AVProVideoPlayer CreatePlayer(int api, boolean enableAudio360, int audio360Channels)
    {
        if (s_Interface != this) {
            return null;
        }
        int index = -1;
        for (int i = 0; i < 256; i++) {
            if (!this.m_Players.containsKey(Integer.valueOf(i)))
            {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return null;
        }
        switch (api)
        {
            case 1:
                AVProVideoPlayer mediaplayer;
                (mediaplayer = new AVProVideoMediaPlayer(index, false, this.m_Random)).Initialise(this.m_Context, enableAudio360, audio360Channels);
                this.m_Players.put(Integer.valueOf(index), mediaplayer);
                return (AVProVideoPlayer)this.m_Players.get(Integer.valueOf(index));
            case 2:
                AVProVideoPlayer exoplayer;
                (exoplayer = new AVProVideoExoPlayer(index, false, this.m_Random)).Initialise(this.m_Context, enableAudio360, audio360Channels);
                this.m_Players.put(Integer.valueOf(index), exoplayer);
                return (AVProVideoPlayer)this.m_Players.get(Integer.valueOf(index));
        }
        return null;
    }

    private AVProVideoPlayer GetAVProClassForPlayerIndex(int playerIndex)
    {
        AVProVideoPlayer returnPlayerClass = null;
        if ((this.m_Players != null ? this.m_Players.containsKey(Integer.valueOf(playerIndex)) : false)) {
            returnPlayerClass = (AVProVideoPlayer)this.m_Players.get(Integer.valueOf(playerIndex));
        }
        return returnPlayerClass;
    }

    public static void RenderPlayer(int playerIndex)
    {
        if (s_Interface == null) {
            return;
        }
        if (((EGL10)EGLContext.getEGL()).eglGetCurrentContext().equals(EGL10.EGL_NO_CONTEXT))
        {
            s_PreviousContextFail = true;
            return;
        }
        if ((s_PreviousContextFail) && (s_PreviousDeviceIndex >= 0))
        {
            RendererReset(s_PreviousDeviceIndex);

            s_PreviousContextFail = false;
        }
        AVProVideoPlayer theClass;
        if ((theClass = s_Interface.GetAVProClassForPlayerIndex(playerIndex)) != null) {
            theClass.Render();
        }
    }

    public static void WaitForNewFramePlayer(int playerIndex)
    {
        if (s_Interface == null) {
            return;
        }
        AVProVideoPlayer theClass;
        if ((theClass = s_Interface.GetAVProClassForPlayerIndex(playerIndex)) != null) {
            theClass.WaitForNewFrame();
        }
    }

    public static void RendererSetupPlayer(int playerIndex, int iDeviceIndex)
    {
        if (s_Interface == null) {
            return;
        }
        s_PreviousDeviceIndex = iDeviceIndex;
        new StringBuilder("RendererSetupPlayer called with index: ").append(playerIndex).append(" | iDeviceIndex: ").append(iDeviceIndex);
        AVProVideoPlayer theClass;
        if ((theClass = s_Interface.GetAVProClassForPlayerIndex(playerIndex)) != null)
        {
            int glesVersion = s_Interface.m_iOpenGLVersion;
            if (iDeviceIndex == 8) {
                glesVersion = 2;
            } else if (iDeviceIndex == 11) {
                glesVersion = 3;
            }
            theClass.RendererSetup(glesVersion);
        }
    }

    public static void RendererDestroyPlayer(int playerIndex)
    {
        if (s_Interface == null) {
            return;
        }
        AVProVideoPlayer theClass;
        if ((theClass = s_Interface.GetAVProClassForPlayerIndex(playerIndex)) != null) {
            theClass.Deinitialise();
        }
    }

    public static int _GetWidth(int playerIndex)
    {
        int iReturn = 0;
        if (s_Interface == null) {
            return 0;
        }
        AVProVideoPlayer theClass;
        if ((theClass = s_Interface.GetAVProClassForPlayerIndex(playerIndex)) != null) {
            iReturn = theClass.GetWidth();
        }
        return iReturn;
    }

    public static int _GetHeight(int playerIndex)
    {
        int iReturn = 0;
        if (s_Interface == null) {
            return 0;
        }
        AVProVideoPlayer theClass;
        if ((theClass = s_Interface.GetAVProClassForPlayerIndex(playerIndex)) != null) {
            iReturn = theClass.GetHeight();
        }
        return iReturn;
    }

    public static int _GetTextureHandle(int playerIndex)
    {
        int iReturn = 0;
        if (s_Interface == null) {
            return 0;
        }
        AVProVideoPlayer theClass;
        if ((theClass = s_Interface.GetAVProClassForPlayerIndex(playerIndex)) != null) {
            iReturn = theClass.GetTextureHandle();
        }
        return iReturn;
    }

    public static long _GetDuration(int playerIndex)
    {
        long iReturn = 0L;
        if (s_Interface == null) {
            return 0L;
        }
        AVProVideoPlayer theClass;
        if ((theClass = s_Interface.GetAVProClassForPlayerIndex(playerIndex)) != null) {
            iReturn = theClass.GetDurationMs();
        }
        return iReturn;
    }

    public static int _GetLastErrorCode(int playerIndex)
    {
        int iReturn = 0;
        if (s_Interface == null) {
            return 0;
        }
        AVProVideoPlayer theClass;
        if ((theClass = s_Interface.GetAVProClassForPlayerIndex(playerIndex)) != null) {
            iReturn = theClass.GetLastErrorCode();
        }
        return iReturn;
    }

    public static int _GetFrameCount(int playerIndex)
    {
        int iReturn = 0;
        if (s_Interface == null) {
            return 0;
        }
        AVProVideoPlayer theClass;
        if ((theClass = s_Interface.GetAVProClassForPlayerIndex(playerIndex)) != null) {
            iReturn = theClass.GetFrameCount();
        }
        return iReturn;
    }

    public static float _GetVideoDisplayRate(int playerIndex)
    {
        float fReturn = 0.0F;
        if (s_Interface == null) {
            return 0.0F;
        }
        AVProVideoPlayer theClass;
        if ((theClass = s_Interface.GetAVProClassForPlayerIndex(playerIndex)) != null) {
            fReturn = theClass.GetDisplayRate();
        }
        return fReturn;
    }

    public static boolean _CanPlay(int playerIndex)
    {
        boolean bReturn = false;
        if (s_Interface == null) {
            return false;
        }
        AVProVideoPlayer theClass;
        if ((theClass = s_Interface.GetAVProClassForPlayerIndex(playerIndex)) != null) {
            bReturn = theClass.CanPlay();
        }
        return bReturn;
    }

    public static void RendererReset(int deviceIndex)
    {
        if (s_Interface == null) {
            return;
        }
        for (Iterator localIterator = s_Interface.m_Players.entrySet().iterator(); localIterator.hasNext();) {
            RendererSetupPlayer(((Integer)((Map.Entry)localIterator.next()).getKey()).intValue(), deviceIndex);
        }
    }

    private static int getGlVersionFromDeviceConfig(Context context)
    {
        int iReturn = 1;
        if (context != null)
        {
            ActivityManager activityManager;
            if ((activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)) != null)
            {
                ConfigurationInfo configInfo;
                if ((configInfo = activityManager.getDeviceConfigurationInfo()) != null) {
                    if (configInfo.reqGlEsVersion >= 196608) {
                        iReturn = 3;
                    } else if (configInfo.reqGlEsVersion >= 131072) {
                        iReturn = 2;
                    }
                }
            }
        }
        return iReturn;
    }

    private static int getVersionFromPackageManager(Context context)
    {
        if (context != null)
        {
            FeatureInfo[] featureInfos;
            if (((featureInfos = context.getPackageManager().getSystemAvailableFeatures()) != null) && (featureInfos.length > 0))
            {
                FeatureInfo[] arrayOfFeatureInfo1;
                int i = (arrayOfFeatureInfo1 = featureInfos).length;
                for (int j = 0; j < i; j++)
                {
                    FeatureInfo featureInfo;
                    if ((featureInfo = arrayOfFeatureInfo1[j]).name == null)
                    {
                        if (featureInfo.reqGlEsVersion != 0) {
                            return getMajorVersion(featureInfo.reqGlEsVersion);
                        }
                        return 1;
                    }
                }
            }
        }
        return 1;
    }

    private static int getMajorVersion(int glEsVersion)
    {
        return (glEsVersion & 0xFFFF0000) >> 16;
    }

    private static native void nativeInit();

    static
    {
        System.loadLibrary("AVProLocal");

        nativeInit();
    }
}
