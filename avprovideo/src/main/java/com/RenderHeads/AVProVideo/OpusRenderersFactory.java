package com.RenderHeads.AVProVideo;

import android.os.Handler;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.twobigears.audio360.SpatDecoderQueue;
import com.twobigears.audio360exo.SpatialAudioTrack;
import com.twobigears.audio360exo2.OpusRenderer;

public final class OpusRenderersFactory
        implements RenderersFactory
{
    private SpatDecoderQueue m_Spat;
    private SpatialAudioTrack m_SpatialTrack;
    private DefaultRenderersFactory m_RenderersFactory;

    public OpusRenderersFactory(SpatDecoderQueue spat, DefaultRenderersFactory renderersFactory, SpatialAudioTrack spatialTrack)
    {
        this.m_Spat = spat;
        this.m_RenderersFactory = renderersFactory;
        this.m_SpatialTrack = spatialTrack;
    }

    public final Renderer[] createRenderers(Handler eventHandler, VideoRendererEventListener videoRendererEventListener, AudioRendererEventListener audioRendererEventListener, TextRenderer.Output textRendererOutput, MetadataRenderer.Output metadataRendererOutput)
    {
        Renderer[] renderers = this.m_RenderersFactory.createRenderers(eventHandler, videoRendererEventListener, audioRendererEventListener, textRendererOutput, metadataRendererOutput);
        for (int i = 0; i < renderers.length; i++) {
            if (renderers[i].getTrackType() == 1) {
                renderers[i] = new OpusRenderer(this.m_SpatialTrack, false);
            }
        }
        return renderers;
    }
}
