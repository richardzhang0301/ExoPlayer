package com.RenderHeads.AVProVideo;

import android.net.Uri;
import com.google.android.exoplayer2.upstream.AssetDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class JarDataSource
        implements DataSource
{
    private static final String[] extensions = { "obb!/", "apk!/" };
    private Uri m_Uri;
    private String m_Path;
    private InputStream m_File;
    private long m_Size;
    private ZipFile m_ZipFile;

    public JarDataSource(String path)
    {
        this.m_Path = path;
        this.m_Uri = Uri.parse(path);
        this.m_File = null;
        this.m_ZipFile = null;
        this.m_Size = 0L;
    }

    public final void close()
    {
        if (this.m_File != null) {
            try
            {
                this.m_File.close();
            }
            catch (IOException e)
            {
                System.err.println(e.getMessage());
            }
        }
        if (this.m_ZipFile != null) {
            try
            {
                this.m_ZipFile.close();
            }
            catch (IOException e)
            {
                System.err.println(e.getMessage());
            }
        }
        this.m_ZipFile = null;
        this.m_File = null;
        this.m_Size = 0L;
    }

    public final Uri getUri()
    {
        return this.m_Uri;
    }

    public final long open(DataSpec dataSpec)
            throws AssetDataSource.AssetDataSourceException
    {
        if (this.m_Uri == null) {
            return 0L;
        }
        for (int i = 0; i < extensions.length; i++)
        {
            String lookFor = extensions[i];
            int iIndexIntoString;
            if ((iIndexIntoString = this.m_Path.lastIndexOf(lookFor)) >= 0)
            {
                String zipPathName = this.m_Path.substring(11, iIndexIntoString + lookFor.length() - 2);
                String zipFileName = this.m_Path.substring(iIndexIntoString + lookFor.length());
                try
                {
                    this.m_ZipFile = new ZipFile(zipPathName);
                    ZipEntry entry;
                    if ((entry = this.m_ZipFile.getEntry(zipFileName)) == null) {
                        throw new AssetDataSource.AssetDataSourceException(new IOException("Unable to locate file " + zipFileName + " in zip " + zipPathName));
                    }
                    this.m_File = this.m_ZipFile.getInputStream(entry);
                    this.m_Size = entry.getSize();
                    if (this.m_File.skip(dataSpec.position) < dataSpec.position) {
                        throw new AssetDataSource.AssetDataSourceException(new IOException("End of file reached"));
                    }
                }
                catch (IOException e)
                {
                    throw new AssetDataSource.AssetDataSourceException(e);
                }
                if (this.m_File != null)
                {
                    if (dataSpec.length == -1L) {
                        return this.m_Size;
                    }
                    return dataSpec.length;
                }
            }
        }
        return 0L;
    }

    public final int read(byte[] buffer, int offset, int readLength)
            throws IOException
    {
        if (this.m_File == null) {
            return 0;
        }
        long bytesToRead = Math.min(readLength, this.m_File.available());

        return this.m_File.read(buffer, offset, (int)bytesToRead);
    }
}
