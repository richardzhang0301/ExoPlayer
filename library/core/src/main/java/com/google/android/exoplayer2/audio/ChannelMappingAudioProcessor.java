/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.Encoding;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * An {@link AudioProcessor} that applies a mapping from input channels onto specified output
 * channels. This can be used to reorder, duplicate or discard channels.
 */
/* package */ final class ChannelMappingAudioProcessor implements AudioProcessor {

  private int channelCount;
  private int sampleRateHz;
  private int[] pendingOutputChannels;

  private boolean active;
  private int[] outputChannels;
  private ByteBuffer buffer;
  private ByteBuffer outputBuffer;
  private boolean inputEnded;

  private static final int DEFAULT_CHANNEL_COUNT = 8;

  /**
   * Creates a new processor that applies a channel mapping.
   */
  public ChannelMappingAudioProcessor() {
    buffer = EMPTY_BUFFER;
    outputBuffer = EMPTY_BUFFER;
    channelCount = Format.NO_VALUE;
    sampleRateHz = Format.NO_VALUE;
  }

  /**
   * Resets the channel mapping. After calling this method, call {@link #configure(int, int, int)}
   * to start using the new channel map.
   *
   * @see AudioTrack#configure(String, int, int, int, int, int[])
   */
  public void setChannelMap(int[] outputChannels) {
    pendingOutputChannels = outputChannels;
  }

  @Override
  public boolean configure(int sampleRateHz, int channelCount, @Encoding int encoding)
          throws UnhandledFormatException {
    if(!Util.HEAR360_HPS) {
      boolean outputChannelsChanged = !Arrays.equals(pendingOutputChannels, outputChannels);
      outputChannels = pendingOutputChannels;
      if (outputChannels == null) {
        active = false;
        return outputChannelsChanged;
      }
      if (encoding != C.ENCODING_PCM_16BIT) {
        throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
      }
      if (!outputChannelsChanged && this.sampleRateHz == sampleRateHz
          && this.channelCount == channelCount) {
        return false;
      }
    }

    this.sampleRateHz = sampleRateHz;
    this.channelCount = channelCount;

    if(!Util.HEAR360_HPS) {
      active = channelCount != outputChannels.length;
      for (int i = 0; i < outputChannels.length; i++) {
        int channelIndex = outputChannels[i];
        if (channelIndex >= channelCount) {
          throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
        }
        active |= (channelIndex != i);
      }
    }
    active = true;

    return true;
  }

  @Override
  public boolean isActive() {
    return active;
  }

  @Override
  public int getOutputChannelCount() {
    if(!Util.HEAR360_HPS) {
      return outputChannels == null ? channelCount : outputChannels.length;
    }
    else {
      return DEFAULT_CHANNEL_COUNT;
    }
  }

  @Override
  public int getOutputEncoding() {
    return C.ENCODING_PCM_16BIT;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    int position = inputBuffer.position();
    int limit = inputBuffer.limit();
    int frameCount = (limit - position) / (2 * channelCount);
    int outputSize;
    if(Util.HEAR360_HPS) {
      outputSize = frameCount * DEFAULT_CHANNEL_COUNT * 2;
    }
    else {
      outputSize = frameCount * outputChannels.length * 2;
    }
    if (buffer.capacity() < outputSize) {
      buffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder());
    } else {
      buffer.clear();
    }
    while (position < limit) {
      if(!Util.HEAR360_HPS) {
        for (int channelIndex : outputChannels) {
          buffer.putShort(inputBuffer.getShort(position + 2 * channelIndex));
        }
      }
      else {
        //1st order Ambisonic
        if(channelCount == 4) {
          float w = (float)(inputBuffer.getShort(position + 2 * 0) / 2);
          float y = (float)(inputBuffer.getShort(position + 2 * 1) / 2);
          float z = (float)(inputBuffer.getShort(position + 2 * 2) / 2);
          float x = (float)(inputBuffer.getShort(position + 2 * 3) / 2);

          buffer.putShort((short)w);
          buffer.putShort((short)y);
          buffer.putShort((short)0);
          buffer.putShort((short)0);
          buffer.putShort((short)z);
          buffer.putShort((short)x);
          buffer.putShort((short)0);
          buffer.putShort((short)0);

//          short lf = (short)((0.30052038200428266f * w + 0.405f * y + 0.36f * x) / 4);
//          short rf = (short)((0.30052038200428266f * w - 0.405f * y + 0.36f * x) / 4);
//          short lb = (short)((0.33234018715767727f * w + 0.415f * y - 0.33f * x) / 4);
//          short rb = (short)((0.33234018715767727f * w - 0.415f * y - 0.33f * x) / 4);
//          short c = (short)((0.1414213562373095f * w + 0.f * y + 0.16f * x) / 4);

//          short lf = (short)((0.30308181575488646f * w + 0.28787490443249214f * y + 0.34272772121390443f * x) / 4);
//          short rf = (short)((0.30308181575488646f * w - 0.28787490443249214f * y + 0.34272772121390443f * x) / 4);
//          short ls = (short)((0.30009778241942736f * w + 0.37430033313047179f * y - 0.0523422745233887f * x) / 4);
//          short rs = (short)((0.30009778241942736f * w - 0.37430033313047179f * y - 0.0523422745233887f * x) / 4);
//          short lb = (short)((0.25945829376797824f * w + 0.15979898864340947f * y - 0.35198623716283389f * x) / 4);
//          short rb = (short)((0.25945829376797824f * w - 0.15979898864340947f * y - 0.35198623716283389f * x) / 4);
//          short c = (short)((0.06626228564058137f * w + 0.095210812676599921f * y) / 4);

//          short l = (short)(0.5f * w + 0.5 * y);
//          short r = (short)(0.5f * w - 0.5 * y);
//          short lf = (short)((1.414f * w + x + y) / 2.8284f / 4);
//          short lb = (short)((1.414f * w - x + y) / 2.8284f / 4);
//          short rb = (short)((1.414f * w - x - y) / 2.8284f / 4);
//          short rf = (short)((1.414f * w + x - y) / 2.8284f / 4);
//
//          buffer.putShort(lf);
//          buffer.putShort(rf);
//          buffer.putShort((short)0);
//          buffer.putShort((short)0);
//          buffer.putShort(lb);
//          buffer.putShort(rb);
//          buffer.putShort((short)0);
//          buffer.putShort((short)0);
//          buffer.putShort(ls);
//          buffer.putShort(rs);

//          buffer.putShort((short)0);
//          buffer.putShort((short)0);
//          buffer.putShort((short)0);
//          buffer.putShort((short)0);
//          buffer.putShort((short)0);
//          buffer.putShort((short)0);
//          buffer.putShort(l);
//          buffer.putShort(r);
        }
        else {
          for (int channelIndex = 0; channelIndex < channelCount; channelIndex++) {
            buffer.putShort(inputBuffer.getShort(position + 2 * channelIndex));
          }

          for (int channelIndex = channelCount; channelIndex < DEFAULT_CHANNEL_COUNT; channelIndex++) {
            buffer.putShort((short) 0);
          }
        }
      }
      position += channelCount * 2;
    }
    inputBuffer.position(limit);
    buffer.flip();
    outputBuffer = buffer;
  }

  @Override
  public void queueEndOfStream() {
    inputEnded = true;
  }

  @Override
  public ByteBuffer getOutput() {
    ByteBuffer outputBuffer = this.outputBuffer;
    this.outputBuffer = EMPTY_BUFFER;
    return outputBuffer;
  }

  @SuppressWarnings("ReferenceEquality")
  @Override
  public boolean isEnded() {
    return inputEnded && outputBuffer == EMPTY_BUFFER;
  }

  @Override
  public void flush() {
    outputBuffer = EMPTY_BUFFER;
    inputEnded = false;
  }

  @Override
  public void reset() {
    flush();
    buffer = EMPTY_BUFFER;
    channelCount = Format.NO_VALUE;
    sampleRateHz = Format.NO_VALUE;
    outputChannels = null;
    active = false;
  }

}
