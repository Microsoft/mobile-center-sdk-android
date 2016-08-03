package avalanche.core.channel;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import avalanche.core.ingestion.AvalancheIngestion;
import avalanche.core.ingestion.ServiceCallback;
import avalanche.core.ingestion.http.AvalancheIngestionHttp;
import avalanche.core.ingestion.http.AvalancheIngestionNetworkStateHandler;
import avalanche.core.ingestion.http.AvalancheIngestionRetryer;
import avalanche.core.ingestion.http.DefaultUrlConnectionFactory;
import avalanche.core.ingestion.http.HttpUtils;
import avalanche.core.ingestion.models.Device;
import avalanche.core.ingestion.models.Log;
import avalanche.core.ingestion.models.LogContainer;
import avalanche.core.ingestion.models.json.LogSerializer;
import avalanche.core.persistence.AvalancheDatabasePersistence;
import avalanche.core.persistence.AvalanchePersistence;
import avalanche.core.utils.AvalancheLog;
import avalanche.core.utils.DeviceInfoHelper;
import avalanche.core.utils.IdHelper;
import avalanche.core.utils.NetworkStateHelper;

public class DefaultAvalancheChannel implements AvalancheChannel {

    /**
     * Synchronization lock.
     */
    private static final Object LOCK = new Object();

    /**
     * TAG used in logging.
     */
    private static final String TAG = "AvalancheChannel";

    /**
     * Application context.
     */
    private final Context mContext;

    /**
     * The appKey that's required for forwarding to ingestion.
     */
    private final UUID mAppKey;

    /**
     * The installId that's required for forwarding to ingestion.
     */
    private final UUID mInstallId;

    /**
     * Handler for triggering ingestion of events.
     */
    private final Handler mIngestionHandler;

    /**
     * Channel state per log group.
     */
    private final Map<String, GroupState> mGroupStates;

    /**
     * Global listeners.
     */
    private final Collection<Listener> mListeners;

    /**
     * The persistence object used to store events in the local storage.
     */
    private AvalanchePersistence mPersistence;

    /**
     * The ingestion object used to send batches to the server.
     */
    private AvalancheIngestion mIngestion;

    /**
     * Is channel enabled?
     */
    private boolean mEnabled;

    /**
     * Device properties.
     */
    private Device mDevice;

    /**
     * Creates and initializes a new instance.
     */
    public DefaultAvalancheChannel(@NonNull Context context, @NonNull UUID appKey, @NonNull LogSerializer logSerializer) {
        mContext = context;
        mAppKey = appKey;
        mInstallId = IdHelper.getInstallId();
        mPersistence = new AvalancheDatabasePersistence();
        mPersistence.setLogSerializer(logSerializer);
        AvalancheIngestionHttp api = new AvalancheIngestionHttp(new DefaultUrlConnectionFactory(), logSerializer);
        api.setBaseUrl("http://avalanche-perf.westus.cloudapp.azure.com:8081"); //TODO make that a parameter
        AvalancheIngestionRetryer retryer = new AvalancheIngestionRetryer(api);
        mIngestion = new AvalancheIngestionNetworkStateHandler(retryer, NetworkStateHelper.getSharedInstance(context));
        mIngestionHandler = new Handler(Looper.getMainLooper());
        mGroupStates = new HashMap<>();
        mListeners = new HashSet<>();
        mEnabled = true;
    }

    /**
     * Overloaded constructor with limited visibility that allows for dependency injection.
     *
     * @param context       the context
     * @param appKey        the appKey
     * @param ingestion     ingestion object for dependency injection
     * @param persistence   persistence object for dependency injection
     * @param logSerializer log serializer object for dependency injection
     */
    @VisibleForTesting
    DefaultAvalancheChannel(@NonNull Context context, @NonNull UUID appKey, @NonNull AvalancheIngestion ingestion, @NonNull AvalanchePersistence persistence, @NonNull LogSerializer logSerializer) {
        this(context, appKey, logSerializer);
        mPersistence = persistence;
        mIngestion = ingestion;
    }

    /**
     * Setter for persistence object, to be used for dependency injection.
     *
     * @param mPersistence the persistence object.
     */
    @VisibleForTesting
    void setPersistence(AvalanchePersistence mPersistence) {
        this.mPersistence = mPersistence;
    }

