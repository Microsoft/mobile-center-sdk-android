/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.appcenter;

/**
 * Callback to invoke once a JWT is acquired.
 */
public interface AuthTokenCallback {

    /**
     * Notify SDK that authentication completed.
     *
     * @param authToken token value (in JWT format) or null if authentication failed.
     */
    void onAuthTokenResult(String authToken);
}