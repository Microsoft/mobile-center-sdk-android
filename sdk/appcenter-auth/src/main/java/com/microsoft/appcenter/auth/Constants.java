/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.appcenter.auth;

import com.microsoft.appcenter.utils.AppCenterLog;

/**
 * Constants for Auth module.
 */
final class Constants {

    /**
     * Name of the service.
     */
    static final String SERVICE_NAME = "Auth";

    /**
     * TAG used in logging for Auth.
     */
    static final String LOG_TAG = AppCenterLog.LOG_TAG + SERVICE_NAME;

    /**
     * Constant marking event of the auth group.
     */
    static final String AUTH_GROUP = "group_auth";

    /**
     * Base URL for remote configuration.
     */
    static final String DEFAULT_CONFIG_URL = "https://config.appcenter.ms";

    /**
     * Config url format. Variables are base url then appSecret.
     */
    static final String CONFIG_URL_FORMAT = "%s/auth/%s.json";

    /**
     * File path to store cached configuration in application files.
     */
    static final String FILE_PATH = "appcenter/auth/config.json";

    /**
     * ETag preference storage key.
     */
    static final String PREFERENCE_E_TAG_KEY = SERVICE_NAME + ".configFileETag";

    /**
     * ETag response header.
     */
    static final String HEADER_E_TAG = "ETag";

    /**
     * ETag request header.
     */
    static final String HEADER_IF_NONE_MATCH = "If-None-Match";

    /**
     * JSON configuration key for authorities array.
     */
    static final String AUTHORITIES = "authorities";

    /**
     * JSON configuration key for authority default boolean.
     */
    static final String AUTHORITY_DEFAULT = "default";

    /**
     * JSON configuration key for authority within {@link #AUTHORITIES} array.
     */
    static final String AUTHORITY_TYPE = "type";

    /**
     * JSON configuration value for b2c authority within {@link #AUTHORITIES} array.
     */
    static final String AUTHORITY_TYPE_B2C = "B2C";

    /**
     * JSON configuration value for aad authority within {@link #AUTHORITIES} array.
     */
    static final String AUTHORITY_TYPE_AAD = "AAD";

    /**
     * JSON configuration key for authority url within {@link #AUTHORITIES} array.
     */
    static final String AUTHORITY_URL = "authority_url";

    /**
     * JSON configuration key for identity scope.
     */
    static final String IDENTITY_SCOPE = "identity_scope";

    /**
     * JSON configuration key for audience within {@link #AUTHORITIES} array.
     */
    static final String AUDIENCE = "audience";

    /**
     * JSON configuration key for type within {@link #AUDIENCE} object.
     */
    static final String AUDIENCE_TYPE = "type";

    /**
     * JSON configuration value for None type within {@link #AUDIENCE} object.
     */
    static final String AUDIENCE_TYPE_NONE = "None";

    /**
     * JSON configuration value for AzureAdMyOrg type within {@link #AUDIENCE} object.
     */
    static final String AUDIENCE_TYPE_AZURE_AD_MY_ORG = "AzureAdMyOrg";

    /**
     * JSON configuration value for AzureAdAndPersonalMicrosoftAccount type within {@link #AUDIENCE} object.
     */
    static final String AZURE_AD_AND_PERSONAL_MICROSOFT_ACCOUNT = "AzureAdAndPersonalMicrosoftAccount";

    /**
     * JSON configuration value for AzureAdMultipleOrgs type within {@link #AUDIENCE} object.
     */
    static final String AUDIENCE_TYPE_AZURE_AD_MULTIPLE_ORGS = "AzureAdMultipleOrgs";

    /**
     * JSON configuration value for AzureAdMultipleOrgs type within {@link #AUDIENCE} object.
     */
    static final String AUDIENCE_TYPE_PERSONAL_MICROSOFT_ACCOUNT = "PersonalMicrosoftAccount";
}