    @Override
    public void addGroup(String groupName, int maxLogsPerBatch, int batchTimeInterval, int maxParallelBatches, GroupListener groupListener) {
        mGroupStates.put(groupName, new GroupState(groupName, maxLogsPerBatch, batchTimeInterval, maxParallelBatches, groupListener));
    }

    @Override
    public void removeGroup(String groupName) {
        mGroupStates.remove(groupName);
    }

    @Override
    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Set the enabled flag. If false, the channel will continue to persist data but not forward any item to ingestion.
     * The most common use-case would be to set it to false and enable sending again after the channel has disabled itself after receiving
     * a recoverable error (most likely related to a server issue).
     *
     * @param enabled flag to enable or disable the channel.
     */
    @Override
    public void setEnabled(boolean enabled) {
        synchronized (LOCK) {
            if (enabled)
                mEnabled = true;
            else
                suspend(true);
        }
    }

    /**
     * Delete all persisted logs for the given group.
     *
     * @param groupName the group name.
     */
    @Override
    public void clear(String groupName) {
        mPersistence.deleteLogs(groupName);
    }

    /**
     * Stop sending logs until app restarted or the channel is enabled again.
     *
     * @param deleteLogs in addition to suspending, if this is true, delete all logs from persistence.
     */
    private void suspend(boolean deleteLogs) {
        synchronized (LOCK) {
            mEnabled = false;
            for (GroupState groupState : mGroupStates.values()) {
                resetThresholds(groupState.mName);
                groupState.mSendingBatches.clear();
            }
            try {
                mIngestion.close();
            } catch (IOException e) {
                AvalancheLog.error("Failed to close ingestion", e);
            }
            if (deleteLogs)
                mPersistence.clear();
            else
                mPersistence.clearPendingLogState();
        }
    }

    /**
     * Reset the counter for a group and restart the timer.
     *
     * @param groupName the group name.
     */
    private void resetThresholds(@NonNull String groupName) {
        synchronized (LOCK) {
            GroupState groupState = mGroupStates.get(groupName);
            setCounter(groupName, 0);
            mIngestionHandler.removeCallbacks(groupState.mRunnable);
            if (mEnabled)
                mIngestionHandler.postDelayed(groupState.mRunnable, groupState.mBatchTimeInterval);
        }
    }

    @VisibleForTesting
    @SuppressWarnings("SameParameterValue")
    int getCounter(@NonNull String groupName) {
        synchronized (LOCK) {
            return mGroupStates.get(groupName).mPendingLogCount;
        }
    }

    /**
     * Update group pending log counter.
     *
     * @param counter new counter value.
     */
    private void setCounter(@NonNull String groupName, int counter) {
        synchronized (LOCK) {
            mGroupStates.get(groupName).mPendingLogCount = counter;
        }
    }

    /**
     * Setter for ingestion dependency, intended to be used for dependency injection.
     *
     * @param ingestion the ingestion object.
     */
    void setIngestion(AvalancheIngestion ingestion) {
        this.mIngestion = ingestion;
    }

    /**
     * This will reset the counters and timers for the event groups and trigger ingestion immediately.
     * Intended to be used after disabling and re-enabling the Channel.
     */
    public void triggerIngestion() {
        synchronized (LOCK) {
            if (mEnabled) {
                for (String groupName : mGroupStates.keySet())
                    triggerIngestion(groupName);
            }
        }
    }

