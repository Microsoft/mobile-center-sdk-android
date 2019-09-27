package com.microsoft.appcenter.distribute.download.http;

import android.net.TrafficStats;
import android.net.Uri;

import com.microsoft.appcenter.AppCenter;
import com.microsoft.appcenter.utils.AppCenterLog;
import com.microsoft.appcenter.utils.AsyncTaskUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.rule.PowerMockRule;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import java.io.BufferedInputStream;
import java.io.File;
import javax.net.ssl.HttpsURLConnection;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

@PrepareForTest({AsyncTaskUtils.class, AppCenterLog.class, HttpDownloadFileTask.class, TrafficStats.class})
public class HttpDownloadFileTaskTest {

    @Mock
    private Uri mMockDownloadUri;

    @Mock
    private File mMockTargetFile;

    @Mock
    private HttpConnectionReleaseDownloader mMockHttpDownloader;

    /**
     * Log tag for this service.
     */
    private static final String LOG_TAG = AppCenter.LOG_TAG + "Distribute";

    @Rule
    public PowerMockRule mRule = new PowerMockRule();

    @Before
    public void setUp() {
        mockStatic(AsyncTaskUtils.class);
        mockStatic(AppCenterLog.class);
        mockStatic(TrafficStats.class);
    }

    @Test
    public void doInBackgroundWhenTotalBytesDownloadedMoreZero() throws Exception {

        /* Prepare data. */
        URL url = mock(URL.class);
        InputStream mockInputStream = mock(InputStream.class);
        URL movedUrl = mock(URL.class);
        FileOutputStream mockFileOutputStream = mock(FileOutputStream.class);
        BufferedInputStream mockBufferedInputStream = mock(BufferedInputStream.class);
        String urlString = "https://mock";
        String movedUrlString = "https://mock2";

        /* Mock file. */
        when(mMockTargetFile.exists()).thenReturn(true);
        when(mMockTargetFile.delete()).thenReturn(false);

        /* Mock url. */
        whenNew(URL.class).withArguments(eq(urlString)).thenReturn(url);
        whenNew(URL.class).withArguments(eq(movedUrlString)).thenReturn(movedUrl);
        when(url.getProtocol()).thenReturn("https");
        when(movedUrl.getProtocol()).thenReturn("https");
        when(mMockDownloadUri.toString()).thenReturn(urlString);

        /* Mock https connection. */
        HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
        when(url.openConnection()).thenReturn(urlConnection);
        when(urlConnection.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_MOVED_PERM);
        when(urlConnection.getHeaderField(anyString())).thenReturn(movedUrlString);

        /* Mock stream. */
        whenNew(FileOutputStream.class).withAnyArguments().thenReturn(mockFileOutputStream);
        whenNew(BufferedInputStream.class).withAnyArguments().thenReturn(mockBufferedInputStream);
        when(urlConnection.getInputStream()).thenReturn(mockInputStream);
        when(mockBufferedInputStream.read(any(byte[].class))).thenReturn(1).thenReturn(-1);
        doThrow(new IOException()).when(mockBufferedInputStream).close();

        /* Start. */
        startDoInBackground();
        HttpDownloadFileTask task = AsyncTaskUtils.execute(LOG_TAG, new HttpDownloadFileTask(mMockHttpDownloader, mMockDownloadUri, mMockTargetFile));
        task.doInBackground(null);

        /* Verify. */
        verifyStatic();
        AppCenterLog.warn(anyString(), anyString());
        verify(urlConnection, never()).disconnect();
        verify(mockBufferedInputStream).close();
        verify(mockFileOutputStream).close();
        verifyStatic();
        TrafficStats.clearThreadStatsTag();
        verify(mMockHttpDownloader).onDownloadComplete(eq(mMockTargetFile));
    }

