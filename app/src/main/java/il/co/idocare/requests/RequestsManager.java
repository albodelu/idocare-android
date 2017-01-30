package il.co.idocare.requests;

import java.util.List;

import il.co.idocare.common.BaseManager;
import il.co.idocare.networking.ServerSyncController;
import il.co.idocare.requests.cachers.RequestsCacher;
import il.co.idocare.requests.retrievers.RequestsRetriever;
import il.co.idocare.useractions.cachers.UserActionCacher;
import il.co.idocare.useractions.entities.CreateRequestUserActionEntity;
import il.co.idocare.useractions.entities.VoteForRequestUserActionEntity;
import il.co.idocare.utils.Logger;
import il.co.idocare.utils.multithreading.BackgroundThreadPoster;
import il.co.idocare.utils.multithreading.MainThreadPoster;

/**
 * This manager encapsulates functionality related to "requests"
 */
public class RequestsManager extends BaseManager<RequestsManager.RequestsManagerListener> {

    private static final String TAG = "RequestsManager";


    public interface RequestsManagerListener {
        public void onRequestsFetched(List<RequestEntity> requests);
    }

    private final BackgroundThreadPoster mBackgroundThreadPoster;
    private final MainThreadPoster mMainThreadPoster;
    private final UserActionCacher mUserActionCacher;
    private final RequestsRetriever mRequestsRetriever;
    private RequestsCacher mRequestsCacher;
    private final Logger mLogger;
    private final ServerSyncController mServerSyncController;

    public RequestsManager(BackgroundThreadPoster backgroundThreadPoster,
                           MainThreadPoster mainThreadPoster,
                           UserActionCacher userActionCacher,
                           RequestsRetriever requestsRetriever,
                           RequestsCacher requestsCacher,
                           Logger logger,
                           ServerSyncController serverSyncController) {
        mBackgroundThreadPoster = backgroundThreadPoster;
        mMainThreadPoster = mainThreadPoster;
        mUserActionCacher = userActionCacher;
        mRequestsRetriever = requestsRetriever;
        mRequestsCacher = requestsCacher;
        mLogger = logger;
        mServerSyncController = serverSyncController;
    }


    public void addNewRequest(final RequestEntity newRequest) {

        mLogger.d(TAG, "addNewRequest(); request ID: " + newRequest.getId());

        final CreateRequestUserActionEntity createRequestUserAction = new CreateRequestUserActionEntity(
                newRequest.getCreatedAt(),
                newRequest.getId(),
                newRequest.getCreatedBy());

        mBackgroundThreadPoster.post(new Runnable() {
            @Override
            public void run() {
                // TODO: ensure atomicity of below actions using TransactionController
                mUserActionCacher.cacheUserAction(createRequestUserAction);
                mRequestsCacher.updateOrInsertAndNotify(newRequest);
            }
        });
    }

    public void voteForRequest(final VoteForRequestUserActionEntity voteAction) {

        mLogger.d(TAG, "voteForRequest(); request ID: " + voteAction.getEntityId() + "; vote: " + voteAction);

        mBackgroundThreadPoster.post(new Runnable() {
            @Override
            public void run() {
                // TODO: ensure atomicity of below actions using TransactionController
                mUserActionCacher.cacheUserAction(voteAction);
                RequestEntity request = mRequestsRetriever.getRequestById(voteAction.getEntityId());
                RequestEntity modifiedRequest = 
                        RequestEntity.getBuilder(request).setModifiedLocally(true).build();
                mRequestsCacher.updateOrInsertAndNotify(modifiedRequest);
            }
        });
    }

    public void fetchAllRequestsAndNotify() {
        mLogger.d(TAG, "fetchAllRequestsAndNotify() called");

        mBackgroundThreadPoster.post(new Runnable() {
            @Override
            public void run() {
                final List<RequestEntity> requests = mRequestsRetriever.getAllRequests();
                notifyListenersWithRequests(requests);
                mServerSyncController.requestImmediateSync();
            }
        });
    }

    public void fetchRequestsAssignedToUserAndNotify(final String userId) {
        mLogger.d(TAG, "fetchRequestsAssignedToUserAndNotify() called; user ID: " + userId);

        mBackgroundThreadPoster.post(new Runnable() {
            @Override
            public void run() {
                final List<RequestEntity> requests = mRequestsRetriever.getRequestsAssignedToUser(userId);
                notifyListenersWithRequests(requests);
                mServerSyncController.requestImmediateSync();
            }
        });

    }

    private void notifyListenersWithRequests(final List<RequestEntity> requests) {
        mMainThreadPoster.post(new Runnable() {
            @Override
            public void run() {
                for (RequestsManagerListener listener : getListeners()) {
                    listener.onRequestsFetched(requests);
                }
            }
        });
    }



}