    /**
     * This will, if we're not using the limit for pending batches, trigger sending of a new request.
     * It will also reset the counters for sending out items for both the number of items enqueued and
     * the handlers. It will do this even if we don't have reached the limit
     * of pending batches or the time interval.
     *
     * @param groupName the group name
     */
    private void triggerIngestion(@NonNull String groupName) {
        synchronized (LOCK) {
            AvalancheLog.debug("triggerIngestion(" + groupName + ")");

            if (TextUtils.isEmpty(groupName) || (mAppKey == null) || (mInstallId == null) || !mEnabled) {
                return;
            }

            //Reset counter and timer
            resetThresholds(groupName);
            GroupState groupState = mGroupStates.get(groupName);
            int limit = groupState.mMaxLogsPerBatch;

            //Check if we have reached the maximum number of pending batches, log to LogCat and don't trigger another sending.
            //condition to stop recursion
            if (groupState.mSendingBatches.size() == groupState.mMaxParallelBatches) {
                AvalancheLog.debug(TAG, "Already sending " + groupState.mMaxParallelBatches + " batches of analytics data to the server.");
                return;
            }

            //Get a batch from persistence
            ArrayList<Log> list = new ArrayList<>(0);
            String batchId = mPersistence.getLogs(groupName, limit, list);

            //Add batchIds to the list of batchIds and forward to ingestion for real
            if ((!TextUtils.isEmpty(batchId)) && (list.size() > 0)) {
                LogContainer logContainer = new LogContainer();
                logContainer.setLogs(list);

                groupState.mSendingBatches.put(batchId, list);
                ingestLogs(groupName, batchId, logContainer);

                //if we have sent a batch that was the maximum amount of logs, we trigger sending once more
                //to make sure we send data that was stored on disc
                if (list.size() == limit) {
                    triggerIngestion(groupName);
                }
            }
        }
    }

    /**
     * Forward LogContainer to Ingestion and implement callback to handle success or failure.
     *
     * @param groupName    the GroupName for each batch
     * @param batchId      the ID of the batch
     * @param logContainer a LogContainer object containing several logs
     */
    private void ingestLogs(@NonNull final String groupName, @NonNull final String batchId, @NonNull LogContainer logContainer) {
        AvalancheLog.debug(TAG, "ingestLogs(" + groupName + "," + batchId + ")");
        mIngestion.sendAsync(mAppKey, mInstallId, logContainer, new ServiceCallback() {
                    @Override
                    public void onCallSucceeded() {
                        handleSendingSuccess(groupName, batchId);
                    }

                    @Override
                    public void onCallFailed(Exception e) {
                        handleSendingFailure(groupName, batchId, e);
                    }
                }
        );
    }

    /**
     * The actual implementation to react to sending a batch to the server successfully.
     *
     * @param groupName The group name
     * @param batchId   The batch ID
     */
    private void handleSendingSuccess(@NonNull final String groupName, @NonNull final String batchId) {
        synchronized (LOCK) {
            GroupState groupState = mGroupStates.get(groupName);

            mPersistence.deleteLogs(groupName, batchId);
            List<Log> removedLogsForBatchId = groupState.mSendingBatches.remove(batchId);
            if (removedLogsForBatchId == null) {
                AvalancheLog.warn(TAG, "Error removing batchId after successfully sending data.");
            } else {
                if (groupState.mListener != null) {
                    for (Log log : removedLogsForBatchId)
                        groupState.mListener.onSuccess(log);
                }
            }
            triggerIngestion(groupName);
        }
    }

    /**
     * The actual implementation to react to not being able to send a batch to the server.
     * Will disable the sender in case of a recoverable error.
     * Will delete batch of data in case of a non-recoverable error.
     *
     * @param groupName the group name
     * @param batchId   the batch ID
     * @param e         the exception
     */
    private void handleSendingFailure(@NonNull final String groupName, @NonNull final String batchId, @NonNull final Exception e) {
        if (!HttpUtils.isRecoverableError(e))
            mPersistence.deleteLogs(groupName, batchId);
        List<Log> removedLogsForBatchId = mGroupStates.get(groupName).mSendingBatches.remove(batchId);
        if (removedLogsForBatchId == null) {
            AvalancheLog.warn(TAG, "Error removing batchId after sending failure.");
        } else {
            GroupListener groupListener = mGroupStates.get(groupName).mListener;
            if (groupListener != null) {
                for (Log log : removedLogsForBatchId)
                    groupListener.onFailure(log, e);
            }
        }
        suspend(false);
    }

