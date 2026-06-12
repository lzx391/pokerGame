package com.example.mgdemoplus.oauth.provider.ding;

import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthDingTalkV2Request;

/**
 * 暴露 JustAuth {@link AuthDingTalkV2Request} 的 protected token/profile 步骤，供 {@link DpDingOAuthProvider} 分步调用。
 */
final class DpJustAuthDingTalkBridge extends AuthDingTalkV2Request {

    DpJustAuthDingTalkBridge(AuthConfig config) {
        super(config);
    }

    AuthToken exchangeToken(AuthCallback callback) {
        return getAccessToken(callback);
    }

    AuthUser loadUser(AuthToken authToken) {
        return getUserInfo(authToken);
    }
}
