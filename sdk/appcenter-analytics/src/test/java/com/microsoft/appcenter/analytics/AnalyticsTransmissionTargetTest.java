package com.microsoft.appcenter.analytics;

import android.content.Context;

import com.microsoft.appcenter.analytics.ingestion.models.EventLog;
import com.microsoft.appcenter.channel.Channel;
import com.microsoft.appcenter.ingestion.models.Log;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


public class AnalyticsTransmissionTargetTest extends AbstractAnalyticsTest {

    @Mock
    private Channel mChannel;

    @Before
    public void setUp() {

        /* Start. */
        super.setUp();
        Analytics analytics = Analytics.getInstance();
        analytics.onStarting(mAppCenterHandler);
        analytics.onStarted(mock(Context.class), mChannel, null, null, false);
    }

    @Test
    public void setEnabled() {

        /* Create a transmission target and assert that it's enabled by default. */
        AnalyticsTransmissionTarget transmissionTarget = Analytics.getTransmissionTarget("test");
        assertTrue(transmissionTarget.isEnabledAsync().get());
        transmissionTarget.trackEvent("eventName1");
        verify(mChannel).enqueue(argThat(new ArgumentMatcher<Log>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof EventLog) {
                    EventLog eventLog = (EventLog) item;
                    return eventLog.getName().equals("eventName1");
                }
                return false;
            }
        }), anyString());

        /* Set enabled to false and assert that it cannot track event. */
        transmissionTarget.setEnabledAsync(false);
        assertFalse(transmissionTarget.isEnabledAsync().get());
        transmissionTarget.trackEvent("eventName2");
        verify(mChannel, never()).enqueue(argThat(new ArgumentMatcher<Log>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof EventLog) {
                    EventLog eventLog = (EventLog) item;
                    return eventLog.getName().equals("eventName2");
                }
                return false;
            }
        }), anyString());
    }

    @Test
    public void setEnabledOnParent() {

        /* Create a transmission target and its child. */
        AnalyticsTransmissionTarget parentTransmissionTarget = Analytics.getTransmissionTarget("parent");
        AnalyticsTransmissionTarget childTransmissionTarget = parentTransmissionTarget.getTransmissionTarget("child");

        /* Set enabled to false on parent and child should also have set enabled to false. */
        parentTransmissionTarget.setEnabledAsync(false);
        assertFalse(parentTransmissionTarget.isEnabledAsync().get());
        assertFalse(childTransmissionTarget.isEnabledAsync().get());
        childTransmissionTarget.trackEvent("eventName1");
        verify(mChannel, never()).enqueue(argThat(new ArgumentMatcher<Log>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof EventLog) {
                    EventLog eventLog = (EventLog) item;
                    return eventLog.getName().equals("eventName1");
                }
                return false;
            }
        }), anyString());

        /* Set enabled to true on parent. Verify that child can track event. */
        parentTransmissionTarget.setEnabledAsync(true);
        childTransmissionTarget.trackEvent("eventName2");
        verify(mChannel).enqueue(argThat(new ArgumentMatcher<Log>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof EventLog) {
                    EventLog eventLog = (EventLog) item;
                    return eventLog.getName().equals("eventName2");
                }
                return false;
            }
        }), anyString());
    }

    @Test
    public void setEnabledOnChild() {

        /* Create a transmission target and its child. */
        AnalyticsTransmissionTarget parentTransmissionTarget = Analytics.getTransmissionTarget("parent");
        AnalyticsTransmissionTarget childTransmissionTarget = parentTransmissionTarget.getTransmissionTarget("child");

        /* Set enabled to false on parent. When try to set enabled to true on child, it should stay false. */
        parentTransmissionTarget.setEnabledAsync(false);
        childTransmissionTarget.setEnabledAsync(true);
        assertFalse(childTransmissionTarget.isEnabledAsync().get());
        childTransmissionTarget.trackEvent("eventName");
        verify(mChannel, never()).enqueue(any(Log.class), anyString());
    }

    @Test
    public void disableAnalytics() {

        /* Set analytics to disabled. */
        Analytics.setEnabled(false);

        /* Create grand parent, parent and child transmission targets and verify that they're all disabled. */
        AnalyticsTransmissionTarget grandParentTarget = Analytics.getTransmissionTarget("grandParent");
        AnalyticsTransmissionTarget parentTarget = grandParentTarget.getTransmissionTarget("parent");
        AnalyticsTransmissionTarget childTarget = parentTarget.getTransmissionTarget("child");
        assertFalse(grandParentTarget.isEnabledAsync().get());
        assertFalse(parentTarget.isEnabledAsync().get());
        assertFalse(childTarget.isEnabledAsync().get());

        /* Enable analytics and verify that they're now all enabled. */
        Analytics.setEnabled(true);
        assertTrue(grandParentTarget.isEnabledAsync().get());
        assertTrue(parentTarget.isEnabledAsync().get());
        assertTrue(childTarget.isEnabledAsync().get());
    }
}