    /**
     * Actual implementation of enqueue logic. Will increase counters, triggers of batching logic.
     *
     * @param log       the Log to be enqueued
     * @param groupName the queue to use
     */
    @Override
    public void enqueue(@NonNull Log log, @NonNull String groupName) {
        synchronized (LOCK) {

            /* Check group name is registered. */
            if (mGroupStates.get(groupName) == null) {
                AvalancheLog.error("Invalid group name:" + groupName);
                return;
            }

            /* Generate device properties only once per process life time. */
            if (mDevice == null) {
                try {
                    mDevice = DeviceInfoHelper.getDeviceInfo(mContext);
                } catch (DeviceInfoHelper.DeviceInfoException e) {
                    AvalancheLog.error("Device log cannot be generated", e);
                    return;
                }
            }

            /* Attach device properties to every log. */
            log.setDevice(mDevice);

            /* Call listeners so that they can decorate the log. */
            for (Listener listener : mListeners)
                listener.onEnqueuingLog(log, groupName);

            /* Set an absolute timestamp, we'll convert to relative just before sending. */
            log.setToffset(System.currentTimeMillis());

            /* Persist log. */
            try {

                /* Save log in database. */
                mPersistence.putLog(groupName, log);

                /* Increment counters and schedule ingestion if we are not disabled. */
                if (!mEnabled) {
                    AvalancheLog.warn(TAG, "Channel is disabled, event was saved to disk.");
                } else {
                    scheduleIngestion(groupName);
                }
            } catch (AvalanchePersistence.PersistenceException e) {
                AvalancheLog.error(TAG, "Error persisting event with exception: " + e.toString());
            }
        }
    }

    /**
     * This will check the counters for each event group and will either trigger ingestion immediately or schedule ingestion at the
     * interval specified for the group.
     *
     * @param groupName the group name
     */
    private void scheduleIngestion(@NonNull String groupName) {
        synchronized (LOCK) {
            GroupState groupState = mGroupStates.get(groupName);
            int counter = groupState.mPendingLogCount;
            int maxCount = groupState.mMaxLogsPerBatch;
            if (counter == 0) {
                //Kick of timer if the counter is 0 and cancel previously running timer
                resetThresholds(groupName);
            }

            //increment counter
            counter = counter + 1;
            if (counter == maxCount) {
                counter = 0;
                //We have reached the max batch count or a multiple of it. Trigger ingestion.
                triggerIngestion(groupName);
            }

            //set the counter property
            setCounter(groupName, counter);
        }
    }

    @Override
    public void addListener(Listener listener) {
        synchronized (LOCK) {
            mListeners.add(listener);
        }
    }

    @Override
    public void removeListener(Listener listener) {
        synchronized (LOCK) {
            mListeners.remove(listener);
        }
    }

    /**
     * State for a specific log group.
     */
    private class GroupState {

        /**
         * Group name
         */
        final String mName;

        /**
         * Maximum log count per batch.
         */
        final int mMaxLogsPerBatch;

        /**
         * Time to wait before 2 batches, in ms.
         */
        final int mBatchTimeInterval;

        /**
         * Maximum number of batches in parallel.
         */
        final int mMaxParallelBatches;

        /**
         * Batches being currently sent to ingestion.
         */
        final Map<String, List<Log>> mSendingBatches = new HashMap<>();

        /**
         * A listener for a feature.
         */
        final GroupListener mListener;

        /**
         * Pending log count not part of a batch yet.
         */
        int mPendingLogCount;

        /**
         * Runnable that triggers ingestion of this group data
         * and triggers itself in {@link #mBatchTimeInterval} ms.
         */
        final Runnable mRunnable = new Runnable() {

            @Override
            public void run() {
                if (mPendingLogCount > 0) {
                    triggerIngestion(mName);
                }
                mIngestionHandler.postDelayed(this, mBatchTimeInterval);
            }
        };

        /**
         * Init.
         *
         * @param name               group name.
         * @param maxLogsPerBatch    max batch size.
         * @param batchTimeInterval  batch interval in ms.
         * @param maxParallelBatches max number of parallel batches.
         * @param listener           listener for a feature.
         */
        GroupState(String name, int maxLogsPerBatch, int batchTimeInterval, int maxParallelBatches, GroupListener listener) {
            mName = name;
            mMaxLogsPerBatch = maxLogsPerBatch;
            mBatchTimeInterval = batchTimeInterval;
            mMaxParallelBatches = maxParallelBatches;
            mListener = listener;
        }
    }
}
