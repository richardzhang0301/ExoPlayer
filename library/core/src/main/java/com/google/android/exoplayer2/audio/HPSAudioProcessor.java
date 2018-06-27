package com.google.android.exoplayer2.audio;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;

import com.google.android.exoplayer2.ext.hps.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by Richard on 9/21/17.
 */

/**
 * An {@link AudioProcessor} that converts audio data to {@link C#ENCODING_PCM_16BIT}.
 */
/* package */ final class HPSAudioProcessor implements AudioProcessor {

    private int sampleRateHz;
    private int channelCount;

    private HPSLibrary hpsLibrary;
    private static HPSAudioDSP hpsAudioDSP;

    private static final Object azimuthLock = new Object();

    @C.PcmEncoding
    private int encoding;
    private ByteBuffer buffer;
    private ByteBuffer outputBuffer;
    private boolean inputEnded;

    private static double azimuth = 0;

    private float[] inputBuf;
    private float[] outputBuf;

    /**
     * Creates a new audio processor that converts audio data to {@link C#ENCODING_PCM_16BIT}.
     */
    public HPSAudioProcessor() {
        boolean result = hpsLibrary.isAvailable();

        sampleRateHz = Format.NO_VALUE;
        channelCount = Format.NO_VALUE;
        encoding = C.ENCODING_INVALID;
        buffer = EMPTY_BUFFER;
        outputBuffer = EMPTY_BUFFER;

        inputBuf = new float[30000];
        outputBuf = new float[30000];
    }

    @Override
    public boolean configure(int sampleRateHz, int channelCount, @C.Encoding int encoding)
            throws AudioProcessor.UnhandledFormatException {
    /*
    boolean outputChannelsChanged = !Arrays.equals(pendingOutputChannels, outputChannels);
    outputChannels = pendingOutputChannels;
    if (outputChannels == null) {
      //active = false;
      return outputChannelsChanged;
    }
*/
        if (encoding != C.ENCODING_PCM_16BIT) {
            throw new AudioProcessor.UnhandledFormatException(sampleRateHz, channelCount, encoding);
        }
        if (this.sampleRateHz == sampleRateHz && this.channelCount == channelCount
                && this.encoding == encoding) {
            return false;
        }
        this.sampleRateHz = sampleRateHz;
        this.channelCount = channelCount;
        this.encoding = encoding;

        hpsAudioDSP = new HPSAudioDSP(sampleRateHz);

        return true;
    }

    @Override
    public boolean isActive() {
        //return false;
        return encoding != C.ENCODING_INVALID;
    }

    @Override
    public int getOutputChannelCount() {
        return channelCount;
    }

    @Override
    public int getOutputEncoding() {
        return C.ENCODING_PCM_16BIT;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        // Prepare the output buffer.
        int position = inputBuffer.position();
        int limit = inputBuffer.limit();
        int frameCount = (limit - position) / (2 * channelCount);
        //6 in 6 out
        int outputSize = frameCount * channelCount * 2;
        if (buffer.capacity() < outputSize) {
            buffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder());
        } else {
            buffer.clear();
        }

        int fBufIndex = 0;
        while (position < limit) {
            short input = inputBuffer.getShort(position);
            inputBuf[fBufIndex++] = (float)input / 32767.0f;
            //(8byte per 8bits)16bit in total, multiple by 8 channels
            position += 2;
        }

        if(frameCount != 0) {
            //hpsAudioDSP.ProcessInPlaceInterleavedFloat(inputBuf, channelCount, frameCount, true, true);
            hpsAudioDSP.ProcessOutOfPlaceInterleavedFloat((float) azimuth, inputBuf, outputBuf, channelCount, channelCount, true, frameCount, true, true);
        }

        for(int i = 0; i < frameCount; i++) {
            float l = outputBuf[i * channelCount] / 4.5f;
            float r = outputBuf[i * channelCount + 1] / 4.5f;

            short inputFrontL = (short)(l * 32767.0f);
            short inputFrontR = (short)(r * 32767.0f);

            //Write the mixed stereo to the first 2 channels as the output
            buffer.putShort(inputFrontL);
            buffer.putShort(inputFrontR);

            //Pad with 0 for all the other channels
            for(int j = 2; j < channelCount; j++) {
                buffer.putShort((short)0);
            }
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
        sampleRateHz = Format.NO_VALUE;
        channelCount = Format.NO_VALUE;
        encoding = C.ENCODING_INVALID;
    }

    public void setAzimuth(double azimuth) {
        //synchronized (azimuthLock) {
            this.azimuth = azimuth;
            Log.i("DSP", "AzimuthSet: " + azimuth + ", at " + System.currentTimeMillis());
        //}
    }
}