    @Test
    public void doInBackgroundVerifyRedirection() throws Exception {

        /* Prepare data. */
        URL url = mock(URL.class);
        URL movedUrl = mock(URL.class);
        InputStream mockInputStream = mock(InputStream.class);
        FileOutputStream mockFileOutputStream = mock(FileOutputStream.class);
        BufferedInputStream mockBufferedInputStream = mock(BufferedInputStream.class);
        String urlString = "https://mock";
        String movedUrlString = "http://mock2";

        /* Mock url. */
        whenNew(URL.class).withArguments(eq(urlString)).thenReturn(url);
        whenNew(URL.class).withArguments(eq(movedUrlString)).thenReturn(movedUrl);
        when(url.getProtocol()).thenReturn("https");
        when(movedUrl.getProtocol()).thenReturn("http")
                .thenReturn("https").thenReturn("http")
                .thenReturn("https").thenReturn("http")
                .thenReturn("https").thenReturn("http")
                .thenReturn("https").thenReturn("http")
                .thenReturn("https").thenReturn("http");
        when(mMockDownloadUri.toString()).thenReturn(urlString);

        /* Mock https connection. */
        HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
        when(url.openConnection()).thenReturn(urlConnection);
        when(movedUrl.openConnection()).thenReturn(urlConnection);
        when(urlConnection.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_MOVED_TEMP);
        when(urlConnection.getHeaderField(anyString())).thenReturn(movedUrlString);

        /* Mock stream. */
        whenNew(FileOutputStream.class).withAnyArguments().thenReturn(mockFileOutputStream);
        whenNew(BufferedInputStream.class).withAnyArguments().thenReturn(mockBufferedInputStream);
        when(urlConnection.getInputStream()).thenReturn(mockInputStream);
        when(mockBufferedInputStream.read(any(byte[].class))).thenReturn(-1);

        /* Start. */
        startDoInBackground();
        HttpDownloadFileTask task = AsyncTaskUtils.execute(LOG_TAG, new HttpDownloadFileTask(mMockHttpDownloader, mMockDownloadUri, mMockTargetFile));
        task.doInBackground(null);

        /* Verify. */
        verify(urlConnection, times(6)).disconnect();
        verify(mockBufferedInputStream).close();
        verify(mockFileOutputStream).close();
        verifyStatic();
        TrafficStats.clearThreadStatsTag();
        verify(mMockHttpDownloader, never()).onDownloadComplete(eq(mMockTargetFile));

    }

    @Test
    public void doInBackgroundWhenTaskCancelled() throws Exception {

        /* Prepare data. */
        URL url = mock(URL.class);
        InputStream mockInputStream = mock(InputStream.class);
        URL movedUrl = mock(URL.class);
        FileOutputStream mockFileOutputStream = mock(FileOutputStream.class);
        BufferedInputStream mockBufferedInputStream = mock(BufferedInputStream.class);
        String urlString = "https://mock";
        String movedUrlString = "https://mock2";

        /* Mock file. */
        when(mMockTargetFile.exists()).thenReturn(true);
        when(mMockTargetFile.delete()).thenReturn(false);

        /* Mock url. */
        whenNew(URL.class).withArguments(eq(urlString)).thenReturn(url);
        whenNew(URL.class).withArguments(eq(movedUrlString)).thenReturn(movedUrl);
        when(url.getProtocol()).thenReturn("https");
        when(movedUrl.getProtocol()).thenReturn("https");
        when(mMockDownloadUri.toString()).thenReturn(urlString);

        /* Mock https connection. */
        HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
        when(url.openConnection()).thenReturn(urlConnection);
        when(urlConnection.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_MOVED_PERM);
        when(urlConnection.getHeaderField(anyString())).thenReturn(movedUrlString);
        when(urlConnection.getContentLength()).thenReturn(1);

        /* Mock stream. */
        whenNew(FileOutputStream.class).withAnyArguments().thenReturn(mockFileOutputStream);
        whenNew(BufferedInputStream.class).withAnyArguments().thenReturn(mockBufferedInputStream);
        when(urlConnection.getInputStream()).thenReturn(mockInputStream);
        when(mockBufferedInputStream.read(any(byte[].class))).thenReturn(1);

        /* Start. */
        // todo spy this task
        startDoInBackground();
        HttpDownloadFileTask task = spy(AsyncTaskUtils.execute(LOG_TAG, new HttpDownloadFileTask(mMockHttpDownloader, mMockDownloadUri, mMockTargetFile)));
        task.doInBackground(null);
        doReturn(true).when(task).isCancelled();

        /* Verify. */
        verifyStatic();

        // inputStream.read(data)
        AppCenterLog.warn(anyString(), anyString());
        verify(urlConnection, never()).disconnect();
        verify(mockBufferedInputStream).close();
        verify(mockFileOutputStream).close();
        verifyStatic();
        TrafficStats.clearThreadStatsTag();
        verify(mMockHttpDownloader).onDownloadComplete(eq(mMockTargetFile));
    }

    private void startDoInBackground() {
        final HttpDownloadFileTask[] task = {null};
        when(AsyncTaskUtils.execute(anyString(), isA(HttpDownloadFileTask.class))).then(new Answer<HttpDownloadFileTask>() {
            @Override
            public HttpDownloadFileTask answer(InvocationOnMock invocation) {
                task[0] = ((HttpDownloadFileTask) invocation.getArguments()[1]);
                return task[0];
            }
        });
    }
}